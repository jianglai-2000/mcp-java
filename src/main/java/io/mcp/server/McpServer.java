package io.mcp.server;

import io.mcp.server.annotation.McpToolProvider;
import io.mcp.server.protocol.*;
import io.mcp.server.registry.*;
import io.mcp.server.transport.StdioTransport;
import io.mcp.server.transport.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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
    private final String registryUrl;

    McpServer(Transport transport, McpProtocolHandler protocolHandler) {
        this(transport, protocolHandler, null);
    }

    McpServer(Transport transport, McpProtocolHandler protocolHandler, String registryUrl) {
        this.transport = transport;
        this.protocolHandler = protocolHandler;
        this.registryUrl = registryUrl;
    }

    public void start() {
        log.info("Starting MCP server...");
        // Health: log transport info
        if (transport instanceof io.mcp.server.transport.SseTransport sse) {
            log.info("Health endpoint: http://localhost:{}/health", sse.getPort());
        }
        transport.start(this::handleMessage, error -> {
            log.error("Transport error", error);
            shutdownLatch.countDown();
        });
        log.info("MCP server started");
        // Self-register if registry is configured
        registerWithRegistry();
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

    /**
     * Register this server with an mcpm registry (self-registration).
     * Called automatically at startup if {@link Builder#enableRegistry(String)} was set.
     */
    private void registerWithRegistry() {
        if (registryUrl == null || registryUrl.isBlank()) return;
        try {
            String manifest = """
                {"name":"mcp-java-server","description":"Java MCP Server","type":"jar","latestVersion":"0.1.0","authors":["mcp-java"],"license":"MIT"}
                """.stripIndent();

            var client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5)).build();
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(registryUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(manifest))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                log.info("Registered with registry: {}", registryUrl);
            } else {
                log.warn("Registry registration failed: HTTP {}", resp.statusCode());
            }
        } catch (Exception e) {
            log.warn("Failed to register with registry: {}", e.getMessage());
        }
    }

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
        private String registryUrl;

        Builder(String name, String version) {
            this.name = name;
            this.version = version;
        }

        /**
         * Enable automatic registration with an mcpm registry on startup.
         * The server will POST its manifest to the given URL.
         */
        public Builder enableRegistry(String url) {
            this.registryUrl = url;
            return this;
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
            return new McpServer(t, handler, registryUrl);
        }

        public ToolRegistry toolRegistry() { return toolRegistry; }
        public ResourceRegistry resourceRegistry() { return resourceRegistry; }
        public PromptRegistry promptRegistry() { return promptRegistry; }
    }
}
