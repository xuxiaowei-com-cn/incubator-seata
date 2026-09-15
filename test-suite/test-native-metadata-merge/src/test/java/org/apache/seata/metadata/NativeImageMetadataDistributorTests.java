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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link NativeImageMetadataDistributor}.
 *
 * <p>The tests use a target resolver that owns the {@code org.apache.seata.core}
 * classes, so the destination of an entry is visible in the assertions without
 * depending on the layout of the project.
 */
class NativeImageMetadataDistributorTests {

    /** Shared Jackson ObjectMapper for reading and writing JSON. */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** Temporary project directory of the test. */
    @TempDir
    Path projectBase;

    /**
     * A reflection entry of a library module is written into the
     * {@code reflect-config.json} of that module - with the {@code name} field
     * of the legacy format instead of the {@code type} field of the unified
     * format - and the canonical file is not created.
     */
    @Test
    void distributesReflectionEntryToReflectConfig() throws IOException {
        JsonNode generated = read(
                """
                {
                  "reflection" : [ {
                    "type" : "org.apache.seata.core.context.ContextCore",
                    "methods" : [ {
                      "name" : "<init>",
                      "parameterTypes" : [ ]
                    } ]
                  } ]
                }
                """);

        distributor().distribute(generated, canonicalFile());

        JsonNode entries = read(coreMetadataDirectory().resolve(NativeImageMetadataDistributor.REFLECT_CONFIG_FILE));
        assertEquals(1, entries.size());
        assertEquals(
                "org.apache.seata.core.context.ContextCore",
                entries.get(0).get("name").asString());
        assertNull(entries.get(0).get("type"), "legacy reflect-config.json uses name, not type");
        assertEquals("<init>", entries.get(0).get("methods").get(0).get("name").asString());
        assertFalse(Files.exists(canonicalFile()), "the canonical file must not receive the entry");
    }

    /**
     * A resource entry that describes an extension point of a library module is
     * written into the {@code resource-config.json} of that module, using the
     * {@code pattern} field of the legacy format.
     */
    @Test
    void distributesResourceEntryToResourceConfig() throws IOException {
        JsonNode generated = read(
                """
                {
                  "resources" : [ {
                    "glob" : "META-INF/services/org.apache.seata.core.context.ContextCore"
                  }, {
                    "glob" : "META-INF/seata/org.apache.seata.core.rpc.hook.RpcHook"
                  } ]
                }
                """);

        distributor().distribute(generated, canonicalFile());

        JsonNode config = read(coreMetadataDirectory().resolve(NativeImageMetadataDistributor.RESOURCE_CONFIG_FILE));
        JsonNode includes = config.get("resources").get("includes");
        assertEquals(2, includes.size());
        assertEquals(
                "META-INF/services/org.apache.seata.core.context.ContextCore",
                includes.get(0).get("pattern").asString());
        assertEquals(
                "META-INF/seata/org.apache.seata.core.rpc.hook.RpcHook",
                includes.get(1).get("pattern").asString());
        assertFalse(Files.exists(canonicalFile()), "the canonical file must not receive the entry");
    }

    /**
     * Entries that do not belong to a class of the project stay in the canonical
     * file, together with the entries that describe a module resource.
     */
    @Test
    void keepsEntriesWithoutModuleInCanonicalFile() throws IOException {
        JsonNode generated = read(
                """
                {
                  "reflection" : [ {
                    "type" : "ch.qos.logback.classic.LoggerContext"
                  }, {
                    "type" : "org.apache.seata.core.context.ContextCore"
                  } ],
                  "resources" : [ {
                    "glob" : "application.yml"
                  }, {
                    "module" : "jdk.jfr",
                    "glob" : "jdk/jfr/internal/query/view.ini"
                  } ]
                }
                """);

        distributor().distribute(generated, canonicalFile());

        JsonNode canonical = read(canonicalFile());
        assertEquals(1, canonical.get("reflection").size());
        assertEquals(
                "ch.qos.logback.classic.LoggerContext",
                canonical.get("reflection").get(0).get("type").asString());
        assertEquals(2, canonical.get("resources").size());
        assertEquals(
                "application.yml", canonical.get("resources").get(0).get("glob").asString());
        assertEquals("jdk.jfr", canonical.get("resources").get(1).get("module").asString());
        assertTrue(
                Files.exists(coreMetadataDirectory().resolve(NativeImageMetadataDistributor.REFLECT_CONFIG_FILE)),
                "the library entry must be written into the module");
    }

