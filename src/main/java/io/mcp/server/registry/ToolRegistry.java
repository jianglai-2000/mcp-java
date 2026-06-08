package io.mcp.server.registry;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of all MCP tools exposed by the server.
 * <p>
 * Thread-safe. Tools can be registered programmatically or discovered
 * via annotation scanning.
 */
public class ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    /**
     * Register a tool with the given definition.
     *
     * @param tool the tool definition
     * @throws IllegalArgumentException if a tool with the same name already exists
     */
    public void register(ToolDefinition tool) {
        Objects.requireNonNull(tool, "tool must not be null");
        if (tools.putIfAbsent(tool.name(), tool) != null) {
            throw new IllegalArgumentException("Tool already registered: " + tool.name());
        }
    }

    /**
     * Get a tool by name.
     *
     * @return the tool definition, or empty if not found
     */
    public Optional<ToolDefinition> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * List all registered tools.
     */
    public List<ToolDefinition> listTools() {
        return List.copyOf(tools.values());
    }

    /**
     * Call a tool by name with the given arguments.
     *
     * @param name      the tool name
     * @param arguments the JSON arguments
     * @return the tool's result
     * @throws IllegalArgumentException if the tool is not found
     */
    public Object callTool(String name, JsonNode arguments) throws Exception {
        ToolDefinition tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        return tool.invoker().invoke(arguments);
    }

    /**
     * Remove a tool from the registry.
     */
    public void unregister(String name) {
        tools.remove(name);
    }

    /**
     * Get the number of registered tools.
     */
    public int size() {
        return tools.size();
    }
}
