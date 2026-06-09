package io.mcp.server.demo;

import io.mcp.server.annotation.McpResourceProvider;
import io.mcp.server.registry.ResourceDefinition;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Demo resource provider that exposes a status endpoint.
 */
public class DemoResourceProvider {

    @McpResourceProvider
    public List<ResourceDefinition> listResources() {
        return List.of(
                new ResourceDefinition(
                        "status://server",
                        "Server Status",
                        "Current server status and uptime information",
                        "application/json",
                        () -> "{\"status\":\"running\",\"time\":\"" + LocalDateTime.now() + "\"}"),
                new ResourceDefinition(
                        "info://version",
                        "Server Version",
                        "MCP server version information",
                        "text/plain",
                        () -> "mcpm-java server v1.0.0")
        );
    }
}
