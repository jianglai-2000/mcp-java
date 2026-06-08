package io.mcp.server.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * A JSON-RPC 2.0 Error Response.
 * <p>
 * Returned when the server encounters an error processing a request.
 */
public class JsonRpcErrorResponse extends JsonRpcMessage {

    private final long id;
    private final RpcError error;

    @JsonCreator
    public JsonRpcErrorResponse(
            @JsonProperty("id") long id,
            @JsonProperty("error") RpcError error) {
        this.id = id;
        this.error = Objects.requireNonNull(error, "error must not be null");
    }

    public long getId() {
        return id;
    }

    public RpcError getError() {
        return error;
    }

    public static JsonRpcErrorResponse of(long id, int code, String message) {
        return new JsonRpcErrorResponse(id, new RpcError(code, message));
    }

    public static JsonRpcErrorResponse methodNotFound(long id, String method) {
        return of(id, -32601, "Method not found: " + method);
    }

    public static JsonRpcErrorResponse internalError(long id, String message) {
        return of(id, -32603, message);
    }

    /** JSON-RPC error object. */
    public static class RpcError {
        private final int code;
        private final String message;

        @JsonCreator
        public RpcError(@JsonProperty("code") int code,
                        @JsonProperty("message") String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() { return code; }
        public String getMessage() { return message; }
    }
}
