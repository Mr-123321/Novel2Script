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
     * Custom {@link RestClient.Builder} with HTTP timeouts.
     * Prevents AI API calls from hanging indefinitely.
     * connect timeout = 5s, read timeout = 30s (balanced for AI response time).
     */
    @Bean
    @Primary
    public RestClient.Builder restClientBuilder() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);   // 5 seconds
        requestFactory.setReadTimeout(30_000);      // 30 seconds
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

                // Build chat options from YAML config
                OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                        .model(opts.getModel())
                        .temperature(opts.getTemperature())
                        .maxTokens(opts.getMaxTokens())
                        .build();

                OpenAiChatModel chatModel = OpenAiChatModel.builder()
                        .openAiApi(api)
                        .defaultOptions(chatOptions)
                        .build();

                models.put(name, chatModel);
                log.info("✅ ChatModel ready: {} ({}) timeout: connect=5s read=30s",
                        name, opts.getModel());
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

    // ── EmbeddingModel bean (optional) ───────────────────

    /**
     * Create an {@link EmbeddingModel} using the default provider (deepseek).
     * If no API key is configured, returns null and EmbeddingService will use hash-based fallback.
     *
     * @return EmbeddingModel or null if not configured
     */
    @Bean
    @org.springframework.context.annotation.Primary
    public EmbeddingModel embeddingModel() {
        String defaultProvider = multiModelProperties.getDefaultProvider();
        MultiModelProperties.ProviderConfig config = multiModelProperties.getProviders().get(defaultProvider);

        if (config == null || config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.info("No API key configured for default provider '{}', EmbeddingModel will not be created", defaultProvider);
            return null;
        }

        try {
            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(config.getBaseUrl())
                    .apiKey(config.getApiKey())
                    .build();

            OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                    .model("deepseek-embedding")
                    .build();

            OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(
                    api,
                    org.springframework.ai.document.MetadataMode.EMBED,
                    options
            );

            log.info("✅ Created EmbeddingModel for provider: {}", defaultProvider);
            return embeddingModel;
        } catch (Exception e) {
            log.warn("Failed to create EmbeddingModel for provider '{}': {}. Using hash-based fallback.",
                    defaultProvider, e.getMessage());
            return null;
        }
    }
}
