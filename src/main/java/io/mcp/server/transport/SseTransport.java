package io.mcp.server.transport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * SSE (Server-Sent Events) transport for MCP over HTTP.
 * <p>
 * Two endpoints:
 * <ul>
 *   <li><b>GET /sse</b> — SSE stream, server pushes events to client</li>
 *   <li><b>POST /message</b> — client sends JSON-RPC messages here</li>
 * </ul>
 * <p>
 * Uses Java built-in {@link HttpServer} — zero extra dependencies.
 */
public class SseTransport implements Transport {

    private static final Logger log = LoggerFactory.getLogger(SseTransport.class);

    private static final AtomicLong SESSION_COUNTER = new AtomicLong(0);

    private final int port;
    private final String host;

    private HttpServer server;
    private SseConnection sseConnection;
    private Consumer<String> onMessage;
    private Consumer<Throwable> onError;
    private volatile boolean running = false;
    private volatile String apiKey;

    public SseTransport(int port) {
        this.port = port;
        this.host = "localhost";
    }

    public SseTransport(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** Set an API key for authentication. If set, all requests must include it. */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void start(Consumer<String> onMessage, Consumer<Throwable> onError) {
        this.onMessage = onMessage;
        this.onError = onError;

        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);

            server.createContext("/sse", this::handleSse);
            server.createContext("/message", this::handleMessage);

            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            running = true;

            log.info("SSE transport listening on http://{}:{}/sse", host, server.getAddress().getPort());
        } catch (IOException e) {
            throw new RuntimeException("Failed to start SSE transport on port " + port, e);
        }
    }

    // ---- SSE endpoint ----

    private void handleSse(HttpExchange exchange) {
        try {
            // Check authentication
            if (!checkAuth(exchange)) return;
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.getResponseHeaders().add("Connection", "keep-alive");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, 0);

            var writer = new OutputStreamWriter(exchange.getResponseBody(), StandardCharsets.UTF_8);
            String sessionId = "sse-" + SESSION_COUNTER.incrementAndGet();

            // Tell client where to POST messages
            String messageEndpoint = "/message?sessionId=" + sessionId;
            writeSseEvent(writer, "endpoint", messageEndpoint);
            writer.flush();

            sseConnection = new SseConnection(sessionId, writer);

            log.info("SSE client connected: {}", sessionId);

            // Keep connection alive — block until client disconnects
            try {
                // Read the InputStream until it closes (client disconnect)
                var input = exchange.getRequestBody();
                byte[] buf = new byte[4096];
                while (input.read(buf) != -1) {
                    // discard — we don't read from SSE stream
                }
            } catch (IOException ignored) {
                // client disconnected
            } finally {
                log.info("SSE client disconnected: {}", sessionId);
                sseConnection = null;
                closeQuietly(exchange);
            }
        } catch (Exception e) {
            log.error("SSE connection error", e);
        }
    }

    // ---- Message endpoint ----

    private void handleMessage(HttpExchange exchange) {
        try {
            // CORS headers
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

            // Handle CORS preflight
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Check authentication
            if (!checkAuth(exchange)) return;

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            // Read JSON-RPC message from request body
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            log.debug("Received via HTTP POST: {}", body);

            // Process message through the registered callback
            if (onMessage != null) {
                try {
                    onMessage.accept(body);
                    // The response will be sent back via SSE by the protocol handler
                    sendJsonResponse(exchange, 202, "{\"status\":\"accepted\"}");
                } catch (Exception e) {
                    log.error("Error processing message", e);
                    sendJsonResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
                }
            } else {
                sendJsonResponse(exchange, 503, "{\"error\":\"Server not ready\"}");
            }
        } catch (Exception e) {
            log.error("Error handling message", e);
            try {
                sendJsonResponse(exchange, 500, "{\"error\":\"Internal server error\"}");
            } catch (IOException ignored) {
            }
        }
    }

    // ---- Transport interface ----

    @Override
    public void send(String message) {
        var conn = sseConnection;
        if (conn != null) {
            conn.sendEvent("message", message);
        } else {
            log.warn("No SSE client connected, dropping message: {}", message);
        }
    }

    @Override
    public boolean isConnected() {
        return running && sseConnection != null;
    }

    @Override
    public void close() {
        running = false;
        if (server != null) {
            server.stop(1); // 1 second grace period
            log.info("SSE transport stopped");
        }
    }

    public int getPort() {
        return server != null ? server.getAddress().getPort() : port;
    }

    // ---- Helpers ----

    private void writeSseEvent(Writer writer, String event, String data) throws IOException {
        writer.write("event: " + event + "\n");
        writer.write("data: " + data + "\n\n");
        writer.flush();
    }

    private void sendJsonResponse(HttpExchange exchange, int code, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().flush();
        exchange.close();
    }

    private void closeQuietly(HttpExchange exchange) {
        try {
            exchange.close();
        } catch (Exception ignored) {
        }
    }

    // ---- Authentication ----

    private boolean checkAuth(HttpExchange exchange) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            return true; // auth not configured, allow all
        }
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.equalsIgnoreCase("Bearer " + apiKey)) {
            return true;
        }
        // Also check query param for SSE connections
        String query = exchange.getRequestURI().getQuery();
        if (query != null && query.contains("token=" + apiKey)) {
            return true;
        }
        sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized: valid API key required\"}");
        return false;
    }

    // ---- SSE connection wrapper ----

    private static class SseConnection {
        private final String sessionId;
        private final Writer writer;

        SseConnection(String sessionId, Writer writer) {
            this.sessionId = sessionId;
            this.writer = writer;
        }

        void sendEvent(String event, String data) {
            try {
                writer.write("event: " + event + "\n");
                writer.write("data: " + data + "\n\n");
                writer.flush();
            } catch (IOException e) {
                // client probably disconnected
            }
        }
    }
}
