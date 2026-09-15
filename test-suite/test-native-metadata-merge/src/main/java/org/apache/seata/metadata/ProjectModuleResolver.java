/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.seata.metadata;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the module that owns a class of this repository.
 *
 * <p>The index is built from the source tree of the project: every
 * {@code src/main/java} directory of a module is scanned, so a class is resolved
 * to the module that compiles it. Classes that are not part of the project
 * (third-party libraries and the JDK) are not resolved.
 *
 * <p>The destination of a resolved module follows the metadata layout of the
 * repository: the {@code server} and {@code namingserver} modules keep a unified
 * {@code reachability-metadata.json}, every other module publishes per-section
 * files in the {@code META-INF/native-image/<groupId>/<artifactId>/} directory
 * taken from the module POM.
 *
 * <p>The compatibility API of Seata 1.x is reflected as well: the classes of the
 * legacy {@code io.seata} packages are mapped to the module that owns the
 * corresponding {@code org.apache.seata} class, which is the module that probes
 * the legacy name.
 */
public final class ProjectModuleResolver implements NativeImageMetadataDistributor.TargetResolver {

    /** Group id of the compatibility module and of the legacy Seata 1.x classes. */
    static final String IO_SEATA_PACKAGE = "io.seata.";

    /** Group id of the Seata 2.x classes. */
    static final String APACHE_SEATA_PACKAGE = "org.apache.seata.";

    /** Modules that publish a single unified metadata file. */
    static final Set<String> UNIFIED_MODULES = Set.of("server", "namingserver");

    static final String MAIN_SOURCES_DIRECTORY = "main";

    static final String METADATA_DIRECTORY = "src/main/resources/META-INF/native-image";

    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("target", "node_modules", ".git", ".idea");

    private static final String TEST_SUITE_DIRECTORY = "test-suite";

    private final Path projectBase;

    /** Class name to the module directories that compile it. */
    private final Map<String, List<Path>> classModules = new HashMap<>();

    /** Module directory to the {@code META-INF/native-image/<groupId>/<artifactId>} directory. */
    private final Map<Path, Path> coordinatesDirectories = new LinkedHashMap<>();

    private ProjectModuleResolver(Path projectBase) {
        this.projectBase = projectBase;
    }

    /**
     * Indexes the modules of the project below the given directory.
     *
     * @param projectBase root directory of the project
     * @return a resolver for the classes of that project
     * @throws IOException when the source tree cannot be read
     */
    public static ProjectModuleResolver forProject(Path projectBase) throws IOException {
        ProjectModuleResolver resolver = new ProjectModuleResolver(projectBase);
        resolver.index();
        return resolver;
    }

    @Override
    public NativeImageMetadataDistributor.Target resolve(String className) {
        if (className == null) {
            return null;
        }
        Path module = moduleOf(className);
        if (module == null) {
            return null;
        }
        String name = moduleName(module);
        if (UNIFIED_MODULES.contains(name)) {
            return new NativeImageMetadataDistributor.UnifiedTarget(
                    module.resolve(METADATA_DIRECTORY).resolve(NativeImageMetadataDistributor.UNIFIED_METADATA_FILE));
        }
        Path coordinates = coordinatesDirectories.get(module);
        return coordinates == null ? null : new NativeImageMetadataDistributor.LegacyTarget(coordinates);
    }

    /**
     * Returns the module that owns a class, including its inner classes and the
     * legacy {@code io.seata} alias of the class.
     */
    Path moduleOf(String className) {
        Path module = lookup(className);
        if (module == null && className.startsWith(IO_SEATA_PACKAGE)) {
            // The compatibility service loader probes the Seata 1.x name of an
            // interface; that name is owned by the module of the 2.x interface.
            module = lookup(APACHE_SEATA_PACKAGE + className.substring(IO_SEATA_PACKAGE.length()));
        }
        return module;
    }

    /** Returns the module of the top level class of the given class name. */
    private Path lookup(String className) {
        String name = className;
        while (true) {
            List<Path> modules = classModules.get(name);
            if (modules != null && !modules.isEmpty()) {
                return select(modules);
            }
            int innerClass = name.lastIndexOf('$');
            if (innerClass < 0) {
                return null;
            }
            name = name.substring(0, innerClass);
        }
    }

    private Path select(List<Path> modules) {
        if (modules.size() == 1) {
            return modules.get(0);
        }
        // Test applications may declare classes in the package of a library
        // module; the library module always wins, then the shortest path.
        return modules.stream()
                .min(Comparator.comparingInt(
                                (Path module) -> moduleName(module).startsWith(TEST_SUITE_DIRECTORY) ? 1 : 0)
                        .thenComparing((Path module) -> moduleName(module)))
                .orElse(null);
    }

    private String moduleName(Path module) {
        return projectBase.relativize(module).toString().replace('\\', '/');
    }

    private void index() throws IOException {
        Files.walkFileTree(projectBase, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                Path name = directory.getFileName();
                if (name != null && SKIPPED_DIRECTORIES.contains(name.toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (isMainSources(directory)) {
                    Path module = directory.getParent().getParent().getParent();
                    indexClasses(module, directory);
                    indexCoordinates(module);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean isMainSources(Path directory) {
        Path name = directory.getFileName();
        Path parent = directory.getParent();
        return name != null
                && "java".equals(name.toString())
                && parent != null
                && parent.getFileName() != null
                && MAIN_SOURCES_DIRECTORY.equals(parent.getFileName().toString());
    }

    private void indexClasses(Path module, Path sourcesDirectory) throws IOException {
        try (var paths = Files.walk(sourcesDirectory)) {
            for (Path source : paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                String fileName = sourcesDirectory.relativize(source).toString();
                String className = fileName.substring(0, fileName.length() - ".java".length())
                        .replace(java.io.File.separatorChar, '.')
                        .replace('/', '.');
                classModules
                        .computeIfAbsent(className, key -> new ArrayList<>())
                        .add(module);
            }
        }
    }

    private void indexCoordinates(Path module) {
        String[] coordinates = readCoordinates(module);
        if (coordinates != null) {
            coordinatesDirectories.put(
                    module,
                    module.resolve(METADATA_DIRECTORY).resolve(coordinates[0]).resolve(coordinates[1]));
        }
    }

    /**
     * Reads the Maven coordinates of a module.
     *
     * @param module directory of the module
     * @return the group id and the artifact id, or {@code null} when the module
     *         has no POM
     */
    private static String[] readCoordinates(Path module) {
        Path pom = module.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(pom.toFile());
            Element project = document.getDocumentElement();
            String groupId = text(project, "groupId");
            if (groupId == null) {
                groupId = text(firstChild(project, "parent"), "groupId");
            }
            String artifactId = text(project, "artifactId");
            if (groupId == null || artifactId == null) {
                return null;
            }
            return new String[] {groupId, artifactId};
        } catch (Exception e) {
            return null;
        }
    }

    private static Element firstChild(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Node child : children(parent)) {
            if (name.equals(child.getNodeName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private static String text(Element parent, String name) {
        Element element = firstChild(parent, name);
        if (element == null) {
            return null;
        }
        String text = element.getTextContent();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static List<Node> children(Element parent) {
        List<Node> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                children.add(node);
            }
        }
        return children;
    }
}
