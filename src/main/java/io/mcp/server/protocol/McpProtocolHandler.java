package io.mcp.server.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mcp.server.registry.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core MCP protocol handler.
 * <p>
 * Handles lifecycle, tools, resources, and prompts per the MCP specification.
 */
public class McpProtocolHandler {

    private static final Logger log = LoggerFactory.getLogger(McpProtocolHandler.class);
    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";

    private final ToolRegistry toolRegistry;
    private final ResourceRegistry resourceRegistry;
    private final PromptRegistry promptRegistry;
    private final String serverName;
    private final String serverVersion;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /** Convenience constructor with empty resource/prompt registries (backward compat). */
    public McpProtocolHandler(ToolRegistry toolRegistry, String serverName, String serverVersion) {
        this(toolRegistry, new ResourceRegistry(), new PromptRegistry(), serverName, serverVersion);
    }

    public McpProtocolHandler(ToolRegistry toolRegistry, ResourceRegistry resourceRegistry,
                              PromptRegistry promptRegistry, String serverName, String serverVersion) {
        this.toolRegistry = toolRegistry;
        this.resourceRegistry = resourceRegistry;
        this.promptRegistry = promptRegistry;
        this.serverName = serverName;
        this.serverVersion = serverVersion;
    }

    public JsonRpcMessage handle(JsonRpcRequest request) {
        return switch (request.getMethod()) {
            case "initialize" -> handleInitialize(request);
            case "tools/list" -> handleToolsList(request);
            case "tools/call" -> handleToolsCall(request);
            case "resources/list" -> handleResourcesList(request);
            case "resources/read" -> handleResourcesRead(request);
            case "prompts/list" -> handlePromptsList(request);
            case "prompts/get" -> handlePromptsGet(request);
            default -> JsonRpcErrorResponse.methodNotFound(request.getId(), request.getMethod());
        };
    }

    public void handleNotification(JsonRpcNotification notification) {
        if ("notifications/initialized".equals(notification.getMethod())) {
            initialized.set(true);
            log.info("Client initialized");
        } else {
            log.debug("Unhandled notification: {}", notification.getMethod());
        }
    }

    // ---- Initialize ----

    private JsonRpcMessage handleInitialize(JsonRpcRequest request) {
        var result = JsonRpcMessage.mapper().createObjectNode();
        result.put("protocolVersion", MCP_PROTOCOL_VERSION);

        var serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", serverName);
        serverInfo.put("version", serverVersion);

        var caps = result.putObject("capabilities");
        caps.set("tools", JsonRpcMessage.mapper().createObjectNode().put("listChanged", false));
        caps.set("resources", JsonRpcMessage.mapper().createObjectNode()
                .put("listChanged", false).put("subscribe", false));
        caps.set("prompts", JsonRpcMessage.mapper().createObjectNode().put("listChanged", false));

        log.info("Responded to initialize with capabilities: tools, resources, prompts");
        return new JsonRpcResponse(request.getId(), result);
    }

    // ---- Tools ----

    private JsonRpcMessage handleToolsList(JsonRpcRequest request) {
        List<ToolDefinition> tools = toolRegistry.listTools();
        var result = JsonRpcMessage.mapper().createObjectNode();
        var toolsArray = result.putArray("tools");
        for (ToolDefinition tool : tools) {
            var t = toolsArray.addObject();
            t.put("name", tool.name());
            t.put("description", tool.description());
            t.set("inputSchema", tool.inputSchema());
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
            return JsonRpcErrorResponse.internalError(request.getId(), e.getMessage());
        }
    }

    // ---- Resources ----

    private JsonRpcMessage handleResourcesList(JsonRpcRequest request) {
        List<ResourceDefinition> resources = resourceRegistry.listResources();
        var result = JsonRpcMessage.mapper().createObjectNode();
        var arr = result.putArray("resources");
        for (ResourceDefinition r : resources) {
            var n = arr.addObject();
            n.put("uri", r.uri());
            n.put("name", r.name());
            if (r.description() != null && !r.description().isEmpty()) n.put("description", r.description());
            if (r.mimeType() != null) n.put("mimeType", r.mimeType());
        }
        return new JsonRpcResponse(request.getId(), result);
    }

    private JsonRpcMessage handleResourcesRead(JsonRpcRequest request) {
        JsonNode params = request.getParams();
        if (params == null || !params.has("uri")) {
            return JsonRpcErrorResponse.of(request.getId(), -32602, "Missing required parameter: uri");
        }
        String uri = params.get("uri").asText();
        var resourceOpt = resourceRegistry.getResource(uri);
        if (resourceOpt.isEmpty()) {
            return JsonRpcErrorResponse.of(request.getId(), -32602, "Resource not found: " + uri);
        }
        try {
            String content = resourceOpt.get().reader().read();
            var result = JsonRpcMessage.mapper().createObjectNode();
            var contents = result.putArray("contents");
            var c = contents.addObject();
            c.put("uri", uri);
            c.put("mimeType", resourceOpt.get().mimeType());
            c.put("text", content);
            return new JsonRpcResponse(request.getId(), result);
        } catch (Exception e) {
            return JsonRpcErrorResponse.internalError(request.getId(), "Failed to read resource: " + e.getMessage());
        }
    }

    // ---- Prompts ----

    private JsonRpcMessage handlePromptsList(JsonRpcRequest request) {
        List<PromptDefinition> prompts = promptRegistry.listPrompts();
        var result = JsonRpcMessage.mapper().createObjectNode();
        var arr = result.putArray("prompts");
        for (PromptDefinition p : prompts) {
            var n = arr.addObject();
            n.put("name", p.name());
            if (p.description() != null) n.put("description", p.description());
            if (p.arguments() != null && !p.arguments().isEmpty()) {
                var argsArr = n.putArray("arguments");
                for (var arg : p.arguments()) {
                    var a = argsArr.addObject();
                    a.put("name", arg.name());
                    if (arg.description() != null) a.put("description", arg.description());
                    a.put("required", arg.required());
                }
            }
        }
        return new JsonRpcResponse(request.getId(), result);
    }

    private JsonRpcMessage handlePromptsGet(JsonRpcRequest request) {
        JsonNode params = request.getParams();
        if (params == null || !params.has("name")) {
            return JsonRpcErrorResponse.of(request.getId(), -32602, "Missing required parameter: name");
        }
        String name = params.get("name").asText();
        var promptOpt = promptRegistry.getPrompt(name);
        if (promptOpt.isEmpty()) {
            return JsonRpcErrorResponse.of(request.getId(), -32602, "Prompt not found: " + name);
        }
        var result = JsonRpcMessage.mapper().createObjectNode();
        result.set("messages", promptOpt.get().messages());
        return new JsonRpcResponse(request.getId(), result);
    }

    public boolean isInitialized() {
        return initialized.get();
    }
}
