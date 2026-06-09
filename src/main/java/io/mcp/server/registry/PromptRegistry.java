package io.mcp.server.registry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of all MCP prompts exposed by the server.
 * <p>
 * Thread-safe. Prompts can be registered programmatically or via annotation scanning.
 */
public class PromptRegistry {

    private final Map<String, PromptDefinition> prompts = new ConcurrentHashMap<>();

    public void register(PromptDefinition prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        if (prompts.putIfAbsent(prompt.name(), prompt) != null) {
            throw new IllegalArgumentException("Prompt already registered: " + prompt.name());
        }
    }

    public void registerAll(Collection<PromptDefinition> defs) {
        defs.forEach(this::register);
    }

    public Optional<PromptDefinition> getPrompt(String name) {
        return Optional.ofNullable(prompts.get(name));
    }

    public List<PromptDefinition> listPrompts() {
        return List.copyOf(prompts.values());
    }

    public void unregister(String name) {
        prompts.remove(name);
    }

    public int size() {
        return prompts.size();
    }
}
