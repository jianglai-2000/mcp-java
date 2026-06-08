package io.mcp.server.protocol;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonRpcMessageTest {

    @Test
    void shouldSerializeRequest() {
        var params = JsonRpcMessage.mapper().createObjectNode()
                .put("city", "Shanghai");
        var request = new JsonRpcRequest(1, "tools/call", params);

        String json = request.toJson();
        assertTrue(json.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(json.contains("\"id\":1"));
        assertTrue(json.contains("\"method\":\"tools/call\""));
        assertTrue(json.contains("\"city\":\"Shanghai\""));
    }

    @Test
    void shouldDeserializeRequest() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}";

        JsonRpcMessage msg = JsonRpcMessage.fromJson(json);
        assertInstanceOf(JsonRpcRequest.class, msg);
        JsonRpcRequest request = (JsonRpcRequest) msg;
        assertEquals(1, request.getId());
        assertEquals("tools/list", request.getMethod());
    }

    @Test
    void shouldDeserializeResponse() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}";

        JsonRpcMessage msg = JsonRpcMessage.fromJson(json);
        assertInstanceOf(JsonRpcResponse.class, msg);
    }

    @Test
    void shouldDeserializeNotification() {
        String json = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

        JsonRpcMessage msg = JsonRpcMessage.fromJson(json);
        assertInstanceOf(JsonRpcNotification.class, msg);
        assertEquals("notifications/initialized", ((JsonRpcNotification) msg).getMethod());
    }

    @Test
    void shouldDeserializeErrorResponse() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}";

        JsonRpcMessage msg = JsonRpcMessage.fromJson(json);
        assertInstanceOf(JsonRpcErrorResponse.class, msg);
        assertEquals(-32601, ((JsonRpcErrorResponse) msg).getError().getCode());
    }

    @Test
    void shouldCreateErrorResponse() {
        var error = JsonRpcErrorResponse.methodNotFound(1, "unknown_method");
        String json = error.toJson();
        assertTrue(json.contains("\"code\":-32601"));
    }

    @Test
    void shouldCreateResponseWithObject() {
        var response = JsonRpcResponse.of(1, new TestResult("hello", 42));
        String json = response.toJson();
        assertTrue(json.contains("\"text\":\"hello\""));
        assertTrue(json.contains("\"value\":42"));
    }

    record TestResult(String text, int value) {}
}
