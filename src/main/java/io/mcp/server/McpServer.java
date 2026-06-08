package io.mcp.server;

import io.mcp.server.annotation.McpToolProvider;
import io.mcp.server.protocol.*;
import io.mcp.server.registry.AnnotationScanner;
import io.mcp.server.registry.ToolRegistry;
import io.mcp.server.transport.StdioTransport;
import io.mcp.server.transport.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * Main MCP Server class.
 * <p>
 * Orchestrates the transport, protocol handler, and tool registry.
 * Example usage:
 * <pre>{@code
 * McpServer server = McpServer.create("my-server", "1.0.0")
 *     .registerTools(new WeatherTools())
 *     .build();
 * server.start();
 * }</pre>
 */
public class McpServer {

    private static final Logger log = LoggerFactory.getLogger(McpServer.class);

    private final Transport transport;
    private final McpProtocolHandler handler;
    private volatile boolean running = false;

    McpServer(Transport transport, McpProtocolHandler handler) {
        this.transport = transport;
        this.handler = handler;
    }

    /**
     * Start the server. Blocks until shutdown.
     * <p>
     * Listens for incoming JSON-RPC messages over the configured transport,
     * dispatches them to the protocol handler, and sends responses back.
     */
    public void start() {
        if (running) return;
        running = true;

        log.info("MCP Server starting...");
        var latch = new CountDownLatch(1);

        transport.start(
                this::handleMessage,
                error -> {
                    log.error("Transport error", error);
                    shutdown();
                }
        );

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        try {
            latch.await(); // Keep alive until shutdown
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutdown();
        }
    }

    private void handleMessage(String rawMessage) {
        try {
            JsonRpcMessage msg = JsonRpcMessage.fromJson(rawMessage);

            if (msg instanceof JsonRpcRequest request) {
                JsonRpcMessage response = handler.handle(request);
                if (response != null) {
                    transport.send(response.toJson());
                }
            } else if (msg instanceof JsonRpcNotification notification) {
                handler.handleNotification(notification);
            }
        } catch (Exception e) {
            log.error("Error processing message: {}", rawMessage, e);
        }
    }

    /**
     * Gracefully shut down the server.
     */
    public void shutdown() {
        if (!running) return;
        running = false;
        log.info("MCP Server shutting down...");
        transport.close();
    }

    // ---- Builder ----

    /**
     * Create a new server builder.
     *
     * @param serverName    the server name (e.g., "file-system-server")
     * @param serverVersion the server version (e.g., "1.0.0")
     */
    public static Builder create(String serverName, String serverVersion) {
        return new Builder(serverName, serverVersion);
    }

    /**
     * Builder for {@link McpServer}.
     */
    public static class Builder {
        private final String serverName;
        private final String serverVersion;
        private final ToolRegistry registry = new ToolRegistry();
        private Transport transport;

        Builder(String serverName, String serverVersion) {
            this.serverName = serverName;
            this.serverVersion = serverVersion;
        }

        /**
         * Use a custom transport (default is {@link StdioTransport}).
         */
        public Builder withTransport(Transport transport) {
            this.transport = transport;
            return this;
        }

        /**
         * Register tool provider(s). Each object should have methods annotated
         * with {@link io.mcp.server.annotation.McpTool}.
         */
        public Builder registerTools(Object... providers) {
            var scanner = new AnnotationScanner(registry);
            for (Object provider : providers) {
                scanner.scan(provider);
            }
            return this;
        }

        /**
         * Access the tool registry directly for programmatic registration.
         */
        public ToolRegistry registry() {
            return registry;
        }

        /**
         * Build the server.
         */
        public McpServer build() {
            if (transport == null) {
                transport = new StdioTransport();
            }
            var handler = new McpProtocolHandler(registry, serverName, serverVersion);
            return new McpServer(transport, handler);
        }
    }
}
