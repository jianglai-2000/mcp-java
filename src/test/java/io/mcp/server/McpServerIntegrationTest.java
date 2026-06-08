package io.mcp.server;

import io.mcp.server.demo.DemoTools;
import io.mcp.server.transport.SseTransport;
import io.mcp.server.transport.StdioTransport;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test simulating MCP client-server conversations
 * over stdio and SSE transports.
 */
class McpServerIntegrationTest {

    // ======================== Stdio Transport ========================

    @Test
    void stdioShouldHandleFullMCPHandshake() throws Exception {
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
    void stdioShouldHandleToolCallWithArguments() throws Exception {
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
    void stdioShouldReportErrorForUnknownTool() throws Exception {
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

    // ======================== SSE Transport ========================

    @Test
    void sseShouldStartAndAcceptMessages() throws Exception {
        var transport = new SseTransport(0); // port 0 = auto-assign
        var registry = new io.mcp.server.registry.ToolRegistry();
        new io.mcp.server.registry.AnnotationScanner(registry).scan(new DemoTools());
        var handler = new io.mcp.server.protocol.McpProtocolHandler(registry, "test", "1.0");
        var server = new McpServer(transport, handler);

        var thread = new Thread(() -> server.start());
        thread.start();
        Thread.sleep(500);

        int port = transport.getPort();
        assertTrue(port > 0, "Server should be listening on a port");

        var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        var body = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                """.strip();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/message"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(202, response.statusCode(), "Should accept message");

        server.shutdown();
        thread.join(3000);
    }

    @Test
    void sseShouldAcceptToolsList() throws Exception {
        var transport = new SseTransport(0);
        var registry = new io.mcp.server.registry.ToolRegistry();
        new io.mcp.server.registry.AnnotationScanner(registry).scan(new DemoTools());
        var handler = new io.mcp.server.protocol.McpProtocolHandler(registry, "test", "1.0");
        var server = new McpServer(transport, handler);

        var thread = new Thread(() -> server.start());
        thread.start();
        Thread.sleep(500);

        int port = transport.getPort();

        var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        var body = """
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """.strip();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/message"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(202, response.statusCode(), "Should accept tools/list message");

        server.shutdown();
        thread.join(3000);
    }

    @Test
    void sseShouldReturn404ForUnknownEndpoint() throws Exception {
        var transport = new SseTransport(0);
        var registry = new io.mcp.server.registry.ToolRegistry();
        new io.mcp.server.registry.AnnotationScanner(registry).scan(new DemoTools());
        var handler = new io.mcp.server.protocol.McpProtocolHandler(registry, "test", "1.0");
        var server = new McpServer(transport, handler);

        var thread = new Thread(() -> server.start());
        thread.start();
        Thread.sleep(500);

        int port = transport.getPort();

        var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // Try a GET request to a non-existent endpoint
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/unknown"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode(), "Should return 404 for unknown endpoint");

        server.shutdown();
        thread.join(3000);
    }
}
