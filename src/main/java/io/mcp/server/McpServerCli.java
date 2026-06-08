package io.mcp.server;

import io.mcp.server.demo.DemoTools;
import io.mcp.server.transport.Transport;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * CLI 入口，启动 MCP 服务器。
 *
 * 使用方式：
 * <pre>
 * # 默认 stdio 模式（给 Claude Desktop 当子进程用）
 * java -jar mcp-java.jar
 *
 * # SSE/HTTP 模式（独立 Web 服务）
 * java -jar mcp-java.jar --transport sse --port 8080
 *
 * # SSE + API Key 认证
 * java -jar mcp-java.jar --transport sse --port 8080 --api-key ***
 *
 * # 文件沙箱 + SSE
 * java -jar mcp-java.jar --transport sse --file-root /data
 * </pre>
 */
@Command(
        name = "mcp-java",
        mixinStandardHelpOptions = true,
        version = "mcp-java 0.1.0",
        description = "轻量级 MCP（Model Context Protocol）服务器，让 AI 客户端调用你的 Java 工具"
)
public class McpServerCli implements Callable<Integer> {

    @Option(names = {"-n", "--name"},
            description = "服务器名称（默认: mcp-java，或环境变量 MCP_SERVER_NAME）")
    private String serverName;

    @Option(names = {"-t", "--transport"},
            description = "传输模式: stdio（默认）| sse（或环境变量 MCP_TRANSPORT）")
    private String transportMode;

    @Option(names = {"-p", "--port"},
            description = "SSE 模式 HTTP 端口（默认: 8080，或环境变量 MCP_PORT）")
    private Integer port;

    @Option(names = {"--host"},
            description = "SSE 模式监听地址（默认: localhost，设 0.0.0.0 允许外网访问，或环境变量 MCP_HOST）")
    private String host;

    @Option(names = {"-k", "--api-key"},
            description = "SSE 认证密钥（可选，或环境变量 MCP_API_KEY）")
    private String apiKey;

    @Option(names = {"--file-root"},
            description = "文件沙箱根目录，限制文件操作范围（可选，或环境变量 MCP_FILE_ROOT）")
    private String fileRoot;

    @Override
    public Integer call() {
        // 加载配置（环境变量 + CLI 参数覆盖）
        var config = new McpServerConfig();

        if (serverName != null) config.setServerName(serverName);
        if (transportMode != null) config.setTransportMode(transportMode);
        if (port != null) config.setPort(port);
        if (host != null) config.setHost(host);
        if (apiKey != null) config.setApiKey(apiKey);
        if (fileRoot != null) config.setFileRoot(fileRoot);

        // 创建传输层
        Transport transport = config.createTransport();

        // 文件沙箱配置写入系统属性（DemoTools 中的 FileSandbox 会读取）
        if (config.getFileRoot() != null && !config.getFileRoot().isBlank()) {
            System.setProperty("mcp.file.root", config.getFileRoot());
            System.out.println("📁 File operations restricted to: " + config.getFileRoot());
        }

        // 启动服务器
        McpServer.create(config.getServerName(), config.getServerVersion())
                .withTransport(transport)
                .registerTools(new DemoTools())
                .build()
                .start();

        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new McpServerCli()).execute(args);
        System.exit(exitCode);
    }
}
