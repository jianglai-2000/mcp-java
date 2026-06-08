package io.mcp.server;

import io.mcp.server.transport.SseTransport;
import io.mcp.server.transport.StdioTransport;
import io.mcp.server.transport.Transport;

/**
 * Server configuration, loaded from CLI args or environment variables.
 * <p>
 * Environment variables take precedence over defaults:
 * <ul>
 *   <li>{@code MCP_TRANSPORT} — transport mode (stdio, sse)</li>
 *   <li>{@code MCP_PORT} — HTTP port for SSE mode</li>
 *   <li>{@code MCP_HOST} — host address for SSE mode</li>
 *   <li>{@code MCP_API_KEY} — API key for SSE authentication</li>
 *   <li>{@code MCP_FILE_ROOT} — restricted root directory for file operations</li>
 *   <li>{@code MCP_SERVER_NAME} — server name</li>
 * </ul>
 */
public class McpServerConfig {

    private String transportMode;
    private int port;
    private String host;
    private String apiKey;
    private String fileRoot;
    private String serverName;
    private String serverVersion;

    public McpServerConfig() {
        // Defaults
        this.transportMode = env("MCP_TRANSPORT", "stdio");
        this.port = intEnv("MCP_PORT", 8080);
        this.host = env("MCP_HOST", "localhost");
        this.apiKey = env("MCP_API_KEY", null);
        this.fileRoot = env("MCP_FILE_ROOT", null);
        this.serverName = env("MCP_SERVER_NAME", "mcp-java");
        this.serverVersion = "0.1.0";
    }

    public Transport createTransport() {
        return switch (transportMode.toLowerCase()) {
            case "sse", "http" -> {
                var sse = new SseTransport(host, port);
                if (apiKey != null && !apiKey.isBlank()) {
                    sse.setApiKey(apiKey);
                }
                System.out.println("MCP server starting: " + serverName + " v" + serverVersion);
                System.out.println("Transport: SSE/HTTP on http://" + host + ":" + port + "/sse");
                if (apiKey != null) {
                    System.out.println("Authentication: API key required");
                }
                yield sse;
            }
            case "stdio" -> new StdioTransport();
            default -> throw new IllegalArgumentException(
                    "Unknown transport: " + transportMode + " (supported: stdio, sse)");
        };
    }

    // ---- Getters / Setters for CLI ----

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

    // ---- Helpers ----

    private static String env(String key, String defaultValue) {
        String val = System.getenv(key);
        return val != null ? val : defaultValue;
    }

    private static int intEnv(String key, int defaultValue) {
        String val = System.getenv(key);
        if (val != null) {
            try { return Integer.parseInt(val); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}
