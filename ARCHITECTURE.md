# MCP Java SDK — 架构说明

> 本文档帮助你理解项目的整体架构、模块划分和核心流程。

---

## 一、项目定位

**mcp-java** 是一个轻量级 MCP（Model Context Protocol）协议的 Java 实现，侧重于 **Server 端**。

让 Java 开发者能通过简单的注解，把自己的工具暴露给 MCP 兼容的 AI 客户端（Claude Desktop、Cursor 等）。

---

## 二、模块架构

```
┌─────────────────────────────────────────────────────┐
│                    McpServer                         │
│                  (引导类 / Builder)                    │
├─────────────────────────────────────────────────────┤
│                                                       │
│   ┌──────────────┐    ┌──────────────────────────┐   │
│   │  Transport    │    │   McpProtocolHandler      │   │
│   │  (传输层)     │◄──►│   (MCP 协议状态机)        │   │
│   ├──────────────┤    │   - initialize            │   │
│   │ StdioTransport│    │   - tools/list            │   │
│   │ SseTransport │    │   - tools/call            │   │
│   └──────────────┘    └──────────┬───────────────┘   │
│                                  │                    │
│                         ┌────────▼──────────────┐    │
│                         │    ToolRegistry        │    │
│                         │    (工具注册中心)       │    │
│                         └────────┬──────────────┘    │
│                                  │                    │
│                     ┌────────────▼────────────┐       │
│                     │   AnnotationScanner      │       │
│                     │   (注解扫描 → 注册)      │       │
│                     └────────────┬────────────┘       │
│                                  │                    │
│                     ┌────────────▼────────────┐       │
│                     │     你的 @McpTool 方法    │       │
│                     └─────────────────────────┘       │
│                                                       │
├─────────────────────────────────────────────────────┤
│              配置 / 安全                              │
│  ┌──────────────┐  ┌──────────────┐                  │
│  │McpServerConfig│  │ FileSandbox  │                  │
│  │ (环境变量/CLI)│  │ (文件沙箱)   │                  │
│  └──────────────┘  └──────────────┘                  │
└─────────────────────────────────────────────────────┘
```

---

## 三、核心模块说明

### 3.1 传输层 (`transport/`)

| 类 | 作用 | 通信方式 |
|----|------|---------|
| `Transport` | 传输层接口 | — |
| `StdioTransport` | 标准输入输出 | 逐行 JSON（stdin/stdout） |
| `SseTransport` | HTTP + SSE | POST 接收 + SSE 推送 |

**选择依据：**
- **stdio** — Claude Desktop 等桌面 AI 客户端使用，进程启动后通过 stdin/stdout 通信
- **SSE** — 独立 Web 服务，适合远程访问、多客户端、Docker 部署

### 3.2 协议层 (`protocol/`)

**JSON-RPC 2.0 消息模型：**

```
JsonRpcMessage (抽象基类)
├── JsonRpcRequest      # 请求（含 id + method + params）
├── JsonRpcResponse     # 成功响应（含 id + result）
├── JsonRpcErrorResponse  # 错误响应（含 id + error.code + error.message）
└── JsonRpcNotification # 通知（无 id，不需要响应）
```

**MCP 协议处理：**

`McpProtocolHandler` 实现了 MCP 协议的核心状态机，当前支持：

| 方法 | 说明 |
|------|------|
| `initialize` | 握手，返回协议版本 + 服务信息 + 能力声明 |
| `notifications/initialized` | 客户端通知已就绪 |
| `tools/list` | 返回工具列表（含 JSON Schema） |
| `tools/call` | 调用指定工具并返回结果 |

### 3.3 注册层 (`registry/`)

| 类 | 作用 |
|----|------|
| `ToolRegistry` | 线程安全的工具注册中心 |
| `ToolDefinition` | 工具定义（名称、描述、输入 Schema、调用器） |
| `AnnotationScanner` | 扫描 @McpToolProvider 类，注册 @McpTool 方法 |

### 3.4 注解层 (`annotation/`)

