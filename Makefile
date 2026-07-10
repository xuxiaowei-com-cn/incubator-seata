# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Use bash as the default shell for consistent behavior across platforms
SHELL := /usr/bin/env bash

# Declare all phony targets (targets that are not actual files)
.PHONY: help
# Set the default goal to 'help' so running `make` without arguments shows usage
.DEFAULT_GOAL := help

help: ## Show help information
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-44s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

# Prefer using the system-installed `mvn`, fall back to the Maven Wrapper (`./mvnw`) if unavailable
MVN ?= $(shell command -v mvn >/dev/null 2>&1 && echo "mvn" || echo "./mvnw")
# Common Maven arguments:
#   -T 4C : use 4 threads per available CPU core for parallel builds
#   -e    : show error stack traces on failure
#   -B    : run in batch (non-interactive) mode
#   -V    : print Maven version information
MAVEN_ARGS ?= -T 4C -e -B -V

NACOS_SERVER_ADDR ?= 127.0.0.1:8848

# Dynamically resolve the server version from the Maven project (e.g. 2.8.0-SNAPSHOT)
SERVER_VERSION ?= $(shell $(MVN) help:evaluate -Dexpression=project.version -q -DforceStdout)
NATIVE_PLATFORM=$(shell $(MVN) help:evaluate -Dexpression=native.platform -q -DforceStdout)
SERVER_NATIVE_NAME ?= seata-server-$(SERVER_VERSION)-$(NATIVE_PLATFORM)

# Declare all GraalVM native-image related phony targets
.PHONY: clean spotless-check spotless-apply checkstyle checkstyle-diff license test package-only package \
	package-server-native-pre \
	package-server-native-metadata-file package-server-native-metadata-file-only run-server-native-metadata-file \
	package-server-native-metadata-nacos package-server-native-metadata-nacos-only run-server-native-metadata-nacos \
	package-server-native package-server-native-only \
	package-test-native-run package-test-native-run-only \
	package-test-native package-test-native-only

clean: ## Clean the project
	$(MVN) $(MAVEN_ARGS) clean -e

spotless-check: ## Run Spotless code format check
	$(MVN) $(MAVEN_ARGS) spotless:check

spotless-apply: ## Apply Spotless code formatting
	$(MVN) $(MAVEN_ARGS) spotless:apply

checkstyle: ## Run global Checkstyle code check
	$(MVN) $(MAVEN_ARGS) clean -e checkstyle:check -Dcheckstyle.skip=false

checkstyle-diff: ## Run Checkstyle code check only on changed .java files
	BASE_REF="$${GITHUB_BASE_REF:-2.x}"; \
	echo "BASE_REF: $${BASE_REF}"; \
	HEAD_SHA="$${PR_HEAD_SHA:-HEAD}"; \
	echo "HEAD_SHA: $${HEAD_SHA}"; \
	if git show-ref --quiet "refs/remotes/origin/$${BASE_REF}"; then \
		DIFF_RANGE="origin/$${BASE_REF}...$${HEAD_SHA}"; \
	else \
		DIFF_RANGE="$${BASE_REF}...$${HEAD_SHA}"; \
	fi; \
	echo "DIFF_RANGE: $${DIFF_RANGE}"; \
	CHANGED_FILES="$$(git diff --name-only --diff-filter=AM "$${DIFF_RANGE}" || true)"; \
	echo "CHANGED_FILES: $${CHANGED_FILES}"; \
	CHECKSTYLE_INCLUDES="$$(echo "$${CHANGED_FILES}" | grep -E '\.java$$' || true)"; \
	CHECKSTYLE_INCLUDES="$$(echo "$${CHECKSTYLE_INCLUDES}" | sed -e 's#.*src/main/java/##g' -e 's#.*src/test/java/##g' | tr '\n' ',' )"; \
	echo "CHECKSTYLE_INCLUDES: $${CHECKSTYLE_INCLUDES}"; \
	if [ -z "$${CHECKSTYLE_INCLUDES//,/}" ]; then \
		echo "No changed .java files detected, skip checkstyle."; \
		exit 0; \
	fi; \
	$(MVN) $(MAVEN_ARGS) clean -e checkstyle:check -Dcheckstyle.skip=false -Dcheckstyle.includes="$${CHECKSTYLE_INCLUDES}"

license: ## Run license check
	$(MVN) $(MAVEN_ARGS) clean -e -Dlicense.skip=false

test: ## Run unit tests
	$(MVN) $(MAVEN_ARGS) clean -e test

package-only: ## Package the project without running tests
	$(MVN) $(MAVEN_ARGS) clean -e package -DskipTests

package: ## Package the project
	$(MVN) $(MAVEN_ARGS) clean -e package

package-server-native-pre: spotless-apply ## Build and install all modules locally (pre-step for native image)
	$(MVN) $(MAVEN_ARGS) clean -e install -DskipTests -pl server -am

package-server-native-metadata-file: package-server-native-pre ## Generate GraalVM native-image metadata files with file registry/config + file storage using the agent (requires GRAALVM_HOME; run the jar manually afterward to collect reflection config)
	$(MVN) $(MAVEN_ARGS) clean -e package -DskipTests -pl server -Prelease-seata-jar
	SEATA_REGISTRY_TYPE=file \
	SEATA_CONFIG_TYPE=file \
	SEATA_STORE_MODE=file \
	${GRAALVM_HOME}/bin/java -agentlib:native-image-agent=config-output-dir=./target/native-image-config \
	-jar ./server/target/seata-server.jar

