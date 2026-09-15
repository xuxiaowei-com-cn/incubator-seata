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

Eight GitHub Actions workflows run on a daily schedule (`0 0 * * *` UTC):

### `schedule-native-server.yml`

Triggers the upstream `native-server.yml` workflow in
[xuxiaowei-com-cn/incubator-seata](https://github.com/xuxiaowei-com-cn/incubator-seata)
(`xuxiaowei/Seata-Server-GraalVM` branch) to perform a full GraalVM native image build,
then downloads the built artifacts (Linux ARM64, Linux X64, Windows X64, macOS ARM64).

### `schedule-native-namingserver.yml`

Triggers the upstream `native-namingserver.yml` workflow in
[apache/incubator-seata](https://github.com/apache/incubator-seata) (`2.x` branch)
to perform a full GraalVM native image build, then downloads the built artifacts
(Linux ARM64, Linux X64, Windows X64, macOS ARM64).

### `schedule-native-namingserver-metadata.yml`

Collects native-image metadata for the **namingserver** module from
`apache/incubator-seata@2.x`:

1. **Checkout** — Checks out the Seata `2.x` branch
2. **Build** — Compiles the namingserver module with Maven and GraalVM JDK 25
3. **Run with agent** — Starts the namingserver JAR with
   `-agentlib:native-image-agent=config-output-dir=./target/native-image-config`
4. **Start Seata server** — Starts a companion Seata server for the namingserver to register with
5. **Exercise endpoints** — Runs native tests to exercise HTTP endpoints so the agent records all reflective accesses,
   resource lookups, proxies, and serialization
6. **Graceful shutdown** — Uses Spring Boot Actuator shutdown + SIGTERM to ensure the agent properly writes
   `reachability-metadata.json`
7. **Merge configs** — Merges collected metadata into
   `META-INF/native-image/org.apache.seata/seata-namingserver`
8. **Diff notification** — Sends native-image config diffs to WeChat Work and uploads them as workflow artifacts

### `schedule-native-server-metadata-file.yml`

Collects native-image metadata for the **server** module using **file-based**
registry and config from `xuxiaowei-com-cn/incubator-seata@xuxiaowei/Seata-Server-GraalVM`:

1. **Checkout & Build** — Checks out and compiles the server module
2. **Run with agent** — Starts the server JAR with native-image-agent in file registry/config mode
3. **Start test-native-server** — Starts a companion test server for exercising endpoints (Linux only)
4. **Exercise endpoints** — Runs native tests to collect metadata (Linux only)
5. **Graceful shutdown** — Actuator shutdown + SIGTERM for clean metadata collection
6. **Merge & Diff** — Merges configs and sends diffs to WeChat Work

> **Note:** On non-Linux runners, the test-native-server and endpoint exercises
> are skipped, but the server still starts and collects baseline metadata.

### `schedule-native-server-metadata-nacos.yml`

Collects native-image metadata for the **server** module using **Nacos-based**
registry and config from `xuxiaowei-com-cn/incubator-seata@xuxiaowei/Seata-Server-GraalVM`:

1. **Start Nacos** — Starts a Nacos Docker container for service discovery and configuration (Linux only)
2. **Checkout & Build** — Checks out and compiles the server module
3. **Run with agent** — Starts the server JAR with Nacos registry/config and native-image-agent (falls back to file
   mode on non-Linux runners)
4. **Start test-native-server** — Starts a companion test server (Linux only)
5. **Exercise endpoints** — Runs native tests to collect metadata (Linux only)
6. **Graceful shutdown** — Actuator shutdown + SIGTERM (actuator shutdown skipped for server module on Nacos)
7. **Merge & Diff** — Merges configs and sends diffs to WeChat Work

> **Note:** On non-Linux runners, falls back to `file` registry and config since
> Nacos (Docker) is not available.

### `schedule-native-server-metadata-seata.yml`

Collects native-image metadata for the **server** module using the **Seata**
(Raft-based) registry from `xuxiaowei-com-cn/incubator-seata@xuxiaowei/Seata-Server-GraalVM`:

1. **Start NamingServer** — Starts the namingserver module so the server has a registry to register with
2. **Checkout & Build** — Checks out and compiles the server module
3. **Run with agent** — Starts the server JAR with the Seata registry, file config/store and native-image-agent
4. **Start test-native-server** — Starts a companion test server for exercising endpoints (Linux only)
5. **Exercise endpoints** — Runs native tests to collect metadata (Linux only)
6. **Graceful shutdown** — Actuator shutdown + SIGTERM for clean metadata collection
7. **Merge & Diff** — Merges configs and sends diffs to WeChat Work

### `schedule-native-server-metadata-mysql.yml`

Collects native-image metadata for the **server** module running with **MySQL** as the
session/lock store (`store.mode=db`, Druid + `com.mysql.cj.jdbc.Driver`) from
`xuxiaowei-com-cn/incubator-seata@xuxiaowei/Seata-Server-GraalVM`.
This is the mode that records JDBC driver, SQL mapper and connection pool reflection:

1. **Start MySQL** — Starts a MySQL 8.0 Docker container with the `seata` database (Linux only)
2. **Initialize schema** — Imports `script/server/db/mysql.sql` (global/branch/lock/distributed-lock/vgroup
   tables) into the `seata` database and creates `seata_test_native` for the test server
3. **Checkout & Build** — Checks out and compiles the server module
4. **Provide the JDBC driver** — Copies `com.mysql:mysql-connector-j` into `server/target/jdbc`, the
   directory Seata resolves the MySQL driver from
5. **Run with agent** — Starts the server JAR with `store.mode=db` and native-image-agent
6. **Start test-native-server** — Starts a companion test server for exercising endpoints (Linux only)
7. **Exercise endpoints** — Runs native tests to collect metadata (Linux only)
8. **Graceful shutdown** — Actuator shutdown + SIGTERM for clean metadata collection
9. **Merge & Diff** — Merges configs and sends diffs to WeChat Work

> **Note:** On non-Linux runners, MySQL (Docker) and the MySQL JDBC driver are not available,
> so the server falls back to `store.mode=file` and only baseline metadata is collected.

### `schedule-native-server-metadata-modes.yml`

Collects native-image metadata for the **server** module in the Config / Registry / Store modes
tracked in [apache/incubator-seata#8162](https://github.com/apache/incubator-seata/pull/8162).
One matrix job runs per mode on both Linux architectures:

| Mode              | Seata settings         | Dependency                                         |
|-------------------|------------------------|----------------------------------------------------|
| `config-consul`   | `config.type=consul`   | Consul 1.22 (KV entry `seata.properties`)          |
| `config-etcd3`    | `config.type=etcd3`    | etcd 3.5 (key `seata.properties`)                  |
| `config-zk`       | `config.type=zk`       | ZooKeeper 3.9 (node `/seata/seata.properties`)     |
| `registry-consul` | `registry.type=consul` | Consul 1.22                                        |
| `registry-eureka` | `registry.type=eureka` | Eureka 4.1 (amd64 image, x86_64 runner only)       |
| `registry-etcd3`  | `registry.type=etcd3`  | etcd 3.5                                           |
| `registry-redis`  | `registry.type=redis`  | Redis 7.2                                          |
| `registry-zk`     | `registry.type=zk`     | ZooKeeper 3.9                                      |
| `store-redis`     | `store.mode=redis`     | Redis 7.2 (session/lock store, `lua` scripts)      |

Each mode starts its dependency, seeds the config center when the mode needs data
(Consul KV and etcd are seeded, ZooKeeper is created by the server itself), exports the
Seata settings of the mode and then runs the usual agent → tests → merge → diff flow.

The mode logic lives in [`script/modes/`](script/modes) — one `<mode>.sh` per mode
implementing `start` (dependency), `seed` (config center data) and `env` (Seata settings),
so a new mode only needs a script plus a matrix entry. All dependency images are multi-arch
and therefore run on both Linux runners, except `registry-eureka` whose image is
`amd64` only and is therefore excluded from the ARM64 runner. On macOS the dependencies
cannot run, so the server keeps its `file` config/registry/store and only baseline
metadata is collected.

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
│   ├── schedule-native-namingserver.yml              # Triggers upstream namingserver native build
│   ├── schedule-native-server.yml                    # Triggers upstream server native build
│   ├── schedule-native-namingserver-metadata.yml     # Collects namingserver metadata
│   ├── schedule-native-server-metadata-file.yml      # Collects server metadata (file registry/config/store)
│   ├── schedule-native-server-metadata-nacos.yml     # Collects server metadata (Nacos registry/config)
│   ├── schedule-native-server-metadata-seata.yml     # Collects server metadata (Seata registry)
│   ├── schedule-native-server-metadata-mysql.yml     # Collects server metadata (MySQL store)
│   └── schedule-native-server-metadata-modes.yml     # Collects server metadata (Consul/etcd3/ZooKeeper/Redis modes)
├── script/
│   ├── send_wechat_work.py                           # WeChat Work diff notification sender
│   └── modes/                                        # Per-mode dependency setup + Seata settings
│       ├── common.sh                                 # Shared container start/wait helper
│       ├── config-consul.sh / config-etcd3.sh / config-zk.sh
│       └── registry-consul.sh / registry-eureka.sh / registry-etcd3.sh / registry-redis.sh / registry-zk.sh / store-redis.sh
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

Default repositories and branches used by each workflow:

| Workflow                                | Default Repository                 | Default Branch                   |
|-----------------------------------------|------------------------------------|----------------------------------|
| `schedule-native-server`                | `xuxiaowei-com-cn/incubator-seata` | `xuxiaowei/Seata-Server-GraalVM` |
| `schedule-native-namingserver`          | `apache/incubator-seata`           | `2.x`                            |
| `schedule-native-namingserver-metadata` | `apache/incubator-seata`           | `2.x`                            |
| `schedule-native-server-metadata-file`  | `xuxiaowei-com-cn/incubator-seata` | `xuxiaowei/Seata-Server-GraalVM` |
| `schedule-native-server-metadata-nacos` | `xuxiaowei-com-cn/incubator-seata` | `xuxiaowei/Seata-Server-GraalVM` |
| `schedule-native-server-metadata-seata` | `xuxiaowei-com-cn/incubator-seata` | `xuxiaowei/Seata-Server-GraalVM` |
| `schedule-native-server-metadata-mysql` | `xuxiaowei-com-cn/incubator-seata` | `xuxiaowei/Seata-Server-GraalVM` |
| `schedule-native-server-metadata-modes` | `xuxiaowei-com-cn/incubator-seata` | `xuxiaowei/Seata-Server-GraalVM` |

Override via workflow dispatch inputs:

- `repository` — Repository to check out (e.g. `apache/incubator-seata`)
- `ref` — Branch or tag to build

## Relationship to Upstream

This repository is derived from [Apache Seata](https://seata.apache.org/), an incubating project under the Apache
Software Foundation. The `xuxiaowei/schedule-native`
branch strips away all Seata source code and retains only the CI orchestration layer, allowing independent iteration on
the automation pipeline while referencing the GraalVM-enabled Seata branch at build time.

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) — same as Apache Seata.