| 注解 | 作用 |
|------|------|
| `@McpToolProvider` | 标记类为工具提供者 |
| `@McpTool(name, description)` | 标记方法为 MCP 工具 |
| `@McpParam("名称")` | 标记方法参数，自动生成 JSON Schema |

### 3.5 安全层

| 模块 | 作用 |
|------|------|
| `McpServerConfig` | 统一配置（环境变量 + CLI），SSE API Key 认证 |
| `FileSandbox` | 文件操作路径限制，防御路径穿越攻击 |

---

## 四、核心流程

### stdio 模式

```
AI 客户端 (Claude Desktop)
       │
       │  启动子进程: java -jar mcp-java.jar
       │
       ├── stdin ────→  {"id":1,"method":"initialize"}
       │
       │←── stdout ────  {"id":1,"result":{...}}
       │
       ├── stdin ────→  {"id":2,"method":"tools/list"}
       │
       │←── stdout ────  {"id":2,"result":{"tools":[...]}}
       │
       ├── stdin ────→  {"id":3,"method":"tools/call",
       │                "params":{"name":"get_weather",...}}
       │
       │←── stdout ────  {"id":3,"result":{"content":[...]}}
       │
       ...
       │
       └── 进程关闭（stdin EOF）
```

### SSE 模式

```
AI 客户端                          mcp-java Server
   │                                     │
   │──── GET /sse ──────────────────────►│   建立 SSE 连接
   │◄──── event: endpoint ───────────────│   获取 POST 地址
   │      data: /message?sessionId=xxx   │
   │                                     │
   │──── POST /message ────────────────►│   发送 JSON-RPC
   │      {"id":1,"method":"initialize"} │
   │◄──── 202 Accepted ◄────────────────│   立即确认
   │◄──── event: message ───────────────│   SSE 推送响应
   │      data: {"id":1,"result":{...}}  │
   │                                     │
   │──── POST /message ────────────────►│   ...
   │                                     │
   │──── (断开 SSE 连接) ───────────────►│   清理 session
```

---

## 五、配置体系

优先级：**CLI 参数 > 环境变量 > 默认值**

| 配置项 | CLI | 环境变量 | 默认 |
|--------|-----|---------|------|
| 传输模式 | `--transport` | `MCP_TRANSPORT` | `stdio` |
| SSE 端口 | `--port` | `MCP_PORT` | `8080` |
| 监听地址 | `--host` | `MCP_HOST` | `localhost` |
| API 密钥 | `--api-key` | `MCP_API_KEY` | (无) |
| 文件沙箱 | `--file-root` | `MCP_FILE_ROOT` | (无) |
| 服务名称 | `--name` | `MCP_SERVER_NAME` | `mcp-java` |

---

## 六、依赖清单

| 依赖 | 用途 | 是否必须 |
|------|------|---------|
| Jackson 2.17.2 | JSON 序列化 / 反序列化 | ✅ |
| picocli 4.7.6 | CLI 参数解析 | ❌（optional，没有也能用） |
| SLF4J 2.0.13 | 日志门面 | ✅ |
| Logback 1.5.6 | 日志实现 | ✅ |
| JUnit 5.10.3 | 测试 | ❌（test scope） |

零第三方框架依赖，**不捆绑 Spring Boot**，谁都能用。

---

## 七、技术决策记录

| 决策 | 选择 | 原因 |
|------|------|------|
| Java 版本 | 21+ | 虚拟线程、模式匹配、密封类 |
| 传输层 | 可插拔接口 | stdio 给桌面客户端，SSE 给 Web |
| 构建工具 | Maven | Java 生态最通用的构建工具 |
| API 风格 | 注解驱动 | 类 Spring 体验，Java 开发者熟悉 |
| SSE 实现 | JDK 内置 HttpServer | 零额外依赖 |
| 算术计算器 | 递归下降解析 | 替代 ScriptEngine，防止代码注入 |

---

## 八、后续规划

- [ ] **MCP Resources** — 暴露文件和数据给 AI
- [ ] **MCP Prompts** — 模板化提示词
- [ ] **Spring Boot Starter** — 自动配置
- [ ] **Maven Central** — 发布到中央仓库
- [ ] **CLI 脚手架** — `mcp init my-tools` 一键生成
