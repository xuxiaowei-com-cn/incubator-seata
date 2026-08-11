# Seata Schedule Native

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

[Apache Seata](https://github.com/apache/incubator-seata) 模块的 GraalVM Native Image 可达性元数据自动收集 CI 调度器。

## 项目简介

本仓库用于自动化收集 Seata 模块的
[GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/metadata/)
可达性元数据（`reachability-metadata.json`）。这些元数据是使用 GraalVM Native Image 将 Seata 编译为原生二进制文件的关键。

仓库本身 **不包含 Seata 源代码**——它是一个轻量级的 CI 编排层，在 CI 运行时从 Seata 主仓库检出并构建代码。

## 工作原理

四个 GitHub Actions 工作流按每日计划（UTC `0 0 * * *`）自动执行：

### `schedule-native-namingserver.yml`

触发 [apache/incubator-seata](https://github.com/apache/incubator-seata)（`2.x` 分支）
中的上游 `native-namingserver.yml` 工作流，执行完整的 GraalVM 原生镜像构建，
并下载构建产物（Linux ARM64、Linux X64、Windows X64、macOS ARM64）。

### `schedule-native-namingserver-metadata.yml`

从 `apache/incubator-seata@2.x` 收集 **namingserver** 模块的原生镜像元数据：

1. **检出代码** — 检出 Seata `2.x` 分支
2. **构建** — 使用 Maven 和 GraalVM JDK 25 编译 namingserver 模块
3. **带代理运行** — 使用
   `-agentlib:native-image-agent=config-output-dir=./target/native-image-config`
   启动 namingserver
4. **启动 Seata server** — 启动配套的 Seata server 供 namingserver 注册
5. **触发端点** — 运行原生测试以触发 HTTP 端点，使代理记录所有反射访问、
   资源查找、代理类和序列化信息
6. **优雅关闭** — 使用 Spring Boot Actuator shutdown + SIGTERM，确保代理
   正确写入 `reachability-metadata.json`
7. **合并配置** — 将收集到的元数据合并到
   `META-INF/native-image/org.apache.seata/seata-namingserver`
8. **差异通知** — 将原生镜像配置差异发送到企业微信，并上传为工作流产物

### `schedule-native-server-metadata-file.yml`

从 `xuxiaowei-com-cn/incubator-seata@xuxiaowei/Seata-Server-GraalVM` 收集
**server** 模块的原生镜像元数据（使用**基于文件**的注册和配置）：

1. **检出与构建** — 检出并编译 server 模块
2. **带代理运行** — 使用文件注册/配置模式启动 server JAR 并挂载 native-image-agent
3. **启动 test-native-server** — 启动配套测试服务器以触发端点（仅 Linux）
4. **触发端点** — 运行原生测试收集元数据（仅 Linux）
5. **优雅关闭** — Actuator shutdown + SIGTERM 确保元数据正确写出
6. **合并与差异** — 合并配置并将差异发送到企业微信

> **注意：** 在非 Linux 运行器上，test-native-server 和端点测试会被跳过，
> 但 server 仍会启动并收集基线元数据。

### `schedule-native-server-metadata-nacos.yml`

从 `xuxiaowei-com-cn/incubator-seata@xuxiaowei/Seata-Server-GraalVM` 收集
**server** 模块的原生镜像元数据（使用**基于 Nacos** 的注册和配置）：

1. **启动 Nacos** — 启动 Nacos Docker 容器用于服务发现和配置（仅 Linux）
2. **检出与构建** — 检出并编译 server 模块
3. **带代理运行** — 使用 Nacos 注册/配置模式启动 server JAR 并挂载
   native-image-agent（非 Linux 运行器回退到文件模式）
4. **启动 test-native-server** — 启动配套测试服务器（仅 Linux）
5. **触发端点** — 运行原生测试收集元数据（仅 Linux）
6. **优雅关闭** — Actuator shutdown + SIGTERM（Nacos 模式下 server 模块跳过 actuator shutdown）
7. **合并与差异** — 合并配置并将差异发送到企业微信

> **注意：** 在非 Linux 运行器上，由于 Nacos（Docker）不可用，会回退到
> `file` 注册和配置模式。

### 支持的平台

在不同操作系统/架构上分别收集元数据：

| 操作系统     | 架构                  |
|--------------|-----------------------|
| Ubuntu 24.04 | x86_64                |
| Ubuntu 24.04 | ARM64                 |
| macOS 26     | Apple Silicon (ARM64) |

> **注意：** macOS Intel (x86_64) 不被支持，因为 GraalVM 
> 已停止对该平台的更新。
> 参见 [GraalVM CE JDK 25.0.1 发行说明](https://github.com/graalvm/graalvm-ce-builds/releases/tag/jdk-25.0.1)。
>
> **注意：** Windows 已被排除，因为它不支持 native-image-agent 写元数据
> 所需的优雅 JVM 关闭信号（actuator shutdown + SIGTERM + kill -0）。

## 仓库结构

```
incubator-seata-schedule-native/
├── .github/workflows/
│   ├── schedule-native-namingserver.yml              # 触发上游 namingserver 原生构建
│   ├── schedule-native-namingserver-metadata.yml     # 收集 namingserver 元数据
│   ├── schedule-native-server-metadata-file.yml      # 收集 server 元数据（文件 注册/配置/储存）
│   └── schedule-native-server-metadata-nacos.yml     # 收集 server 元数据（Nacos 注册/配置）
├── script/
│   └── send_wechat_work.py                           # 企业微信差异通知脚本
└── .gitignore
```

## 配置

### 必需密钥

| 密钥                 | 说明                                   |
|----------------------|----------------------------------------|
| `WECHAT_WEBHOOK_URL` | 用于发送差异通知的企业微信 Webhook URL |

### 执行计划

工作流每天 UTC 零点自动执行。你也可以通过 **Actions** 标签页 → **workflow_dispatch** 手动触发，并可选择指定自定义仓库和分支/标签。

### 自定义仓库

各工作流使用的默认仓库和分支：

| Workflow                                | 默认仓库                           | 默认分支                         |
|-----------------------------------------|------------------------------------|----------------------------------|
| `schedule-native-namingserver`          | `apache/incubator-seata`           | `2.x`                            |
| `schedule-native-namingserver-metadata` | `apache/incubator-seata`           | `2.x`                            |
| `schedule-native-server-metadata-file`  | `xuxiaowei-com-cn/incubator-seata` | `xuxiaowei/Seata-Server-GraalVM` |
| `schedule-native-server-metadata-nacos` | `xuxiaowei-com-cn/incubator-seata` | `xuxiaowei/Seata-Server-GraalVM` |

可通过 workflow dispatch 输入参数覆盖：

- `repository` — 要检出的仓库（如 `apache/incubator-seata`）
- `ref` — 要构建的分支或标签

## 与上游的关系

本仓库源自 [Apache Seata](https://seata.apache.org/)，这是 Apache 软件基金会 下的孵化项目。`xuxiaowei/schedule-native`
分支移除了所有 Seata 源代码，仅保留 CI 编排层，允许独立迭代自动化流水线，同时在构建时引用启用 GraalVM 的 Seata 分支。

## 许可证

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) — 与 Apache Seata 相同。
