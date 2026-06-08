package io.mcp.server.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a provider of MCP tools.
 * <p>
 * When a class is annotated with {@code @McpToolProvider}, the server will
 * scan its methods for {@link McpTool} annotations and register them automatically.
 * <p>
 * Example:
 * <pre>{@code
 * @McpToolProvider
 * public class WeatherTools {
 *
 *     @McpTool(name = "get_weather", description = "...")
 *     public String getWeather(@McpParam("city") String city) {
 *         return "Sunny 25°C";
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpToolProvider {
}
