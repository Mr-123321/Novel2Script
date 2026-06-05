package com.novel2script.infrastructure.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.Map;

import com.novel2script.common.enums.TaskType;

/**
 * Routes AI tasks to the most appropriate {@link ChatModel} based on
 * {@link TaskType} or an explicit provider name.
 *
 * <h3>Routing strategy</h3>
 * <ul>
 *   <li><b>Simple tasks</b> (chapter parse, compose, export) → cheap model</li>
 *   <li><b>Creative tasks</b> (dialogue, character extraction, plot) → strong model</li>
 *   <li><b>Batch tasks</b> (scene segmentation, action generation) → medium model</li>
 * </ul>
 *
 * <p>Fallback order: task's default provider → global default provider
 * → <i>any available provider</i>.
 */
@Component
public class AiModelRouter {

    private final Map<String, ChatModel> chatModels;
    private final MultiModelProperties properties;

    public AiModelRouter(Map<String, ChatModel> chatModels, MultiModelProperties properties) {
        this.chatModels = chatModels;
        this.properties = properties;
    }

    /**
     * Select a {@link ChatModel} based on the given task type.
     * Uses the task type's default provider preference.
     *
     * @param taskType the type of AI task to perform
     * @return the best ChatModel for this task
     * @throws IllegalStateException if no provider is configured
     */
    public ChatModel route(TaskType taskType) {
        String provider = taskType.getDefaultProvider();
        return resolveModel(provider);
    }

    /**
     * Select a {@link ChatModel} by explicit provider name.
     *
     * @param providerName the provider name (e.g. "deepseek", "openai", "qwen", "claude")
     * @return the ChatModel for this provider
     * @throws IllegalStateException if the provider is not configured
     */
    public ChatModel getModel(String providerName) {
        return resolveModel(providerName);
    }

    /**
     * Return all available models keyed by provider name (read-only view).
     */
    public Map<String, ChatModel> availableModels() {
        return Map.copyOf(chatModels);
    }

    // ── Internal helpers ─────────────────────────────────

    private ChatModel resolveModel(String providerName) {
        // 1. Exact match
        ChatModel model = chatModels.get(providerName);
        if (model != null) {
            return model;
        }

        // 2. Fall back to global default
        String fallback = properties.getDefaultProvider();
        model = chatModels.get(fallback);
        if (model != null) {
            return model;
        }

        // 3. Last resort: any available model
        if (!chatModels.isEmpty()) {
            return chatModels.values().iterator().next();
        }

        throw new IllegalStateException(
                "No AI provider configured. Add at least one entry to spring.ai.providers.");
    }
}
