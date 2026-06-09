package io.mcp.server.registry;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of all MCP resources exposed by the server.
 * <p>
 * Thread-safe. Resources can be added/removed at runtime.
 */
public class ResourceRegistry {

    private final List<ResourceDefinition> resources = new CopyOnWriteArrayList<>();

    public void register(ResourceDefinition resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        resources.add(resource);
    }

    public void registerAll(Collection<ResourceDefinition> defs) {
        resources.addAll(defs);
    }

    public Optional<ResourceDefinition> getResource(String uri) {
        return resources.stream()
                .filter(r -> r.uri().equals(uri))
                .findFirst();
    }

    public List<ResourceDefinition> listResources() {
        return List.copyOf(resources);
    }

    public void unregister(String uri) {
        resources.removeIf(r -> r.uri().equals(uri));
    }

    public int size() {
        return resources.size();
    }

    public void clear() {
        resources.clear();
    }
}
