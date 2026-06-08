package io.mcp.server;

import io.mcp.server.transport.SseTransport;
import io.mcp.server.transport.StdioTransport;
import io.mcp.server.transport.Transport;

/**
 * 服务器配置，支持 CLI 参数和环境变量两种方式。
 *
 * 环境变量优先级低于 CLI 参数，高于默认值。
 * 通过环境变量配置，适合 Docker 部署和 CI/CD 场景。
 *
 * <pre>
 * 环境变量速查：
 *
 * MCP_TRANSPORT=stdio      # 传输模式：stdio | sse
 * MCP_PORT=8080            # SSE 模式下 HTTP 端口
 * MCP_HOST=localhost       # SSE 模式下监听地址（0.0.0.0 允许外部访问）
 * MCP_API_KEY=xxx          # SSE 认证密钥（可选，设置后需要 Bearer token）
 * MCP_FILE_ROOT=/data      # 文件操作沙箱根目录（可选，设置后限制文件访问范围）
 * MCP_SERVER_NAME=mcp-java # 服务器名称
 * </pre>
 */
public class McpServerConfig {

    // ======================== 字段定义 ========================

    /** 传输模式：stdio（默认）或 sse */
    private String transportMode;

    /** SSE/HTTP 端口（默认 8080） */
    private int port;

    /** SSE 监听地址（默认 localhost，设 0.0.0.0 开放外网） */
    private String host;

    /** SSE 认证密钥（可选，不设置则无认证） */
    private String apiKey;

    /** 文件沙箱根目录（可选，不设置则不限制文件操作路径） */
    private String fileRoot;

    /** 服务器名称 */
    private String serverName;

    /** 服务器版本 */
    private final String serverVersion;

    // ======================== 初始化 ========================

    public McpServerConfig() {
        // 从环境变量加载配置，CLI 参数后续覆盖
        this.transportMode = env("MCP_TRANSPORT", "stdio");
        this.port = intEnv("MCP_PORT", 8080);
        this.host = env("MCP_HOST", "localhost");
        this.apiKey = env("MCP_API_KEY", null);
        this.fileRoot = env("MCP_FILE_ROOT", null);
        this.serverName = env("MCP_SERVER_NAME", "mcp-java");
        this.serverVersion = "0.1.0";
    }

    // ======================== 创建传输层 ========================

    /**
     * 根据当前配置创建对应的 Transport 实例。
     * 启动时会打印配置摘要到控制台。
     */
    public Transport createTransport() {
        return switch (transportMode.toLowerCase()) {
            case "sse", "http" -> {
                var sse = new SseTransport(host, port);
                if (apiKey != null && !apiKey.isBlank()) {
                    sse.setApiKey(apiKey);
                }
                System.out.println("☕ MCP server: " + serverName + " v" + serverVersion);
                System.out.println("🔌 Transport: " + transportMode + " on http://" + host + ":" + port + "/sse");
                if (apiKey != null) {
                    System.out.println("🔑 Auth: API key required");
                }
                yield sse;
            }
            case "stdio" -> {
                System.out.println("☕ MCP server: " + serverName + " v" + serverVersion);
                System.out.println("🔌 Transport: stdio (pipe to AI client)");
                yield new StdioTransport();
            }
            default -> throw new IllegalArgumentException(
                    "Unknown transport: " + transportMode + " (supported: stdio, sse)");
        };
    }

    // ======================== Getter / Setter ========================

    public String getTransportMode() { return transportMode; }
    public void setTransportMode(String transportMode) { this.transportMode = transportMode; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getFileRoot() { return fileRoot; }
    public void setFileRoot(String fileRoot) { this.fileRoot = fileRoot; }

    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }

    public String getServerVersion() { return serverVersion; }

    // ======================== 工具方法 ========================

    /** 读取环境变量，不存在则返回默认值 */
    private static String env(String key, String defaultValue) {
        String val = System.getenv(key);
        return val != null ? val : defaultValue;
    }

    /** 读取整数类型环境变量，解析失败则返回默认值 */
    private static int intEnv(String key, int defaultValue) {
        String val = System.getenv(key);
        if (val != null) {
            try { return Integer.parseInt(val); } catch (NumberFormatException ignored) {
                System.err.println("⚠ Warning: invalid " + key + "='" + val + "', using default " + defaultValue);
            }
        }
        return defaultValue;
    }
}
