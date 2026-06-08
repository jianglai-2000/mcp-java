package io.mcp.server.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * A successful JSON-RPC 2.0 Response.
 */
public class JsonRpcResponse extends JsonRpcMessage {

    private final long id;
    private final JsonNode result;

    @JsonCreator
    public JsonRpcResponse(
            @JsonProperty("id") long id,
            @JsonProperty("result") JsonNode result) {
        this.id = id;
        this.result = Objects.requireNonNull(result, "result must not be null");
    }

    public long getId() {
        return id;
    }

    public JsonNode getResult() {
        return result;
    }

    /** Create a response with a plain Java object as result (auto-serialized). */
    public static JsonRpcResponse of(long id, Object result) {
        JsonNode node = JsonRpcMessage.mapper().valueToTree(result);
        return new JsonRpcResponse(id, node);
    }
}
