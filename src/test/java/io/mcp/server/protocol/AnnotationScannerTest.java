package io.mcp.server.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import io.mcp.server.annotation.McpParam;
import io.mcp.server.annotation.McpTool;
import io.mcp.server.annotation.McpToolProvider;
import io.mcp.server.registry.AnnotationScanner;
import io.mcp.server.registry.ToolDefinition;
import io.mcp.server.registry.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationScannerTest {

    private ToolRegistry registry;
    private AnnotationScanner scanner;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        scanner = new AnnotationScanner(registry);
    }

    @Test
    void shouldScanAndRegisterTools() {
        scanner.scan(new TestTools());

        assertEquals(2, registry.size());
        assertTrue(registry.getTool("hello").isPresent());
        assertTrue(registry.getTool("add_numbers").isPresent());
    }

    @Test
    void shouldBuildInputSchema() {
        scanner.scan(new TestTools());

        ToolDefinition tool = registry.getTool("add_numbers").orElseThrow();
        JsonNode schema = tool.inputSchema();

        assertEquals("object", schema.get("type").asText());
        assertTrue(schema.has("properties"));
        assertTrue(schema.get("required").isArray());
        assertEquals(2, schema.get("required").size());
    }

    @Test
    void shouldInvokeTool() throws Exception {
        scanner.scan(new TestTools());

        var args = JsonRpcMessage.mapper().createObjectNode();
        args.put("name", "World");

        Object result = registry.callTool("hello", args);
        assertEquals("Hello, World!", result);
    }

    @McpToolProvider
    public static class TestTools {

        @McpTool(name = "hello", description = "Say hello")
        public String hello(@McpParam("name") String name) {
            return "Hello, " + name + "!";
        }

        @McpTool(name = "add_numbers", description = "Add two numbers")
        public int add(@McpParam("a") int a, @McpParam("b") int b) {
            return a + b;
        }
    }
}