    /**
     * A generated entry never overwrites a curated entry of the target: the
     * existing methods and fields are kept and missing ones are added.
     */
    @Test
    void keepsCuratedEntriesOfTheTarget() throws IOException {
        write(
                canonicalFile(),
                """
                {
                  "reflection" : [ {
                    "type" : "ch.qos.logback.classic.LoggerContext",
                    "methods" : [ {
                      "name" : "getLoggerContext",
                      "parameterTypes" : [ ]
                    } ]
                  } ],
                  "foreign" : {
                    "downcalls" : [ {
                      "returnType" : "void",
                      "parameterTypes" : [ "jlong" ]
                    } ]
                  }
                }
                """);
        write(
                coreMetadataDirectory().resolve(NativeImageMetadataDistributor.REFLECT_CONFIG_FILE),
                """
                [ {
                  "name" : "org.apache.seata.core.context.ContextCore",
                  "methods" : [ {
                    "name" : "getRootContext",
                    "parameterTypes" : [ ]
                  } ]
                } ]
                """);
        JsonNode generated = read(
                """
                {
                  "reflection" : [ {
                    "type" : "ch.qos.logback.classic.LoggerContext"
                  }, {
                    "type" : "org.apache.seata.core.context.ContextCore",
                    "methods" : [ {
                      "name" : "<init>",
                      "parameterTypes" : [ ]
                    } ]
                  } ]
                }
                """);

        distributor().distribute(generated, canonicalFile());

        JsonNode canonical = read(canonicalFile());
        assertEquals(1, canonical.get("reflection").size());
        assertEquals(
                "getLoggerContext",
                canonical
                        .get("reflection")
                        .get(0)
                        .get("methods")
                        .get(0)
                        .get("name")
                        .asString());
        assertEquals(1, canonical.get("foreign").get("downcalls").size());

        JsonNode entries = read(coreMetadataDirectory().resolve(NativeImageMetadataDistributor.REFLECT_CONFIG_FILE));
        assertEquals(1, entries.size(), "the entry must not be duplicated");
        assertEquals(2, entries.get(0).get("methods").size(), "the missing method must be added");
    }

    /**
     * The distribution is idempotent: running it twice with the same generated
     * metadata leaves the files unchanged, so a scheduled collection without new
     * metadata does not report a difference.
     */
    @Test
    void distributesIdempotently() throws IOException {
        JsonNode generated = read(
                """
                {
                  "reflection" : [ {
                    "type" : "org.apache.seata.core.context.ContextCore"
                  }, {
                    "type" : "ch.qos.logback.classic.LoggerContext"
                  } ],
                  "resources" : [ {
                    "glob" : "META-INF/services/org.apache.seata.core.context.ContextCore"
                  } ]
                }
                """);

        distributor().distribute(generated, canonicalFile());
        String reflectConfig =
                Files.readString(coreMetadataDirectory().resolve(NativeImageMetadataDistributor.REFLECT_CONFIG_FILE));
        String resourceConfig =
                Files.readString(coreMetadataDirectory().resolve(NativeImageMetadataDistributor.RESOURCE_CONFIG_FILE));
        String canonical = Files.readString(canonicalFile());

        distributor().distribute(generated, canonicalFile());

        assertEquals(
                reflectConfig,
                Files.readString(coreMetadataDirectory().resolve(NativeImageMetadataDistributor.REFLECT_CONFIG_FILE)));
        assertEquals(
                resourceConfig,
                Files.readString(coreMetadataDirectory().resolve(NativeImageMetadataDistributor.RESOURCE_CONFIG_FILE)));
        assertEquals(canonical, Files.readString(canonicalFile()));
    }

    /**
     * An entry of another unified module is written into the unified file of that
     * module instead of the canonical file.
     */
    @Test
    void distributesToTheUnifiedFileOfAnotherModule() throws IOException {
        Path namingserver = projectBase.resolve("namingserver/src/main/resources/META-INF/native-image/"
                + NativeImageMetadataDistributor.UNIFIED_METADATA_FILE);
        NativeImageMetadataDistributor distributor =
                new NativeImageMetadataDistributor(className -> className.startsWith("org.apache.seata.namingserver.")
                        ? new NativeImageMetadataDistributor.UnifiedTarget(namingserver)
                        : null);
        JsonNode generated = read(
                """
                {
                  "reflection" : [ {
                    "type" : "org.apache.seata.namingserver.NamingServer"
                  } ]
                }
                """);

        distributor.distribute(generated, canonicalFile());

        assertEquals(
                "org.apache.seata.namingserver.NamingServer",
                read(namingserver).get("reflection").get(0).get("type").asString());
        assertFalse(Files.exists(canonicalFile()));
    }

    /**
     * Creates the distributor used by the tests: the classes of the
     * {@code org.apache.seata.core} and {@code io.seata.core} packages are owned
     * by the simulated core module.
     */
    private NativeImageMetadataDistributor distributor() {
        Path core = coreMetadataDirectory();
        return new NativeImageMetadataDistributor(
                className -> className.startsWith("org.apache.seata.core.") || className.startsWith("io.seata.core.")
                        ? new NativeImageMetadataDistributor.LegacyTarget(core)
                        : null);
    }

    private Path canonicalFile() {
        return projectBase.resolve("server/src/main/resources/META-INF/native-image/"
                + NativeImageMetadataDistributor.UNIFIED_METADATA_FILE);
    }

    private Path coreMetadataDirectory() {
        return projectBase.resolve("core/src/main/resources/META-INF/native-image/org.apache.seata/seata-core");
    }

    private static JsonNode read(Path file) {
        return objectMapper.readTree(file.toFile());
    }

    private static JsonNode read(String json) {
        return objectMapper.readTree(json);
    }

    private static void write(Path file, String json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
    }
}
