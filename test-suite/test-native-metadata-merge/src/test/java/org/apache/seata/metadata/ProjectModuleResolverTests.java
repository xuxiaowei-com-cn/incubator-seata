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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link ProjectModuleResolver}.
 *
 * <p>Every test builds a small project below the temporary directory: a module
 * is a POM with a source tree, which is the only information the resolver reads.
 */
class ProjectModuleResolverTests {

    /** Temporary project directory of the test. */
    @TempDir
    Path projectBase;

    private ProjectModuleResolver resolver;

    @BeforeEach
    void indexProject() throws IOException {
        writeModule(
                "core",
                """
                <project>
                    <parent>
                        <groupId>org.apache.seata</groupId>
                        <artifactId>seata-parent</artifactId>
                    </parent>
                    <artifactId>seata-core</artifactId>
                </project>
                """,
                "org/apache/seata/core/context/ContextCore.java");
        writeModule(
                "server",
                """
                <project>
                    <groupId>org.apache.seata</groupId>
                    <artifactId>seata-server</artifactId>
                </project>
                """,
                "org/apache/seata/server/Server.java");
        resolver = ProjectModuleResolver.forProject(projectBase);
    }

    /**
     * A class of a library module is resolved to the per-section metadata
     * directory of that module, named after its Maven coordinates.
     */
    @Test
    void resolvesLibraryClassToCoordinatesDirectory() {
        NativeImageMetadataDistributor.LegacyTarget target = assertInstanceOf(
                NativeImageMetadataDistributor.LegacyTarget.class,
                resolver.resolve("org.apache.seata.core.context.ContextCore"));

        assertEquals(
                projectBase.resolve("core/src/main/resources/META-INF/native-image/org.apache.seata/seata-core"),
                target.directory());
    }

    /**
     * A class of the server module is resolved to the unified metadata file of
     * that module.
     */
    @Test
    void resolvesServerClassToUnifiedFile() {
        NativeImageMetadataDistributor.UnifiedTarget target = assertInstanceOf(
                NativeImageMetadataDistributor.UnifiedTarget.class, resolver.resolve("org.apache.seata.server.Server"));

        assertEquals(
                projectBase.resolve("server/src/main/resources/META-INF/native-image/reachability-metadata.json"),
                target.file());
    }

    /**
     * Inner classes and generated classes of a module are owned by that module,
     * and the classes of the legacy {@code io.seata} package are owned by the
     * module of the corresponding {@code org.apache.seata} class.
     */
    @Test
    void resolvesInnerClassesAndLegacyPackageNames() {
        Path core = projectBase.resolve("core/src/main/resources/META-INF/native-image/org.apache.seata/seata-core");

        assertEquals(
                core,
                assertInstanceOf(
                                NativeImageMetadataDistributor.LegacyTarget.class,
                                resolver.resolve("org.apache.seata.core.context.ContextCore$$SpringCGLIB$$0"))
                        .directory());
        assertEquals(
                core,
                assertInstanceOf(
                                NativeImageMetadataDistributor.LegacyTarget.class,
                                resolver.resolve("io.seata.core.context.ContextCore"))
                        .directory());
    }

    /** Classes that are not part of the project have no destination. */
    @Test
    void resolvesNothingForThirdPartyClasses() {
        assertNull(resolver.resolve("ch.qos.logback.classic.LoggerContext"));
        assertNull(resolver.resolve("java.lang.String"));
    }

    private void writeModule(String module, String pom, String... classes) throws IOException {
        Path moduleDirectory = projectBase.resolve(module);
        Files.createDirectories(moduleDirectory);
        Files.writeString(moduleDirectory.resolve("pom.xml"), pom);
        for (String clazz : classes) {
            Path source = moduleDirectory.resolve("src/main/java").resolve(clazz);
            Files.createDirectories(source.getParent());
            Files.writeString(source, "package " + clazz.replace("/", ".").replaceAll("\\.java$", "") + ";\n");
        }
    }
}