package-server-native-metadata-file-only: ## Generate GraalVM native-image metadata files with file registry/config + file storage using the agent (requires GRAALVM_HOME; run the jar manually afterward to collect reflection config)
	$(MVN) $(MAVEN_ARGS) clean -e package -DskipTests -pl server -Prelease-seata-jar
	SEATA_REGISTRY_TYPE=file \
	SEATA_CONFIG_TYPE=file \
	SEATA_STORE_MODE=file \
	${GRAALVM_HOME}/bin/java -agentlib:native-image-agent=config-output-dir=./target/native-image-config \
	-jar ./server/target/seata-server.jar

run-server-native-metadata-file: ## Run the server native image with file registry/config and file storage
	SEATA_REGISTRY_TYPE=file \
	SEATA_CONFIG_TYPE=file \
	SEATA_STORE_MODE=file \
	./server/target/$(SERVER_NATIVE_NAME)

package-server-native-metadata-nacos: package-server-native-pre ## Generate GraalVM native-image metadata files with Nacos registry/config + file storage using the agent (requires GRAALVM_HOME, Nacos running; run the jar manually afterward to collect reflection config)
	$(MVN) $(MAVEN_ARGS) clean -e package -DskipTests -pl server -Prelease-seata-jar
	SEATA_REGISTRY_TYPE=nacos \
	SEATA_REGISTRY_NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR} \
	SEATA_CONFIG_TYPE=nacos \
	SEATA_CONFIG_NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR} \
	SEATA_CONFIG_NACOS_DATA_ID=seataServer.properties \
	SEATA_STORE_MODE=file \
	${GRAALVM_HOME}/bin/java -agentlib:native-image-agent=config-output-dir=./target/native-image-config \
	-jar ./server/target/seata-server.jar

package-server-native-metadata-nacos-only: ## Generate GraalVM native-image metadata files with Nacos registry/config + file storage using the agent (requires GRAALVM_HOME, Nacos running; run the jar manually afterward to collect reflection config)
	$(MVN) $(MAVEN_ARGS) clean -e package -DskipTests -pl server -Prelease-seata-jar
	SEATA_REGISTRY_TYPE=nacos \
	SEATA_REGISTRY_NACOS_SERVER_ADDR=127.0.0.1:8848 \
	SEATA_CONFIG_TYPE=nacos \
	SEATA_CONFIG_NACOS_SERVER_ADDR=127.0.0.1:8848 \
	SEATA_CONFIG_NACOS_DATA_ID=seataServer.properties \
	SEATA_STORE_MODE=file \
	${GRAALVM_HOME}/bin/java -agentlib:native-image-agent=config-output-dir=./target/native-image-config \
	-jar ./server/target/seata-server.jar

run-server-native-metadata-nacos: ## Run the server native image with Nacos registry/config and file storage
	SEATA_REGISTRY_TYPE=nacos \
	SEATA_REGISTRY_NACOS_SERVER_ADDR=127.0.0.1:8848 \
	SEATA_CONFIG_TYPE=nacos \
	SEATA_CONFIG_NACOS_SERVER_ADDR=127.0.0.1:8848 \
	SEATA_CONFIG_NACOS_DATA_ID=seataServer.properties \
	SEATA_STORE_MODE=file \
	./server/target/$(SERVER_NATIVE_NAME)

package-server-native: package-server-native-pre ## Build server native image (GraalVM) with pre-step
	$(MVN) $(MAVEN_ARGS) clean -e package -DskipTests -pl server -Pnative spring-boot:process-aot native:compile

package-server-native-only: spotless-apply ## Build server native image (GraalVM) without pre-step
	$(MVN) $(MAVEN_ARGS) clean -e package -DskipTests -pl server -Pnative spring-boot:process-aot native:compile

package-test-native-run: ## Build native test suite
	$(MVN) $(MAVEN_ARGS) -Ptest-native -pl test-suite/test-native spotless:apply
	$(MVN) $(MAVEN_ARGS) -Ptest-native -pl test-suite/test-native install -DskipTests -am
	$(MVN) $(MAVEN_ARGS) -Ptest-native -pl test-suite/test-native clean spring-boot:run

package-test-native-run-only: ## Build and run native test suite without pre-step
	$(MVN) $(MAVEN_ARGS) -Ptest-native -pl test-suite/test-native spotless:apply
	$(MVN) $(MAVEN_ARGS) -Ptest-native -pl test-suite/test-native clean spring-boot:run

package-test-native: ## Build native test suite
	$(MVN) $(MAVEN_ARGS) -Ptest-native -pl test-suite/test-native spotless:apply
	$(MVN) $(MAVEN_ARGS) -Ptest-native -pl test-suite/test-native install -DskipTests -am
	$(MVN) $(MAVEN_ARGS) -Ptest-native -pl test-suite/test-native clean package

package-test-native-only: ## Build native test suite without pre-step
	$(MVN) $(MAVEN_ARGS) -Ptest-native -pl test-suite/test-native spotless:apply
	$(MVN) $(MAVEN_ARGS) -Ptest-native -pl test-suite/test-native clean package
