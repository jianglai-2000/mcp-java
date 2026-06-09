package io.mcp.server.demo;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mcp.server.annotation.McpPromptProvider;
import io.mcp.server.protocol.JsonRpcMessage;
import io.mcp.server.registry.PromptDefinition;

import java.util.List;

/**
 * Demo prompt provider with example prompt templates.
 */
public class DemoPromptProvider {

    @McpPromptProvider
    public List<PromptDefinition> listPrompts() {
        // Greeting prompt
        ObjectNode greetingMessages = JsonRpcMessage.mapper().createObjectNode();
        var msgArr = greetingMessages.putArray("messages");
        var msg = msgArr.addObject();
        msg.put("role", "assistant");
        msg.put("content", "Hello! I'm an MCP-powered AI assistant. How can I help you today?");

        return List.of(
                new PromptDefinition(
                        "greeting",
                        "A friendly greeting message",
                        List.of(),
                        greetingMessages),
                new PromptDefinition(
                        "summarize",
                        "Summarize the given text",
                        List.of(
                                new PromptDefinition.PromptArgument("text", "The text to summarize", true),
                                new PromptDefinition.PromptArgument("max_words", "Maximum words in summary", false)
                        ),
                        greetingMessages)
        );
    }
}
