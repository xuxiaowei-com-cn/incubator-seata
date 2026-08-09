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

# Set the default goal to 'help' so running `make` without arguments shows usage
.DEFAULT_GOAL := help

# Declare all phony targets (targets that are not actual files, i.e. they don't produce a file
# matching the target name — make will always execute them regardless of file timestamps)
.PHONY: help clean spotless-check spotless-apply checkstyle checkstyle-diff license test \
	package-only package \
	package-server-native-metadata run-server-native-metadata \
	run-test-native-spring-boot run-test-native \
	test-native-server \
	run-merge-native-server \
	package-server-native run-server-native-mode-file package-run-server-native-mode-file

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

clean: ## Clean the project
	$(MVN) $(MAVEN_ARGS) clean

spotless-check: ## Run Spotless code format check
	$(MVN) $(MAVEN_ARGS) spotless:check

spotless-apply: ## Apply Spotless code formatting
	$(MVN) $(MAVEN_ARGS) spotless:apply -Ptest-native-server

checkstyle: ## Run global Checkstyle code check
	$(MVN) $(MAVEN_ARGS) clean checkstyle:check -Dcheckstyle.skip=false

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
	$(MVN) $(MAVEN_ARGS) clean checkstyle:check -Dcheckstyle.skip=false -Dcheckstyle.includes="$${CHECKSTYLE_INCLUDES}"

license: ## Run license check
	$(MVN) $(MAVEN_ARGS) clean -Dlicense.skip=false

test: ## Run unit tests
	$(MVN) $(MAVEN_ARGS) clean test

package-only: ## Package the project without running tests
	$(MVN) $(MAVEN_ARGS) clean package -DskipTests

package: ## Package the project
	$(MVN) $(MAVEN_ARGS) clean package

package-server-native-metadata: ## Build server JAR for GraalVM native-image metadata collection
	$(MVN) $(MAVEN_ARGS) clean install -DskipTests -pl server -am
	$(MVN) $(MAVEN_ARGS) clean package -DskipTests -pl server -Prelease-seata-jar

run-server-native-metadata: ## Run server with GraalVM native-image agent to collect reflection/config metadata
	${GRAALVM_HOME}/bin/java -agentlib:native-image-agent=config-output-dir=./target/native-image-config -jar ./server/target/seata-server.jar

test-native-server: ## Run server GraalVM native-image compatibility tests (requires GraalVM with native-image)
	$(MVN) $(MAVEN_ARGS) clean test -Ptest-native-server -pl test-suite/test-native-server

run-merge-native-server: ## Merge collected native-image metadata into the server resource directory (required to regenerate native metadata, requires JDK 21+)
	java script/native/MergeNativeImageConfig.java --target-dir server/src/main/resources/META-INF/native-image/org.apache.seata/seata-server

package-server-native: spotless-apply ## Build server GraalVM native image, spotless-apply is automatically executed before building the native image
	$(MVN) $(MAVEN_ARGS) clean package -DskipTests -pl server spring-boot:process-aot -Pnative native:compile

run-server-native-mode-file: ## Run the server native image binary directly
	./server/target/seata-server-$(SERVER_VERSION)-$(NATIVE_PLATFORM)

package-run-server-native-mode-file: package-server-native ## Build native image and then run it
	./server/target/seata-server-$(SERVER_VERSION)-$(NATIVE_PLATFORM)
