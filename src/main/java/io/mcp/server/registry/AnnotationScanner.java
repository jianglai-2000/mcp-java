package io.mcp.server.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mcp.server.annotation.McpParam;
import io.mcp.server.annotation.McpTool;
import io.mcp.server.annotation.McpToolProvider;
import io.mcp.server.protocol.JsonRpcMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

/**
 * Scans annotated classes for {@link McpTool} methods and registers them.
 * <p>
 * Usage:
 * <pre>{@code
 * var registry = new ToolRegistry();
 * var scanner = new AnnotationScanner(registry);
 * scanner.scan(new WeatherTools());
 * }</pre>
 */
public class AnnotationScanner {

    private static final Logger log = LoggerFactory.getLogger(AnnotationScanner.class);

    private final ToolRegistry registry;

    public AnnotationScanner(ToolRegistry registry) {
        this.registry = registry;
    }

    /**
     * Scan an object for @McpToolProvider and @McpTool annotations, registering
     * all discovered tools.
     *
     * @param provider the object whose class will be scanned
     */
    public void scan(Object provider) {
        Class<?> clazz = provider.getClass();

        // Check for @McpToolProvider (optional — we also scan plain objects)
        if (!clazz.isAnnotationPresent(McpToolProvider.class)) {
            log.debug("Class {} is not annotated with @McpToolProvider, scanning methods anyway", clazz.getSimpleName());
        }

        Arrays.stream(clazz.getMethods())
                .filter(m -> m.isAnnotationPresent(McpTool.class))
                .forEach(method -> registerTool(provider, method));
    }

    private void registerTool(Object instance, Method method) {
        McpTool annotation = method.getAnnotation(McpTool.class);
        String name = annotation.name();
        String description = annotation.description();

        // Build JSON Schema from method parameters
        ObjectNode inputSchema = buildInputSchema(method);

        ToolDefinition tool = new ToolDefinition(
                name,
                description,
                inputSchema,
                arguments -> {
                    // Convert JSON arguments to Java method call
                    Object[] args = buildArguments(method, arguments);
                    return method.invoke(instance, args);
                }
        );

        try {
            registry.register(tool);
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
            if (!paramDesc.isEmpty()) {
                prop.put("description", paramDesc);
            }

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
            if (value == null || value.isNull()) {
                args[i] = null;
            } else {
                args[i] = convertValue(value, param.getType());
            }
        }

        return args;
    }

    private static String jsonTypeFor(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == long.class || type == Integer.class || type == Long.class) return "integer";
        if (type == float.class || type == double.class || type == Float.class || type == Double.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        return "string"; // fallback
    }

    private static Object convertValue(JsonNode value, Class<?> targetType) throws Exception {
        if (targetType == String.class) return value.asText();
        if (targetType == int.class || targetType == Integer.class) return value.asInt();
        if (targetType == long.class || targetType == Long.class) return value.asLong();
        if (targetType == double.class || targetType == Double.class) return value.asDouble();
        if (targetType == boolean.class || targetType == Boolean.class) return value.asBoolean();
        return JsonRpcMessage.mapper().treeToValue(value, targetType);
    }
}
