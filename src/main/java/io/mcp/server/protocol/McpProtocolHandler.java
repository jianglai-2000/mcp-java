package io.mcp.server.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mcp.server.registry.ToolDefinition;
import io.mcp.server.registry.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core MCP protocol handler.
 * <p>
 * Implements the server side of the Model Context Protocol, handling
 * lifecycle methods (initialize, initialized) and tool operations
 * (tools/list, tools/call).
 * <p>
 * <a href="https://spec.modelcontextprotocol.io">MCP Specification</a>
 */
public class McpProtocolHandler {

    private static final Logger log = LoggerFactory.getLogger(McpProtocolHandler.class);

    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";

    private final ToolRegistry toolRegistry;
    private final String serverName;
    private final String serverVersion;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public McpProtocolHandler(ToolRegistry toolRegistry, String serverName, String serverVersion) {
        this.toolRegistry = toolRegistry;
        this.serverName = serverName;
        this.serverVersion = serverVersion;
    }

    /**
     * Process an incoming JSON-RPC message and return a response.
     *
     * @param request The incoming request
     * @return A response message, or null for notifications (no response expected)
     */
    public JsonRpcMessage handle(JsonRpcRequest request) {
        return switch (request.getMethod()) {
            case "initialize" -> handleInitialize(request);
            case "tools/list" -> handleToolsList(request);
            case "tools/call" -> handleToolsCall(request);
            default -> JsonRpcErrorResponse.methodNotFound(request.getId(), request.getMethod());
        };
    }

    /**
     * Process an incoming notification (no response expected).
     */
    public void handleNotification(JsonRpcNotification notification) {
        if ("notifications/initialized".equals(notification.getMethod())) {
            initialized.set(true);
            log.info("Client initialized");
        } else {
            log.debug("Unhandled notification: {}", notification.getMethod());
        }
    }

    private JsonRpcMessage handleInitialize(JsonRpcRequest request) {
        var capabilities = buildCapabilities();
        var result = JsonRpcMessage.mapper().createObjectNode();
        result.put("protocolVersion", MCP_PROTOCOL_VERSION);

        var serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", serverName);
        serverInfo.put("version", serverVersion);

        var caps = result.putObject("capabilities");
        caps.set("tools", capabilities);

        log.info("Received initialize request, responding with protocol version: {}", MCP_PROTOCOL_VERSION);
        return new JsonRpcResponse(request.getId(), result);
    }

    private ObjectNode buildCapabilities() {
        var caps = JsonRpcMessage.mapper().createObjectNode();
        // Tool capabilities: indicate if the server supports tool listing and calling
        var toolCaps = caps.putObject("tools");
        toolCaps.put("listChanged", false);
        return caps;
    }

    private JsonRpcMessage handleToolsList(JsonRpcRequest request) {
        List<ToolDefinition> tools = toolRegistry.listTools();
        log.info("tools/list: returning {} tools", tools.size());

        var result = JsonRpcMessage.mapper().createObjectNode();
        var toolsArray = result.putArray("tools");

        for (ToolDefinition tool : tools) {
            var toolNode = toolsArray.addObject();
            toolNode.put("name", tool.name());
            toolNode.put("description", tool.description());
            toolNode.set("inputSchema", tool.inputSchema());
        }

        return new JsonRpcResponse(request.getId(), result);
    }

    private JsonRpcMessage handleToolsCall(JsonRpcRequest request) {
        JsonNode params = request.getParams();
        if (params == null || !params.has("name")) {
            return JsonRpcErrorResponse.of(request.getId(), -32602, "Missing required parameter: name");
        }

        String toolName = params.get("name").asText();
        JsonNode arguments = params.has("arguments") ? params.get("arguments") : JsonNodeFactory.instance.objectNode();

        log.info("tools/call: invoking tool '{}'", toolName);

        try {
            Object result = toolRegistry.callTool(toolName, arguments);
            var responseResult = JsonRpcMessage.mapper().createObjectNode();
            var content = responseResult.putArray("content");
            var item = content.addObject();
            item.put("type", "text");
            item.put("text", result != null ? result.toString() : "");

            return new JsonRpcResponse(request.getId(), responseResult);
        } catch (IllegalArgumentException e) {
            return JsonRpcErrorResponse.of(request.getId(), -32602, e.getMessage());
        } catch (Exception e) {
            log.error("Error calling tool '{}'", toolName, e);
            return JsonRpcErrorResponse.internalError(request.getId(), e.getMessage());
        }
    }

    public boolean isInitialized() {
        return initialized.get();
    }
}
