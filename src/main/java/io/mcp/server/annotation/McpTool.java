package io.mcp.server.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as an MCP tool that can be called by AI clients.
 * <p>
 * Example usage:
 * <pre>{@code
 * @McpTool(name = "get_weather", description = "Get current weather for a city")
 * public String getWeather(@McpParam("city") String city) {
 *     return weatherService.query(city);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {

    /** The name of the tool. Used by AI clients to invoke this tool. */
    String name();

    /** A human-readable description of what this tool does. */
    String description() default "";
}
