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
	package-namingserver-native-metadata run-namingserver-native-metadata \
	run-test-native-spring-boot run-test-native \
	run-merge-native-namingserver \
	package-namingserver-native run-namingserver-native-mode-file package-run-namingserver-native-mode-file

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

# Dynamically resolve the namingserver version from the Maven project (e.g. 2.8.0-SNAPSHOT)
SERVER_VERSION ?= $(shell $(MVN) help:evaluate -Dexpression=project.version -q -DforceStdout)
NATIVE_PLATFORM=$(shell $(MVN) help:evaluate -Dexpression=native.platform -q -DforceStdout)
SERVER_NATIVE_NAME ?= seata-namingserver-$(SERVER_VERSION)-$(NATIVE_PLATFORM)

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

package-namingserver-native-metadata: ## Build namingserver JAR for GraalVM native-image metadata collection
	$(MVN) $(MAVEN_ARGS) clean -e install -DskipTests -pl namingserver -am
	$(MVN) $(MAVEN_ARGS) clean -e package -DskipTests -pl namingserver -Prelease-seata-jar

run-namingserver-native-metadata: ## Run namingserver with GraalVM native-image agent to collect reflection/config metadata
	${GRAALVM_HOME}/bin/java -agentlib:native-image-agent=config-output-dir=./target/native-image-config -jar ./namingserver/target/seata-namingserver.jar --console.user.username=seata  --console.user.password=seata

run-merge-native-namingserver: ## Merge collected native-image metadata into the namingserver resource directory
	python3 script/native/merge_native_image_config.py --target-dir namingserver/src/main/resources/META-INF/native-image/org.apache.seata/seata-namingserver

package-namingserver-native: spotless-apply ## Build namingserver GraalVM native image (requires GraalVM with native-image)
	$(MVN) $(MAVEN_ARGS) clean -e package -DskipTests -pl namingserver spring-boot:process-aot -Pnative native:compile

run-namingserver-native-mode-file: ## Run the namingserver native image binary directly
	./namingserver/target/$(SERVER_NATIVE_NAME)

package-run-namingserver-native-mode-file: package-namingserver-native ## Build native image and then run it
	./namingserver/target/$(SERVER_NATIVE_NAME)
