package com.novel2script.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring AI multi-model configuration.
 *
 * <p>Creates a {@link ChatModel} bean (and corresponding {@link ChatClient})
 * for every provider listed under {@code spring.ai.providers.*} that has a valid API key.
 * Each bean is qualified by the provider name so that {@link AiModelRouter}
 * can select the right model at runtime.
 *
 * <p>Only providers with non-blank API keys will be instantiated.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(MultiModelProperties.class)
public class SpringAiConfig {

    private final MultiModelProperties multiModelProperties;

    public SpringAiConfig(MultiModelProperties multiModelProperties) {
        this.multiModelProperties = multiModelProperties;
    }

    /**
     * Custom {@link RestClient.Builder} with HTTP timeouts tuned for AI streaming.
     * connect timeout = 10s, read timeout = 120s (accommodates long AI generations).
     * <p>
     * When streaming is enabled ({@code stream: true}), the AI sends SSE events
     * incrementally, keeping the connection alive and preventing timeouts even
     * for multi-minute generation tasks.
     */
    @Bean
    @Primary
    public RestClient.Builder restClientBuilder() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);   // 10 seconds
        requestFactory.setReadTimeout(120_000);      // 120 seconds — AI generation can take time
        return RestClient.builder().requestFactory(requestFactory);
    }

    // ── ChatModel beans (one per provider) ───────────────

    /**
     * Build a {@link ChatModel} for every configured provider with a valid API key.
     *
     * @return a map of provider-name → ChatModel (only providers with API keys)
     */
    @Bean
    public Map<String, ChatModel> chatModels(RestClient.Builder restClientBuilder) {
        Map<String, ChatModel> models = new HashMap<>();

        multiModelProperties.getProviders().forEach((name, config) -> {
            // Skip providers without API keys
            if (config.getApiKey() == null || config.getApiKey().isBlank()) {
                log.info("⏭️  Provider '{}' skipped — no API key configured", name);
                return;
            }

            try {
                MultiModelProperties.ChatOptions opts = config.getChat().getOptions();

                // Build OpenAiApi targeting this provider's base URL
                // Use the custom RestClient.Builder with timeouts
                OpenAiApi api = OpenAiApi.builder()
                        .baseUrl(config.getBaseUrl())
                        .apiKey(config.getApiKey())
                        .restClientBuilder(restClientBuilder)
                        .build();

                // Build chat options from YAML config (including streaming)
                OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                        .model(opts.getModel())
                        .temperature(opts.getTemperature())
                        .maxTokens(opts.getMaxTokens())
                        .streamUsage(opts.isStream())
                        .build();

                OpenAiChatModel chatModel = OpenAiChatModel.builder()
                        .openAiApi(api)
                        .defaultOptions(chatOptions)
                        .build();

                models.put(name, chatModel);
                log.info("✅ ChatModel ready: {} ({}) timeout: connect=10s read=120s stream={}",
                        name, opts.getModel(), opts.isStream());
            } catch (Exception e) {
                log.error("❌ Failed to create ChatModel for provider '{}': {}", name, e.getMessage());
                throw new IllegalStateException(
                    "Failed to initialize AI provider '" + name + "'. Please check your configuration.", e);
            }
        });

        if (models.isEmpty()) {
            log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.error("❌ No AI providers could be initialized!");
            log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            throw new IllegalStateException(
                "No AI providers are properly configured. Please add at least one provider with a valid API key in application-dev.yml");
        }

        log.info("📊 Total ChatModels initialized: {}, available: {}", models.size(), models.keySet());
        return models;
    }

    // ── ChatClient beans (one per provider) ──────────────

    /**
     * Build a {@link ChatClient} for every configured provider.
     */
    @Bean
    public Map<String, ChatClient> chatClients(Map<String, ChatModel> chatModels) {
        Map<String, ChatClient> clients = new HashMap<>();
        chatModels.forEach((name, model) ->
                clients.put(name, ChatClient.builder(model).build()));
        return clients;
    }

    // ── Streaming ChatClient (for long-generation tasks) ─

    /**
     * Build a streaming {@link ChatClient} keyed by provider name.
     * Agents performing long-generation tasks (e.g., script composition)
     * should use this client with {@code .prompt().stream().chatResponse()}
     * to receive SSE events incrementally, avoiding read-timeout.
     *
     * @see com.novel2script.application.service.agent.ScriptGenerationAgent
     */
    @Bean
    public Map<String, ChatClient> streamingChatClients(Map<String, ChatModel> chatModels) {
        Map<String, ChatClient> clients = new HashMap<>();
        chatModels.forEach((name, model) ->
                clients.put(name, ChatClient.builder(model).build()));
        return clients;
    }

    // ── EmbeddingModel bean (with graceful fallback) ─────

    /**
     * Create an {@link EmbeddingModel} using the configured embedding model
     * (default: Qwen {@code text-embedding-v1}) via the default provider's API.
     *
     * <h3>Fallback chain</h3>
     * <ol>
     *   <li>Try to create {@link OpenAiEmbeddingModel} with configured model</li>
     *   <li>If the default provider has no API key → return {@code null}</li>
     *   <li>If creation throws → log warning, return {@code null}</li>
     *   <li>{@link com.novel2script.infrastructure.vector.EmbeddingService}
     *       detects {@code null} and falls back to hash-based embedding</li>
     * </ol>
     *
     * @return EmbeddingModel or {@code null} if unavailable
     */
    @Bean
    @org.springframework.context.annotation.Primary
    public EmbeddingModel embeddingModel(RestClient.Builder restClientBuilder) {
        MultiModelProperties.EmbeddingConfig embConfig = multiModelProperties.getEmbedding();

        // ── Check if embedding is explicitly disabled ──
        if (!embConfig.isEnabled()) {
            log.info("⏭️  Embedding model disabled via config — will use hash-based fallback");
            return null;
        }

        String modelName = embConfig.getModel();
        String defaultProvider = multiModelProperties.getDefaultProvider();
        MultiModelProperties.ProviderConfig providerConfig =
                multiModelProperties.getProviders().get(defaultProvider);

        // ── Check provider availability ──
        if (providerConfig == null) {
            log.warn("⚠️  Default provider '{}' not configured — embedding model unavailable, "
                    + "falling back to hash-based embedding", defaultProvider);
            return null;
        }

        if (providerConfig.getApiKey() == null || providerConfig.getApiKey().isBlank()) {
            log.warn("⚠️  No API key for provider '{}' — embedding model unavailable, "
                    + "falling back to hash-based embedding", defaultProvider);
            return null;
        }

        // ── Build the embedding model ──
        try {
            log.info("🔧 Creating EmbeddingModel: provider={}, model={}, baseUrl={}",
                    defaultProvider, modelName, providerConfig.getBaseUrl());

            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(providerConfig.getBaseUrl())
                    .apiKey(providerConfig.getApiKey())
                    .restClientBuilder(restClientBuilder)
                    .build();

            OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                    .model(modelName)                       // ← configurable (text-embedding-v1)
                    .build();

            OpenAiEmbeddingModel model = new OpenAiEmbeddingModel(
                    api,
                    org.springframework.ai.document.MetadataMode.EMBED,
                    options
            );

            log.info("✅ EmbeddingModel created successfully: model={}, provider={}, dimensions=1536",
                    modelName, defaultProvider);
            return model;

        } catch (Exception e) {
            log.warn("┌─────────────────────────────────────────────────────");
            log.warn("│ ⚠️  EmbeddingModel creation FAILED");
            log.warn("│ Provider: {}", defaultProvider);
            log.warn("│ Model:    {}", modelName);
            log.warn("│ Error:    {}", e.getMessage());
            log.warn("│ Action:   Falling back to hash-based embedding");
            log.warn("│ Impact:   Vector search accuracy will be reduced");
            log.warn("│           (cosine similarity on hash embeddings)");
            log.warn("│ Fix:      Verify API key and network connectivity");
            log.warn("└─────────────────────────────────────────────────────");
            return null;
        }
    }
}
