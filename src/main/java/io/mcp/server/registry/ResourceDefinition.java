package io.mcp.server.registry;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Immutable definition of a registered MCP resource.
 * <p>
 * Resources are static or dynamic data sources exposed by the server,
 * identified by URI and typed by MIME content type.
 *
 * @param uri          The URI identifying this resource (e.g. "file:///logs/app.log")
 * @param name         A human-readable name
 * @param description  Optional description
 * @param mimeType     MIME type (e.g. "text/plain", "application/json")
 * @param reader       The function that reads the resource content
 */
public record ResourceDefinition(
        String uri,
        String name,
        String description,
        String mimeType,
        ResourceReader reader) {

    @FunctionalInterface
    public interface ResourceReader {
        String read() throws Exception;
    }
}
