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
package org.apache.seata.server.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import org.apache.seata.server.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestPropertySource;

import java.util.Iterator;

/**
 * Tests for logback appender configuration.
 *
 * <p>Note: logstash, kafka, and metric appenders were previously enabled conditionally
 * via Janino {@code <if>} expressions in {@code logback-spring.xml}. These {@code <if>}
 * conditionals were removed because Janino runtime compilation is not supported in
 * GraalVM native images. As a result, the extended appenders are no longer dynamically
 * enabled by the {@code logging.extend.*.enabled} properties. The appender config XML
 * files still exist and can be manually included if needed.
 *
 * @see <a href="https://github.com/apache/incubator-seata/pull/...">PR that removed Janino conditionals</a>
 */
@TestPropertySource(
        properties = {
            "logging.extend.logstash-appender.enabled=true",
            "logging.extend.kafka-appender.enabled=true",
            "logging.extend.kafka-appender.topic=test",
            "logging.extend.metric-appender.enabled=true"
        })
public class AppenderTest extends BaseSpringBootTest {

    @Test
    public void testAppenderEnabled() {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        Iterator<Appender<ILoggingEvent>> appenderIterator =
                lc.getLogger("ROOT").iteratorForAppenders();

        boolean kafkaFound = false;
        boolean metricFound = false;
        boolean logstashFound = false;

        while (appenderIterator.hasNext()) {
            Appender<ILoggingEvent> appender = appenderIterator.next();
            if (appender.getName().equals("KAFKA")) {
                kafkaFound = true;
            }
            if (appender.getName().equals("METRIC")) {
                metricFound = true;
            }
            if (appender.getName().equals("LOGSTASH")) {
                logstashFound = true;
            }
        }

        // Extended appenders (logstash, kafka, metric) are no longer conditionally
        // included since Janino <if> expressions were removed for GraalVM native
        // image compatibility. Even with enabled=true, they should not be present.
        Assertions.assertFalse(kafkaFound, "KAFKA appender should not be present after Janino <if> removal");
        Assertions.assertFalse(metricFound, "METRIC appender should not be present after Janino <if> removal");
        Assertions.assertFalse(logstashFound, "LOGSTASH appender should not be present after Janino <if> removal");
    }
}
