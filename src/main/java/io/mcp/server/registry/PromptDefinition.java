package io.mcp.server.registry;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

/**
 * Immutable definition of a registered MCP prompt.
 * <p>
 * Prompts are reusable templates that can be invoked by AI clients.
 *
 * @param name         The prompt name (used by clients to reference it)
 * @param description  Human-readable description
 * @param arguments    Optional list of arguments accepted by this prompt
 * @param messages     The messages this prompt generates (as JSON nodes)
 */
public record PromptDefinition(
        String name,
        String description,
        List<PromptArgument> arguments,
        ObjectNode messages) {

    public record PromptArgument(
            String name,
            String description,
            boolean required) {}
}
