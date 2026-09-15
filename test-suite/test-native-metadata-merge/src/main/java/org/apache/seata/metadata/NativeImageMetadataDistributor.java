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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Distributes generated GraalVM reachability metadata to the module that owns
 * each entry.
 *
 * <p>The native image agent collects the metadata of the whole application, so
 * a single run of the server records the reflection and resource entries of
 * every Seata module on its classpath. Writing all of them into the server
 * metadata file makes the file an aggregate of the whole project and duplicates
 * the metadata that is published by each module jar.
 *
 * <p>This class routes every entry to the module that owns the referenced class
 * or resource, using the layout of the repository:
 *
 * <ul>
 *   <li>the {@code server} and {@code namingserver} modules keep a single
 *       unified {@link #UNIFIED_METADATA_FILE};</li>
 *   <li>every other module publishes per-section files
 *       ({@link #REFLECT_CONFIG_FILE}, {@link #RESOURCE_CONFIG_FILE}) in the
 *       {@code META-INF/native-image/<groupId>/<artifactId>/} directory of the
 *       module.</li>
 * </ul>
 *
 * <p>Entries that cannot be mapped to a module (third-party libraries, the JDK,
 * or metadata that is not owned by a class) stay in the canonical file passed
 * to {@link #distribute(JsonNode, Path)}.
 *
 * <p>The merge itself is delegated to {@link MergeNativeImageMetadata}, so
 * existing entries are never overwritten - only missing entries and missing
 * methods or fields are added.
 */
public final class NativeImageMetadataDistributor {

    /** Metadata file of the modules that keep a single unified metadata file. */
    public static final String UNIFIED_METADATA_FILE = "reachability-metadata.json";

    /** Reflection configuration of the modules that publish per-section files. */
    public static final String REFLECT_CONFIG_FILE = "reflect-config.json";

    /** Resource configuration of the modules that publish per-section files. */
    public static final String RESOURCE_CONFIG_FILE = "resource-config.json";

    private static final String SERVICES_DIRECTORY = "META-INF/services/";

    private static final String SEATA_DIRECTORY = "META-INF/seata/";

    private static final String APACHE_SEATA_PACKAGE = "org.apache.seata.";

    private static final String IO_SEATA_PACKAGE = "io.seata.";

    private static final String APACHE_SEATA_RESOURCE_PREFIX = "org/apache/seata/";

    private static final String ARRAY_SUFFIX = "[]";

    private static final String CLASS_SUFFIX = ".class";

    /** Shared ObjectMapper instance. */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** Destination of the metadata of a single class. */
    public sealed interface Target permits UnifiedTarget, LegacyTarget {}

    /** The metadata is merged into a unified {@code reachability-metadata.json}. */
    public record UnifiedTarget(Path file) implements Target {}

    /** The metadata is merged into the per-section files of a module. */
    public record LegacyTarget(Path directory) implements Target {}

    /**
     * Resolves the module that owns the metadata of a class.
     *
     * @param className fully qualified class name of the entry
     * @return the destination of the entry, or {@code null} when the entry has to
     *         stay in the canonical metadata file
     */
    public interface TargetResolver {
        Target resolve(String className);
    }

    private final TargetResolver targetResolver;

    /**
     * Creates a distributor that routes entries with the given resolver.
     *
     * @param targetResolver resolver of the owning module of a class
     */
    public NativeImageMetadataDistributor(TargetResolver targetResolver) {
        this.targetResolver = targetResolver;
    }

    /**
     * Distributes the generated metadata over the modules of the project and
     * merges the remaining entries into the canonical metadata file.
     *
     * @param generated metadata collected by the native image agent
     * @param canonicalFile metadata file that keeps the entries without a module
     * @throws IOException when a metadata file cannot be read or written
     */
    public void distribute(JsonNode generated, Path canonicalFile) throws IOException {
        Map<Path, ArrayNode> unifiedReflection = new LinkedHashMap<>();
        Map<Path, ArrayNode> unifiedResources = new LinkedHashMap<>();
        Map<Path, ArrayNode> legacyReflection = new LinkedHashMap<>();
        Map<Path, ArrayNode> legacyResources = new LinkedHashMap<>();

        for (JsonNode entry : entries(generated, "reflection")) {
            Target target = resolve(typeName(entry));
            if (target instanceof LegacyTarget legacyTarget) {
                legacyReflection
                        .computeIfAbsent(legacyTarget.directory(), directory -> objectMapper.createArrayNode())
                        .add(toReflectConfigEntry(entry));
            } else {
                Path file = target instanceof UnifiedTarget unifiedTarget ? unifiedTarget.file() : canonicalFile;
                unifiedReflection
                        .computeIfAbsent(file, destination -> objectMapper.createArrayNode())
                        .add(entry);
            }
        }

        for (JsonNode entry : entries(generated, "resources")) {
            Target target = resolve(resourceClassName(entry));
            if (target instanceof LegacyTarget legacyTarget) {
                legacyResources
                        .computeIfAbsent(legacyTarget.directory(), directory -> objectMapper.createArrayNode())
                        .add(toResourceConfigEntry(entry));
            } else {
                Path file = target instanceof UnifiedTarget unifiedTarget ? unifiedTarget.file() : canonicalFile;
                unifiedResources
                        .computeIfAbsent(file, destination -> objectMapper.createArrayNode())
                        .add(entry);
            }
        }

        for (Map.Entry<Path, ArrayNode> entry : unifiedReflection.entrySet()) {
            mergeUnifiedMetadata(entry.getKey(), "reflection", entry.getValue());
        }
        for (Map.Entry<Path, ArrayNode> entry : unifiedResources.entrySet()) {
            mergeUnifiedMetadata(entry.getKey(), "resources", entry.getValue());
        }
        for (Map.Entry<Path, ArrayNode> entry : legacyReflection.entrySet()) {
            mergeReflectConfig(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Path, ArrayNode> entry : legacyResources.entrySet()) {
            mergeResourceConfig(entry.getKey(), entry.getValue());
        }
    }

    private Target resolve(String className) {
        return className == null ? null : targetResolver.resolve(className);
    }

    /**
     * Returns the class name of a reflection entry, or {@code null} for entries
     * that do not describe a single class (proxies and lambdas).
     */
    private static String typeName(JsonNode entry) {
        JsonNode type = entry.get("type");
        if (type == null || !type.isString()) {
            return null;
        }
        String name = type.asString();
        while (name.endsWith(ARRAY_SUFFIX)) {
            name = name.substring(0, name.length() - ARRAY_SUFFIX.length());
        }
        return name;
    }

    /**
     * Returns the class name described by a resource entry, or {@code null} when
     * the resource is not a Seata class descriptor.
     *
     * <p>Seata reads the extension points from the resource files named after
     * the interface they implement, so those files are owned by the module of
     * that interface.
     */
    static String resourceClassName(JsonNode entry) {
        JsonNode glob = entry.get("glob");
        if (glob == null || !glob.isString() || entry.has("module") || entry.has("bundle")) {
            return null;
        }
        String resource = glob.asString();
        String className = null;
        if (resource.startsWith(APACHE_SEATA_RESOURCE_PREFIX)) {
            String path = resource.substring(APACHE_SEATA_RESOURCE_PREFIX.length());
            if (path.endsWith(CLASS_SUFFIX)) {
                path = path.substring(0, path.length() - CLASS_SUFFIX.length());
            }
            className = APACHE_SEATA_PACKAGE + path.replace("/", ".");
        } else if (resource.startsWith(SEATA_DIRECTORY)) {
            className = resource.substring(SEATA_DIRECTORY.length());
        } else if (resource.startsWith(SERVICES_DIRECTORY + APACHE_SEATA_PACKAGE)
                || resource.startsWith(SERVICES_DIRECTORY + IO_SEATA_PACKAGE)) {
            className = resource.substring(SERVICES_DIRECTORY.length());
        }
        return className == null || className.isEmpty() ? null : className;
    }

    /** Converts a unified reflection entry into a reflect-config.json entry. */
    private static ObjectNode toReflectConfigEntry(JsonNode entry) {
        return renameProperty(entry, "type", "name");
    }

    /** Converts a unified resource entry into a resource-config.json entry. */
    private static ObjectNode toResourceConfigEntry(JsonNode entry) {
        return renameProperty(entry, "glob", "pattern");
    }

    private static ObjectNode renameProperty(JsonNode entry, String from, String to) {
        ObjectNode converted = objectMapper.createObjectNode();
        for (Map.Entry<String, JsonNode> property : entry.properties()) {
            converted.set(from.equals(property.getKey()) ? to : property.getKey(), property.getValue());
        }
        return converted;
    }

    private static ArrayNode entries(JsonNode generated, String field) {
        JsonNode node = generated.get(field);
        return node instanceof ArrayNode array ? array : objectMapper.createArrayNode();
    }

    private static void mergeUnifiedMetadata(Path file, String field, ArrayNode source) throws IOException {
        ObjectNode document = readObject(file);
        MergeNativeImageMetadata.mergeArrayNodes(source, ensureArray(document, field));
        write(file, document);
    }

    private static void mergeReflectConfig(Path directory, ArrayNode source) throws IOException {
        Path file = directory.resolve(REFLECT_CONFIG_FILE);
        ArrayNode target = readArray(file);
        MergeNativeImageMetadata.mergeArrayNodes(source, target);
        write(file, target);
    }

    private static void mergeResourceConfig(Path directory, ArrayNode source) throws IOException {
        Path file = directory.resolve(RESOURCE_CONFIG_FILE);
        ObjectNode document = readObject(file);
        ArrayNode target = ensureArray(ensureObject(document, "resources"), "includes");
        MergeNativeImageMetadata.mergeArrayNodes(source, target);
        write(file, document);
    }

    private static ObjectNode readObject(Path file) {
        JsonNode node = read(file);
        return node instanceof ObjectNode object ? object : objectMapper.createObjectNode();
    }

    private static ArrayNode readArray(Path file) {
        JsonNode node = read(file);
        return node instanceof ArrayNode array ? array : objectMapper.createArrayNode();
    }

    private static JsonNode read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return objectMapper.readTree(file.toFile());
    }

    private static ObjectNode ensureObject(ObjectNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node instanceof ObjectNode object) {
            return object;
        }
        ObjectNode created = objectMapper.createObjectNode();
        parent.set(field, created);
        return created;
    }

    private static ArrayNode ensureArray(ObjectNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node instanceof ArrayNode array) {
            return array;
        }
        ArrayNode created = objectMapper.createArrayNode();
        parent.set(field, created);
        return created;
    }

    private static void write(Path file, JsonNode node) throws IOException {
        Path directory = file.getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(file.toString()), node);
    }
}
