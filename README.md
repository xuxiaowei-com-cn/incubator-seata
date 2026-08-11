# Seata Schedule Native

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

Automated CI scheduler for collecting GraalVM Native Image reachability metadata for
[Apache Seata](https://github.com/apache/incubator-seata) modules.

## What It Does

This repository orchestrates the automated collection of
[GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/metadata/)
reachability metadata (`reachability-metadata.json`) for Seata's modules. The collected metadata is essential for
compiling Seata into native binaries using GraalVM Native Image.

The repository itself contains **no Seata source code** — it is a lightweight CI orchestration layer that checks out and
builds the main Seata repository during workflow runs.

## How It Works

Two GitHub Actions workflows run on a daily schedule (`0 0 * * *` UTC):

### `schedule-native.yml`

Triggers the upstream `native.yml` workflow in the
[xuxiaowei-com-cn/incubator-seata](https://github.com/xuxiaowei-com-cn/incubator-seata)
repository to perform a full GraalVM native image build.

### `schedule-native-metadata.yml`

The core workflow that collects native-image metadata:

1. **Checkout** — Checks out the GraalVM-enabled Seata branch from the main repo
2. **Build** — Compiles the namingserver module with Maven and GraalVM JDK 25
3. **Run with agent** — Starts the namingserver JAR with
   `-agentlib:native-image-agent=config-output-dir=./target/native-image-config`
4. **Exercise endpoints** — Runs tests to exercise HTTP endpoints so the agent records all reflective accesses, resource
   lookups, proxies, and serialization
5. **Graceful shutdown** — Uses Spring Boot Actuator shutdown + SIGTERM to ensure the agent properly writes
   `reachability-metadata.json`
6. **Merge configs** — Merges collected metadata into
   `META-INF/native-image/org.apache.seata/seata-namingserver`
7. **Diff notification** — Sends native-image config diffs to WeChat Work and uploads them as workflow artifacts

### Supported Platforms

Metadata is collected on four platforms to account for OS/architecture differences:

| OS           | Architecture          |
|--------------|-----------------------|
| Ubuntu 24.04 | x86_64                |
| Ubuntu 24.04 | ARM64                 |
| macOS 26     | Apple Silicon (ARM64) |

> **Note:** macOS Intel (x86_64) is not supported because GraalVM has
> discontinued updates for that platform.
> See [GraalVM CE JDK 25.0.1 release notes](https://github.com/graalvm/graalvm-ce-builds/releases/tag/jdk-25.0.1).
>
> **Note:** Windows is excluded because it cannot support the graceful JVM
> shutdown signals (actuator shutdown + SIGTERM + kill -0) required for the
> native-image-agent to properly write metadata.

## Repository Structure

```
incubator-seata-schedule-native/
├── .github/workflows/
│   ├── schedule-native.yml              # Triggers upstream native build
│   └── schedule-native-metadata.yml     # Core metadata collection workflow
├── script/
│   └── send_wechat_work.py              # WeChat Work diff notification sender
└── .gitignore
```

## Setup

### Required Secrets

| Secret               | Description                                    |
|----------------------|------------------------------------------------|
| `WECHAT_WEBHOOK_URL` | WeChat Work webhook URL for diff notifications |

### Schedule

The workflows run daily at midnight UTC. You can also trigger them manually via the **Actions** tab →
**workflow_dispatch**, optionally specifying a custom repository and branch/tag.

### Custom Repository

By default, the workflow builds the `xuxiaowei/Seata-Namingserver-GraalVM` branch from
`xuxiaowei-com-cn/incubator-seata`. Override via workflow dispatch inputs:

- `repository` — Repository to check out (e.g. `apache/incubator-seata`)
- `ref` — Branch or tag to build

## Relationship to Upstream

This repository is derived from [Apache Seata](https://seata.apache.org/), an incubating project under the Apache
Software Foundation. The `xuxiaowei/schedule-native`
branch strips away all Seata source code and retains only the CI orchestration layer, allowing independent iteration on
the automation pipeline while referencing the GraalVM-enabled Seata branch at build time.

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) — same as Apache Seata.
