package com.novel2script.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Multi-provider AI model configuration bound from {@code spring.ai.*}.
 *
 * <p>Supports any OpenAI-compatible API provider (DeepSeek, Qwen, OpenAI, etc.)
 * by configuring their API key, base URL, and chat options independently.
 */
@ConfigurationProperties(prefix = "spring.ai")
public class MultiModelProperties {

    /** Default provider name used when no specific provider is requested. */
    private String defaultProvider = "deepseek";

    /** Per-provider configurations keyed by provider name. */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    // ── Getters / Setters ─────────────────────────────────

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderConfig> providers) {
        this.providers = providers;
    }

    /**
     * Resolve the configuration for the default provider.
     *
     * @throws IllegalStateException if the default provider is not configured
     */
    public ProviderConfig getDefaultProviderConfig() {
        ProviderConfig config = providers.get(defaultProvider);
        if (config == null) {
            throw new IllegalStateException(
                    "Default provider '" + defaultProvider + "' is not configured in spring.ai.providers");
        }
        return config;
    }

    /**
     * Resolve the configuration for a named provider, falling back to the default.
     */
    public ProviderConfig getProviderConfig(String name) {
        return providers.getOrDefault(name, getDefaultProviderConfig());
    }

    // ── Inner records ─────────────────────────────────────

    /**
     * Configuration for a single AI provider.
     */
    public static class ProviderConfig {
        private String apiKey;
        private String baseUrl = "https://api.openai.com";
        private ChatConfig chat = new ChatConfig();

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public ChatConfig getChat() { return chat; }
        public void setChat(ChatConfig chat) { this.chat = chat; }
    }

    /**
     * Chat configuration wrapper (maps to {@code chat.options.*} in YAML).
     */
    public static class ChatConfig {
        private ChatOptions options = new ChatOptions();

        public ChatOptions getOptions() { return options; }
        public void setOptions(ChatOptions options) { this.options = options; }
    }

    /**
     * Chat completion options (model, temperature, max-tokens).
     */
    public static class ChatOptions {
        private String model = "deepseek-chat";
        private double temperature = 0.7;
        private int maxTokens = 4096;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    }
}
