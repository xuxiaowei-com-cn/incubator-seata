# Seata Schedule Native

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

[Apache Seata](https://github.com/apache/incubator-seata) 模块的 GraalVM Native Image 可达性元数据自动收集 CI 调度器。

## 项目简介

本仓库用于自动化收集 Seata 模块的
[GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/metadata/)
可达性元数据（`reachability-metadata.json`）。这些元数据是使用 GraalVM Native Image 将 Seata 编译为原生二进制文件的关键。

仓库本身 **不包含 Seata 源代码**——它是一个轻量级的 CI 编排层，在 CI 运行时从 Seata 主仓库检出并构建代码。

## 工作原理

两个 GitHub Actions 工作流按每日计划（UTC `0 0 * * *`）自动执行：

### `schedule-native.yml`

触发 [xuxiaowei-com-cn/incubator-seata](https://github.com/xuxiaowei-com-cn/incubator-seata)
仓库中的上游 `native.yml` 工作流，执行完整的 GraalVM 原生镜像构建。

### `schedule-native-metadata.yml`

核心工作流，负责收集原生镜像元数据：

1. **检出代码** — 从主仓库检出启用 GraalVM 的 Seata 分支
2. **构建** — 使用 Maven 和 GraalVM JDK 25 编译 namingserver 模块
3. **带代理运行** — 使用
   `-agentlib:native-image-agent=config-output-dir=./target/native-image-config`
   启动 namingserver
4. **触发端点** — 运行测试以触发 HTTP 端点，使代理记录所有反射访问、 资源查找、代理类和序列化信息
5. **优雅关闭** — 使用 Spring Boot Actuator shutdown + SIGTERM，确保代理 正确写入 `reachability-metadata.json`
6. **合并配置** — 将收集到的元数据合并到
   `META-INF/native-image/org.apache.seata/seata-namingserver`
7. **差异通知** — 将原生镜像配置差异发送到企业微信，并上传为工作流产物

### 支持的平台

在不同操作系统/架构上分别收集元数据：

| 操作系统     | 架构                  |
|--------------|-----------------------|
| Ubuntu 24.04 | x86_64                |
| Ubuntu 24.04 | ARM64                 |
| macOS 26     | Intel (x86_64)        |
| macOS 26     | Apple Silicon (ARM64) |

> **注意：** Windows 已被排除，因为它不支持 native-image-agent 写元数据
> 所需的优雅 JVM 关闭信号（actuator shutdown + SIGTERM + kill -0）。

## 仓库结构

```
incubator-seata-schedule-native/
├── .github/workflows/
│   ├── schedule-native.yml              # 触发上游原生构建
│   └── schedule-native-metadata.yml     # 核心元数据收集工作流
├── script/
│   └── send_wechat_work.py              # 企业微信差异通知脚本
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

默认情况下，工作流从 `xuxiaowei-com-cn/incubator-seata` 的
`xuxiaowei/Seata-Namingserver-GraalVM` 分支构建。可通过 workflow dispatch 输入参数覆盖：

- `repository` — 要检出的仓库（如 `apache/incubator-seata`）
- `ref` — 要构建的分支或标签

## 与上游的关系

本仓库源自 [Apache Seata](https://seata.apache.org/)，这是 Apache 软件基金会 下的孵化项目。`xuxiaowei/schedule-native`
分支移除了所有 Seata 源代码，仅保留 CI 编排层，允许独立迭代自动化流水线，同时在构建时引用启用 GraalVM 的 Seata 分支。

## 许可证

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) — 与 Apache Seata 相同。
