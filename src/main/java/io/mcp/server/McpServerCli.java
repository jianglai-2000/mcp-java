package io.mcp.server;

import io.mcp.server.demo.DemoTools;
import io.mcp.server.transport.Transport;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * CLI entry point for running an MCP server.
 * <p>
 * Usage:
 * <pre>
 * # stdio mode (default, for Claude Desktop subprocess)
 * java -jar mcp-java.jar
 *
 * # SSE/HTTP mode (standalone server)
 * java -jar mcp-java.jar --transport sse --port 8080
 *
 * # SSE with API key authentication
 * java -jar mcp-java.jar --transport sse --port 8080 --api-key my-secret-key
 * </pre>
 */
@Command(
        name = "mcp-java",
        mixinStandardHelpOptions = true,
        version = "mcp-java 0.1.0",
        description = "A lightweight MCP (Model Context Protocol) server for Java"
)
public class McpServerCli implements Callable<Integer> {

    @Option(names = {"-n", "--name"},
            description = "Server name (default: mcp-java, or $MCP_SERVER_NAME)")
    private String serverName;

    @Option(names = {"-t", "--transport"},
            description = "Transport mode: stdio (default) or sse (or $MCP_TRANSPORT)")
    private String transportMode;

    @Option(names = {"-p", "--port"},
            description = "HTTP port for SSE transport (default: 8080, or $MCP_PORT)")
    private Integer port;

    @Option(names = {"--host"},
            description = "Host address for SSE transport (default: localhost, or $MCP_HOST)")
    private String host;

    @Option(names = {"-k", "--api-key"},
            description = "API key for SSE authentication (or $MCP_API_KEY)")
    private String apiKey;

    @Option(names = {"--file-root"},
            description = "Restricted root directory for file operations (or $MCP_FILE_ROOT)")
    private String fileRoot;

    @Override
    public Integer call() {
        // Load config from CLI args + env vars
        var config = new McpServerConfig();

        if (serverName != null) config.setServerName(serverName);
        if (transportMode != null) config.setTransportMode(transportMode);
        if (port != null) config.setPort(port);
        if (host != null) config.setHost(host);
        if (apiKey != null) config.setApiKey(apiKey);
        if (fileRoot != null) config.setFileRoot(fileRoot);

        Transport transport = config.createTransport();

        var builder = McpServer.create(config.getServerName(), config.getServerVersion())
                .withTransport(transport);

        // Apply file sandbox if configured
        if (config.getFileRoot() != null && !config.getFileRoot().isBlank()) {
            System.out.println("File operations restricted to: " + config.getFileRoot());
            // File sandbox is applied via system property for now
            System.setProperty("mcp.file.root", config.getFileRoot());
        }

        builder.registerTools(new DemoTools())
                .build()
                .start();

        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new McpServerCli()).execute(args);
        System.exit(exitCode);
    }
}
