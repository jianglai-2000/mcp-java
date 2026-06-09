package io.mcp.server.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as an MCP prompt provider.
 * <p>
 * The method should return a PromptDefinition or list of PromptDefinitions.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpPromptProvider {
}
