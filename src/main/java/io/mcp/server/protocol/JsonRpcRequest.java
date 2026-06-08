package io.mcp.server.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * A JSON-RPC 2.0 Request object.
 * <p>
 * Requests have an {@code id} for response correlation, a {@code method} name,
 * and optional {@code params}.
 */
public class JsonRpcRequest extends JsonRpcMessage {

    private final long id;
    private final String method;
    private final JsonNode params;

    @JsonCreator
    public JsonRpcRequest(
            @JsonProperty("id") long id,
            @JsonProperty("method") String method,
            @JsonProperty("params") JsonNode params) {
        this.id = id;
        this.method = Objects.requireNonNull(method, "method must not be null");
        this.params = params;
    }

    public long getId() {
        return id;
    }

    public String getMethod() {
        return method;
    }

    public JsonNode getParams() {
        return params;
    }
}
