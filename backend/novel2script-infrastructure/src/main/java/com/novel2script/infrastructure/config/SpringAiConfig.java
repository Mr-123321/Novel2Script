package com.novel2script.infrastructure.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring AI multi-model configuration.
 *
 * <p>Creates a {@link ChatModel} bean (and corresponding {@link ChatClient})
 * for every provider listed under {@code spring.ai.providers.*}.
 * Each bean is qualified by the provider name so that {@link AiModelRouter}
 * can select the right model at runtime.
 *
 * <p>The default provider gets the {@link Primary} qualifier so that
 * injection points without an explicit {@code @Qualifier} still work.
 */
@Configuration
@EnableConfigurationProperties(MultiModelProperties.class)
public class SpringAiConfig {

    private final MultiModelProperties multiModelProperties;

    public SpringAiConfig(MultiModelProperties multiModelProperties) {
        this.multiModelProperties = multiModelProperties;
    }

    // ── ChatModel beans (one per provider) ───────────────

    /**
     * Build a {@link ChatModel} for every configured provider.
     *
     * @return a map of provider-name → ChatModel
     */
    @Bean
    public Map<String, ChatModel> chatModels() {
        Map<String, ChatModel> models = new HashMap<>();

        multiModelProperties.getProviders().forEach((name, config) -> {
            MultiModelProperties.ChatOptions opts = config.getChat().getOptions();

            // Build OpenAiApi targeting this provider's base URL
            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(config.getBaseUrl())
                    .apiKey(config.getApiKey())
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
        });

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
}
