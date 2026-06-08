package io.mcp.server;

import io.mcp.server.demo.DemoTools;
import io.mcp.server.transport.SseTransport;
import io.mcp.server.transport.StdioTransport;
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
            description = "Server name",
            defaultValue = "mcp-java")
    private String serverName;

    @Option(names = {"-v", "--server-version"},
            description = "Server version",
            defaultValue = "0.1.0")
    private String serverVersion;

    @Option(names = {"-t", "--transport"},
            description = "Transport mode: stdio (default) or sse",
            defaultValue = "stdio")
    private String transportMode;

    @Option(names = {"-p", "--port"},
            description = "HTTP port for SSE transport (default: 8080)")
    private int port = 8080;

    @Option(names = {"--host"},
            description = "Host address for SSE transport (default: localhost)")
    private String host = "localhost";

    @Override
    public Integer call() {
        Transport transport = createTransport();

        McpServer server = McpServer.create(serverName, serverVersion)
                .withTransport(transport)
                .registerTools(new DemoTools())
                .build();

        server.start();
        return 0;
    }

    private Transport createTransport() {
        return switch (transportMode.toLowerCase()) {
            case "sse", "http" -> {
                var sse = new SseTransport(host, port);
                System.out.println("Starting MCP server with SSE transport on http://" + host + ":" + port + "/sse");
                yield sse;
            }
            case "stdio" -> new StdioTransport();
            default -> throw new IllegalArgumentException(
                    "Unknown transport: " + transportMode + " (supported: stdio, sse)");
        };
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new McpServerCli()).execute(args);
        System.exit(exitCode);
    }
}
