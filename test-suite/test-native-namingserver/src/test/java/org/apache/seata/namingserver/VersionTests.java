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
package org.apache.seata.namingserver;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for Namingserver version API.
 *
 * <p>Verifies the version endpoint {@code GET /version.json}
 * returns a response containing the version field.
 */
class VersionTests {

    /** HTTP client for sending requests. */
    RestTemplate restTemplate = new RestTemplate();

    /** Namingserver version endpoint URL. */
    String url = "http://127.0.0.1:8081/version.json";

    /**
     * Test querying the version endpoint.
     *
     * <p>Expects a non-null response with a non-null version field.
     */
    @Test
    void versionSuccess() {
        Map map = restTemplate.getForObject(url, Map.class);
        assertNotNull(map);
        assertNotNull(map.get("version"));
    }

}
