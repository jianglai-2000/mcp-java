package io.mcp.server.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mcp.server.annotation.*;
import io.mcp.server.protocol.JsonRpcMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;

/**
 * Scans annotated classes for tools, resources, and prompts.
 * <p>
 * Supports:
 * <ul>
 *   <li>{@link McpTool} methods in {@link McpToolProvider} classes</li>
 *   <li>{@link McpResourceProvider} methods returning resource definitions</li>
 *   <li>{@link McpPromptProvider} methods returning prompt definitions</li>
 * </ul>
 */
public class AnnotationScanner {

    private static final Logger log = LoggerFactory.getLogger(AnnotationScanner.class);

    private final ToolRegistry toolRegistry;
    private final ResourceRegistry resourceRegistry;
    private final PromptRegistry promptRegistry;

    public AnnotationScanner(ToolRegistry toolRegistry) {
        this(toolRegistry, new ResourceRegistry(), new PromptRegistry());
    }

    public AnnotationScanner(ToolRegistry toolRegistry, ResourceRegistry resourceRegistry, PromptRegistry promptRegistry) {
        this.toolRegistry = toolRegistry;
        this.resourceRegistry = resourceRegistry;
        this.promptRegistry = promptRegistry;
    }

    @SuppressWarnings("unchecked")
    public void scan(Object provider) {
        Class<?> clazz = provider.getClass();
        boolean hasAnnotations = false;

        // Scan @McpTool methods
        for (Method method : clazz.getMethods()) {
            if (method.isAnnotationPresent(McpTool.class)) {
                registerTool(provider, method);
                hasAnnotations = true;
            }
        }

        // Scan @McpResourceProvider methods
        for (Method method : clazz.getMethods()) {
            if (method.isAnnotationPresent(McpResourceProvider.class)) {
                try {
                    Object result = method.invoke(provider);
                    if (result instanceof List<?> list) {
                        resourceRegistry.registerAll((List<ResourceDefinition>) list);
                        log.info("Registered resources from {}.{}", clazz.getSimpleName(), method.getName());
                    }
                } catch (Exception e) {
                    log.warn("Failed to register resources from {}.{}: {}", clazz.getSimpleName(), method.getName(), e.getMessage());
                }
                hasAnnotations = true;
            }
        }

        // Scan @McpPromptProvider methods
        for (Method method : clazz.getMethods()) {
            if (method.isAnnotationPresent(McpPromptProvider.class)) {
                try {
                    Object result = method.invoke(provider);
                    if (result instanceof PromptDefinition prompt) {
                        promptRegistry.register(prompt);
                        log.info("Registered prompt: {} from {}.{}", prompt.name(), clazz.getSimpleName(), method.getName());
                    } else if (result instanceof List<?> list) {
                        promptRegistry.registerAll((List<PromptDefinition>) list);
                        log.info("Registered prompts from {}.{}", clazz.getSimpleName(), method.getName());
                    }
                } catch (Exception e) {
                    log.warn("Failed to register prompt from {}.{}: {}", clazz.getSimpleName(), method.getName(), e.getMessage());
                }
                hasAnnotations = true;
            }
        }

        if (!hasAnnotations) {
            log.debug("No MCP annotations found on {}.{}", clazz.getPackageName(), clazz.getSimpleName());
        }
    }

    // ---- Tool registration (unchanged) ----

    private void registerTool(Object instance, Method method) {
        McpTool annotation = method.getAnnotation(McpTool.class);
        String name = annotation.name();
        String description = annotation.description();
        ObjectNode inputSchema = buildInputSchema(method);

        ToolDefinition tool = new ToolDefinition(name, description, inputSchema,
                arguments -> {
                    Object[] args = buildArguments(method, arguments);
                    return method.invoke(instance, args);
                });

        try {
            toolRegistry.register(tool);
            log.info("Registered tool: {} (from {}.{})", name, method.getDeclaringClass().getSimpleName(), method.getName());
        } catch (IllegalArgumentException e) {
            log.warn("Duplicate tool registration skipped: {}", name);
        }
    }

    private ObjectNode buildInputSchema(Method method) {
        ObjectNode schema = JsonRpcMessage.mapper().createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        for (Parameter param : method.getParameters()) {
            McpParam mcpParam = param.getAnnotation(McpParam.class);
            String paramName = mcpParam != null ? mcpParam.value() : param.getName();
            String paramDesc = mcpParam != null ? mcpParam.description() : "";
            ObjectNode prop = properties.putObject(paramName);
            prop.put("type", jsonTypeFor(param.getType()));
            if (!paramDesc.isEmpty()) prop.put("description", paramDesc);
            required.add(paramName);
        }
        return schema;
    }

    private Object[] buildArguments(Method method, JsonNode arguments) throws Exception {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            McpParam mcpParam = param.getAnnotation(McpParam.class);
            String paramName = mcpParam != null ? mcpParam.value() : param.getName();
            JsonNode value = arguments.get(paramName);
            args[i] = (value == null || value.isNull()) ? null : convertValue(value, param.getType());
        }
        return args;
    }

    private static String jsonTypeFor(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == long.class || type == Integer.class || type == Long.class) return "integer";
        if (type == float.class || type == double.class || type == Float.class || type == Double.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        return "string";
    }

    private static Object convertValue(JsonNode value, Class<?> targetType) throws Exception {
        if (targetType == String.class) return value.asText();
        if (targetType == int.class || targetType == Integer.class) return value.asInt();
        if (targetType == long.class || targetType == Long.class) return value.asLong();
        if (targetType == double.class || targetType == Double.class) return value.asDouble();
        if (targetType == boolean.class || targetType == Boolean.class) return value.asBoolean();
        return JsonRpcMessage.mapper().treeToValue(value, targetType);
    }

    public ToolRegistry toolRegistry() { return toolRegistry; }
    public ResourceRegistry resourceRegistry() { return resourceRegistry; }
    public PromptRegistry promptRegistry() { return promptRegistry; }
}
