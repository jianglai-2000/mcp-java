package io.mcp.server;

import io.mcp.server.annotation.McpToolProvider;
import io.mcp.server.protocol.*;
import io.mcp.server.registry.*;
import io.mcp.server.transport.StdioTransport;
import io.mcp.server.transport.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * Main MCP Server class.
 * <p>
 * Orchestrates the transport, protocol handler, and registries for
 * tools, resources, and prompts.
 */
public class McpServer {

    private static final Logger log = LoggerFactory.getLogger(McpServer.class);

    private final Transport transport;
    private final McpProtocolHandler protocolHandler;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    McpServer(Transport transport, McpProtocolHandler protocolHandler) {
        this.transport = transport;
        this.protocolHandler = protocolHandler;
    }

    public void start() {
        log.info("Starting MCP server...");
        transport.start(this::handleMessage, error -> {
            log.error("Transport error", error);
            shutdownLatch.countDown();
        });
        log.info("MCP server started");
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stop();
        }
    }

    public void stop() {
        log.info("Stopping MCP server...");
        transport.close();
        shutdownLatch.countDown();
        log.info("MCP server stopped");
    }

    /** @deprecated Use {@link #stop()} */
    @Deprecated
    public void shutdown() { stop(); }

    private void handleMessage(String line) {
        try {
            var parsed = JsonRpcMessage.fromJson(line);

            if (parsed instanceof JsonRpcRequest request) {
                JsonRpcMessage response = protocolHandler.handle(request);
                if (response != null) {
                    transport.send(response.toJson());
                }
            } else if (parsed instanceof JsonRpcNotification notification) {
                protocolHandler.handleNotification(notification);
            } else if (parsed instanceof JsonRpcResponse) {
                log.warn("Unexpected response message: {}", line);
            }
        } catch (Exception e) {
            log.error("Error handling message: {}", line, e);
        }
    }

    // ---- Builder ----

    public static Builder create(String name, String version) {
        return new Builder(name, version);
    }

    public static class Builder {
        private final String name;
        private final String version;
        private final ToolRegistry toolRegistry = new ToolRegistry();
        private final ResourceRegistry resourceRegistry = new ResourceRegistry();
        private final PromptRegistry promptRegistry = new PromptRegistry();
        private Transport transport;

        Builder(String name, String version) {
            this.name = name;
            this.version = version;
        }

        public Builder withTransport(Transport transport) {
            this.transport = transport;
            return this;
        }

        public Builder registerTools(Object provider) {
            new AnnotationScanner(toolRegistry, resourceRegistry, promptRegistry).scan(provider);
            return this;
        }

        public Builder registerResources(Object provider) {
            new AnnotationScanner(toolRegistry, resourceRegistry, promptRegistry).scan(provider);
            return this;
        }

        public Builder registerPrompts(Object provider) {
            new AnnotationScanner(toolRegistry, resourceRegistry, promptRegistry).scan(provider);
            return this;
        }

        public Builder registerResource(ResourceDefinition resource) {
            resourceRegistry.register(resource);
            return this;
        }

        public Builder registerPrompt(PromptDefinition prompt) {
            promptRegistry.register(prompt);
            return this;
        }

        public McpServer build() {
            var handler = new McpProtocolHandler(toolRegistry, resourceRegistry, promptRegistry, name, version);
            Transport t = transport != null ? transport : new StdioTransport();
            return new McpServer(t, handler);
        }

        public ToolRegistry toolRegistry() { return toolRegistry; }
        public ResourceRegistry resourceRegistry() { return resourceRegistry; }
        public PromptRegistry promptRegistry() { return promptRegistry; }
    }
}
