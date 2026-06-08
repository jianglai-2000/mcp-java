package io.mcp.server;

import io.mcp.server.demo.DemoTools;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * CLI entry point for running an MCP server.
 * <p>
 * Usage:
 * <pre>
 * java -jar mcp-java-server.jar
 * </pre>
 */
@Command(
        name = "mcp-java-server",
        mixinStandardHelpOptions = true,
        version = "mcp-java-server 0.1.0",
        description = "A lightweight Java MCP (Model Context Protocol) server"
)
public class McpServerCli implements Callable<Integer> {

    @Option(names = {"-n", "--name"},
            description = "Server name",
            defaultValue = "mcp-java-server")
    private String serverName;

    @Option(names = {"-v", "--server-version"},
            description = "Server version",
            defaultValue = "0.1.0")
    private String serverVersion;

    @Override
    public Integer call() {
        McpServer server = McpServer.create(serverName, serverVersion)
                .registerTools(new DemoTools())
                .build();

        server.start();
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new McpServerCli()).execute(args);
        System.exit(exitCode);
    }
}
