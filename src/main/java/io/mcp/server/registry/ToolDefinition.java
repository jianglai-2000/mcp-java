package io.mcp.server.registry;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Immutable definition of a registered MCP tool.
 * <p>
 * Each tool has a name, description, and a JSON Schema defining its
 * expected input parameters.
 */
public record ToolDefinition(
        String name,
        String description,
        JsonNode inputSchema,
        ToolInvoker invoker) {

    /** Functional interface for invoking the tool with parsed arguments. */
    @FunctionalInterface
    public interface ToolInvoker {
        Object invoke(JsonNode arguments) throws Exception;
    }
}
