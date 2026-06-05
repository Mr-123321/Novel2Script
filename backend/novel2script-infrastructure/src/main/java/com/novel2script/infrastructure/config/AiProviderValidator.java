package com.novel2script.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Validates AI provider configuration at application startup.
 * Ensures at least one provider is properly configured with an API key.
 */
@Slf4j
@Component
public class AiProviderValidator implements CommandLineRunner {

    private final MultiModelProperties properties;

    public AiProviderValidator(MultiModelProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("  AI Provider Configuration Validation");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        Map<String, MultiModelProperties.ProviderConfig> providers = properties.getProviders();
        
        if (providers == null || providers.isEmpty()) {
            log.error("❌ No AI providers configured!");
            log.error("");
            log.error("Please configure at least one AI provider in application-dev.yml:");
            log.error("");
            log.error("spring:");
            log.error("  ai:");
            log.error("    default-provider: deepseek");
            log.error("    providers:");
            log.error("      deepseek:");
            log.error("        api-key: your-api-key-here");
            log.error("        base-url: https://api.deepseek.com");
            log.error("        chat:");
            log.error("          options:");
            log.error("            model: deepseek-chat");
            log.error("            temperature: 0.7");
            log.error("            max-tokens: 4096");
            log.error("");
            log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            throw new IllegalStateException("No AI providers configured. Please check application-dev.yml");
        }

        // Check for at least one valid provider with API key
        long validCount = providers.entrySet().stream()
                .filter(entry -> {
                    String name = entry.getKey();
                    MultiModelProperties.ProviderConfig config = entry.getValue();
                    boolean hasApiKey = config.getApiKey() != null && !config.getApiKey().isBlank();
                    
                    if (!hasApiKey) {
                        log.warn("⚠️  Provider '{}' has no API key configured (skipped)", name);
                    } else {
                        log.info("✅ Provider '{}' configured successfully", name);
                        log.info("   - Base URL: {}", config.getBaseUrl());
                        log.info("   - Model: {}", config.getChat() != null && config.getChat().getOptions() != null 
                                ? config.getChat().getOptions().getModel() : "N/A");
                    }
                    
                    return hasApiKey;
                })
                .count();

        if (validCount == 0) {
            log.error("");
            log.error("❌ No valid AI providers found! All providers are missing API keys.");
            log.error("");
            log.error("Available providers to configure:");
            providers.keySet().forEach(name -> 
                log.error("  - {} (add api-key in application-dev.yml)", name));
            log.error("");
            log.error("Example configuration:");
            log.error("  spring.ai.providers.deepseek.api-key: sk-your-key");
            log.error("");
            log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            throw new IllegalStateException(
                "No AI providers have valid API keys. Please configure at least one provider in application-dev.yml");
        }

        String defaultProvider = properties.getDefaultProvider();
        MultiModelProperties.ProviderConfig defaultConfig = providers.get(defaultProvider);
        
        if (defaultConfig == null || defaultConfig.getApiKey() == null || defaultConfig.getApiKey().isBlank()) {
            log.warn("⚠️  Default provider '{}' is not configured or has no API key", defaultProvider);
            log.warn("   The system will use the first available provider instead");
        } else {
            log.info("");
            log.info("✅ Default provider: {} (ready to use)", defaultProvider);
        }

        log.info("");
        log.info("📊 Summary: {} valid provider(s) configured", validCount);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
