package io.mcp.server;

import io.mcp.server.demo.DemoTools;
import io.mcp.server.transport.StdioTransport;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test simulating a real MCP client-server conversation
 * over stdin/stdout.
 */
class McpServerIntegrationTest {

    @Test
    void shouldHandleFullMCPHandshake() throws Exception {
        var input = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"uuid","arguments":{}}}
                """;

        var stdin = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        var stdout = new ByteArrayOutputStream();

        var transport = new StdioTransport(stdin, stdout);
        var registry = new io.mcp.server.registry.ToolRegistry();
        new io.mcp.server.registry.AnnotationScanner(registry).scan(new DemoTools());
        var handler = new io.mcp.server.protocol.McpProtocolHandler(registry, "test", "1.0");

        var server = new McpServer(transport, handler);

        var thread = new Thread(() -> server.start());
        thread.start();
        thread.join(5000);

        var output = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("\"protocolVersion\""), "Should respond to initialize");
        assertTrue(output.contains("\"tools\":"), "Should list tools");
        assertTrue(output.contains("\"content\""), "Should respond to tools/call");
    }

    @Test
    void shouldHandleToolCallWithArguments() throws Exception {
        var input = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"system_info","arguments":{}}}
                """;

        var stdin = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        var stdout = new ByteArrayOutputStream();

        var transport = new StdioTransport(stdin, stdout);
        var registry = new io.mcp.server.registry.ToolRegistry();
        new io.mcp.server.registry.AnnotationScanner(registry).scan(new DemoTools());
        var handler = new io.mcp.server.protocol.McpProtocolHandler(registry, "test", "1.0");

        var server = new McpServer(transport, handler);

        var thread = new Thread(() -> server.start());
        thread.start();
        thread.join(5000);

        var output = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("OS:"), "Should contain OS info");
        assertTrue(output.contains("Java:"), "Should contain Java info");
    }

    @Test
    void shouldReportErrorForUnknownTool() throws Exception {
        var input = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"non_existent_tool","arguments":{}}}
                """;

        var stdin = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        var stdout = new ByteArrayOutputStream();

        var transport = new StdioTransport(stdin, stdout);
        var registry = new io.mcp.server.registry.ToolRegistry();
        new io.mcp.server.registry.AnnotationScanner(registry).scan(new DemoTools());
        var handler = new io.mcp.server.protocol.McpProtocolHandler(registry, "test", "1.0");

        var server = new McpServer(transport, handler);

        var thread = new Thread(() -> server.start());
        thread.start();
        thread.join(5000);

        var output = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("\"code\":-32602"), "Should return error code for unknown tool");
    }
}
