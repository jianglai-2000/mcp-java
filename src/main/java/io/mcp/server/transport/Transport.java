package io.mcp.server.transport;

import java.util.function.Consumer;

/**
 * Abstract transport layer for MCP communication.
 * <p>
 * MCP supports multiple transport modes. The initial implementation
 * supports {@link StdioTransport} (stdin/stdout), with SSE to follow.
 */
public interface Transport extends AutoCloseable {

    /** Start the transport and begin reading messages. */
    void start(Consumer<String> onMessage, Consumer<Throwable> onError);

    /** Send a raw JSON-RPC message string. */
    void send(String message);

    /** Check if the transport is connected and operational. */
    boolean isConnected();

    /** Close the transport and release resources. */
    @Override
    void close();
}
