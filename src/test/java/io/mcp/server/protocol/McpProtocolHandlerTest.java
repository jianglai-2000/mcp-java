package io.mcp.server.protocol;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.mcp.server.registry.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class McpProtocolHandlerTest {

    private ToolRegistry registry;
    private McpProtocolHandler handler;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        handler = new McpProtocolHandler(registry, "test-server", "1.0.0");
    }

    @Test
    void shouldHandleInitialize() {
        var params = JsonRpcMessage.mapper().createObjectNode();
        var request = new JsonRpcRequest(1, "initialize", params);

        JsonRpcMessage response = handler.handle(request);
        assertInstanceOf(JsonRpcResponse.class, response);
        String json = response.toJson();
        assertTrue(json.contains("\"protocolVersion\""));
        assertTrue(json.contains("\"serverInfo\""));
        assertTrue(json.contains("\"capabilities\""));
    }

    @Test
    void shouldReturnEmptyToolsList() {
        var request = new JsonRpcRequest(1, "tools/list", JsonRpcMessage.mapper().createObjectNode());

        JsonRpcMessage response = handler.handle(request);
        assertInstanceOf(JsonRpcResponse.class, response);
        assertTrue(response.toJson().contains("\"tools\":[]"));
    }

    @Test
    void shouldReturnMethodNotFound() {
        var request = new JsonRpcRequest(1, "unknown_method", null);

        JsonRpcMessage response = handler.handle(request);
        assertInstanceOf(JsonRpcErrorResponse.class, response);
        assertEquals(-32601, ((JsonRpcErrorResponse) response).getError().getCode());
    }

    @Test
    void shouldHandleInitializedNotification() {
        assertFalse(handler.isInitialized());

        var notification = new JsonRpcNotification("notifications/initialized", null);
        handler.handleNotification(notification);

        assertTrue(handler.isInitialized());
    }
}
