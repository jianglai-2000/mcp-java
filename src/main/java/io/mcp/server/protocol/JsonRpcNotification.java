package io.mcp.server.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * A JSON-RPC 2.0 Notification (a Request without an {@code id}).
 * <p>
 * Notifications do not expect a response. MCP uses notifications for
 * events like {@code initialized} and {@code notifications/...}.
 */
public class JsonRpcNotification extends JsonRpcMessage {

    private final String method;
    private final JsonNode params;

    @JsonCreator
    public JsonRpcNotification(
            @JsonProperty("method") String method,
            @JsonProperty("params") JsonNode params) {
        this.method = Objects.requireNonNull(method, "method must not be null");
        this.params = params;
    }

    public String getMethod() {
        return method;
    }

    public JsonNode getParams() {
        return params;
    }
}
