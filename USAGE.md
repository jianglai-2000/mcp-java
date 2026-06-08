# 拿到 mcp-java 后怎么用？

> 小白友好的完整指南，从下载到让你的 AI 调用你的 Java 代码。

---

## 场景一：我就想跑起来看看效果（5 分钟）

### 1. 克隆项目

```bash
git clone https://github.com/jianglai-2000/mcp-java.git
cd mcp-java
```

### 2. 打包（第一次会下载依赖，稍等一分钟）

```bash
# 用 Maven Wrapper（不需要装 Maven）
./mvnw clean package -DskipTests
```

### 3. 启动

```bash
java -jar target/mcp-java-0.1.0.jar
```

你会看到控制台输出一行日志，server 已在运行。

### 4. 测试

新开一个终端，敲一行 JSON 喂进去：

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}' | java -jar target/mcp-java-0.1.0.jar
```

或者用文件：

```bash
# 建一个测试文件
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
{"jsonrpc":"2.0","method":"notifications/initialized"}
{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"system_info","arguments":{}}}' > test.txt

# 喂给 server
type test.txt | java -jar target/mcp-java-0.1.0.jar
```

你会看到初始化、工具列表、系统信息一条条打出来。Ctrl+C 退出。

---

## 场景二：我自己写工具，让 AI 调用（10 分钟）

这是核心用法。三步走。

### 第一步：引入依赖

在你的 Maven 项目 `pom.xml` 里加：

```xml
<dependency>
    <groupId>io.mcp</groupId>
    <artifactId>mcp-java</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 第二步：写一个工具类

```java
package com.example;

import io.mcp.server.annotation.*;

@McpToolProvider
public class MyTools {

    @McpTool(name = "get_time", description = "获取当前时间")
    public String getTime() {
        return java.time.LocalDateTime.now().toString();
    }

    @McpTool(name = "greet", description = "跟人打招呼")
    public String greet(@McpParam("name") String name) {
        return "你好，" + name + "！";
    }

    @McpTool(name = "calc", description = "计算两个数之和")
    public int add(@McpParam("a") int a, @McpParam("b") int b) {
        return a + b;
    }
}
```

就这么简单，三个注解：

| 注解 | 写在哪 | 作用 |
|------|--------|------|
| `@McpToolProvider` | 类上 | 告诉框架这个类里有工具 |
| `@McpTool(name, description)` | 方法上 | 标记这是一个 MCP 工具 |
| `@McpParam("参数名")` | 参数上 | 定义工具的参数名 |

框架会自动从方法签名生成 JSON Schema，你不用写任何配置文件。

### 第三步：启动

```java
import io.mcp.server.McpServer;

public class Main {
    public static void main(String[] args) {
        McpServer.create("my-server", "1.0.0")
            .registerTools(new MyTools())
            .build()
            .start();
    }
}
```

运行 `main` 方法，你的工具就通过 MCP 协议暴露出来了。

---

## 场景三：连上 Claude Desktop（3 分钟）

### 1. 打包你的项目

```bash
mvn clean package -DskipTests
```

得到一个 `target/xxx.jar`。

### 2. 配置 Claude Desktop

编辑 Claude Desktop 的配置文件（通常是 `claude_desktop_config.json`）：

```json
{
  "mcpServers": {
    "my-java-tools": {
      "command": "java",
      "args": ["-jar", "D:/projects/my-tools/target/my-tools-1.0.jar"]
    }
  }
}
```

### 3. 重启 Claude Desktop

然后你就可以在对话里说：

> "帮我算一下 25 × 48"
> "现在几点了"
> "跟小明打个招呼"

Claude 会自动调用你写的 Java 方法来回答。

---

## 场景四：部署成 Web 服务（5 分钟）

不想走子进程模式？可以跑成 HTTP 服务：

```bash
java -jar mcp-java-0.1.0.jar --transport sse --port 8080
```

然后你的服务就在 `http://localhost:8080/sse` 上。

```bash
# 测试：发一条初始化消息
curl -X POST http://localhost:8080/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
```

需要认证的话：

```bash
java -jar mcp-java-0.1.0.jar --transport sse --port 8080 --api-key my-secret-key

# 请求时带上 token
curl -X POST http://localhost:8080/message \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer my-secret-key" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
```

限制文件操作范围：

```bash
# Windows
set MCP_FILE_ROOT=C:\allowed\path
java -jar mcp-java-0.1.0.jar

# Linux / macOS
export MCP_FILE_ROOT=/home/user/allowed
java -jar mcp-java-0.1.0.jar
```

---

## 常见问题

### 我没有 Claude Desktop，能测吗？

能。用 `echo` 管道模拟客户端就行：

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' | java -jar mcp-java-0.1.0.jar
```

你会看到 server 返回工具列表的 JSON。

### 必须用 Maven 吗？

最好用，但也可以把 `mcp-java-0.1.0.jar` 放到你的项目里当普通 jar 用。

### 能和其他 AI 客户端连吗？

MCP 是开放协议。只要对方支持 MCP（Cursor、VS Code 的 AI 插件等），都可以连。

### 能跟 Spring Boot 项目一起用吗？

可以。在你的 Spring Boot 项目里引入依赖，启动时调 `McpServer.create()` 就行。

---

## 一句话总结

> **你写 `@McpTool` 注解，AI 直接调你的 Java 方法。** 
> 
> 不用写 HTTP 接口、不用解析 JSON、不用考虑并发——框架都帮你干了。
