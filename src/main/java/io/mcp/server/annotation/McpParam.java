package io.mcp.server.annotation;

import java.lang.annotation.*;

/**
 * Marks a method parameter as an MCP tool parameter.
 * <p>
 * The parameter name and type are automatically inferred from the Java method
 * signature, and included in the tool's JSON Schema input definition.
 * <p>
 * Example:
 * <pre>{@code
 * public String greet(@McpParam("name") String name) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpParam {

    /** The parameter name exposed in the tool's JSON Schema. */
    String value();

    /** Optional description of this parameter. */
    String description() default "";
}
