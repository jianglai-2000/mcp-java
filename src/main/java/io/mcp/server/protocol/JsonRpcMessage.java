package io.mcp.server.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Objects;

/**
 * Base JSON-RPC 2.0 message representation.
 * <p>
 * MCP uses JSON-RPC 2.0 as its wire protocol. Every message is either
 * a Request, a Response (success), or an Error Response.
 */
public abstract class JsonRpcMessage {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @JsonProperty("jsonrpc")
    private final String jsonrpc = "2.0";

    public String getJsonrpc() {
        return jsonrpc;
    }

    /** Serialize this message to JSON string. */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize JSON-RPC message", e);
        }
    }

    /** Deserialize a JSON string into a JSON-RPC message. */
    public static JsonRpcMessage fromJson(String json) {
        try {
            var node = MAPPER.readTree(json);
            if (node.has("id") && node.has("method")) {
                return MAPPER.readValue(json, JsonRpcRequest.class);
            } else if (node.has("id") && node.has("result")) {
                return MAPPER.readValue(json, JsonRpcResponse.class);
            } else if (node.has("id") && node.has("error")) {
                return MAPPER.readValue(json, JsonRpcErrorResponse.class);
            } else if (!node.has("id") && node.has("method")) {
                return MAPPER.readValue(json, JsonRpcNotification.class);
            }
            throw new IllegalArgumentException("Unknown JSON-RPC message format: " + json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON-RPC message: " + json, e);
        }
    }

    /** Get the ObjectMapper instance for custom serialization. */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
