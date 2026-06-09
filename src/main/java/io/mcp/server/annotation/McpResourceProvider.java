package io.mcp.server.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as an MCP resource provider.
 * <p>
 * The method should return a list of ResourceDefinition objects.
 * <pre>{@code
 * @McpResourceProvider
 * public List<ResourceDefinition> listResources() {
 *     return List.of(new ResourceDefinition("file:///logs/app.log", "Application Log", "text/plain"));
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpResourceProvider {
}
