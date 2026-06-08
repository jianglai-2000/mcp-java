package io.mcp.server.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Transport implementation using standard input/output streams.
 * <p>
 * This is the primary transport for MCP servers launched as subprocesses
 * by AI clients (e.g., Claude Desktop, Cursor).
 * <p>
 * Messages are newline-delimited JSON (one JSON-RPC message per line) over stdin/stdout.
 * Stderr is reserved for logging and diagnostics.
 */
public class StdioTransport implements Transport {

    private static final Logger log = LoggerFactory.getLogger(StdioTransport.class);

    private final BufferedReader reader;
    private final PrintWriter writer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread readThread;

    public StdioTransport() {
        this.reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        this.writer = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
    }

    /** Create a transport with custom input/output streams (useful for testing). */
    public StdioTransport(InputStream in, OutputStream out) {
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
    }

    @Override
    public void start(Consumer<String> onMessage, Consumer<Throwable> onError) {
        if (running.compareAndSet(false, true)) {
            readThread = Thread.ofVirtual()
                    .name("mcp-stdio-reader")
                    .start(() -> {
                        try {
                            String line;
                            while (running.get() && (line = reader.readLine()) != null) {
                                String trimmed = line.trim();
                                if (trimmed.isEmpty()) continue;
                                log.debug(">>> Received: {}", trimmed);
                                onMessage.accept(trimmed);
                            }
                            // stdin closed (EOF), graceful shutdown
                            if (running.get()) {
                                log.info("stdin closed, shutting down");
                                onError.accept(new RuntimeException("stdin closed"));
                            }
                        } catch (IOException e) {
                            if (running.get()) {
                                onError.accept(e);
                            }
                        } finally {
                            running.set(false);
                        }
                    });
        }
    }

    @Override
    public void send(String message) {
        log.debug("<<< Sending: {}", message);
        writer.println(message);
        writer.flush();
    }

    @Override
    public boolean isConnected() {
        return running.get();
    }

    @Override
    public void close() {
        running.set(false);
        if (readThread != null) {
            try {
                readThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            reader.close();
        } catch (IOException ignored) {
        }
        writer.close();
    }
}
