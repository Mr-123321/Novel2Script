package com.novel2script.infrastructure.prompt;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;

/**
 * In-memory cache for AI responses keyed by prompt content hash.
 *
 * <p>Cache keys are derived from {@code MD5(prompt + model + params)},
 * ensuring that identical requests hit the cache regardless of call site.
 *
 * <h3>Configuration (application.yml)</h3>
 * <pre>
 * novel2script:
 *   prompt:
 *     cache:
 *       ttl-minutes: 60    # TTL in production: 15, development: 60
 *       max-size: 500
 * </pre>
 */
@Slf4j
@Component
public class PromptCache {

    private final Cache<String, String> cache;

    public PromptCache(
            @Value("${novel2script.prompt.cache.ttl-minutes:60}") long ttlMinutes,
            @Value("${novel2script.prompt.cache.max-size:500}") long maxSize) {

        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .maximumSize(maxSize)
                .recordStats()
                .build();

        log.info("PromptCache initialized: ttl={}min, maxSize={}", ttlMinutes, maxSize);
    }

    /**
     * Retrieve a cached response for the given prompt content and model.
     *
     * @param promptText  the rendered prompt text
     * @param model       the model name used
     * @param temperature the sampling temperature
     * @return cached response, or empty if not found / expired
     */
    public Optional<String> get(String promptText, String model, double temperature) {
        String key = computeKey(promptText, model, temperature);
        String cached = cache.getIfPresent(key);
        if (cached != null) {
            log.debug("PromptCache HIT for key={}", key.substring(0, 16));
        } else {
            log.debug("PromptCache MISS for key={}", key.substring(0, 16));
        }
        return Optional.ofNullable(cached);
    }

    /**
     * Store a response in the cache.
     */
    public void put(String promptText, String model, double temperature, String response) {
        String key = computeKey(promptText, model, temperature);
        cache.put(key, response);
        log.debug("PromptCache PUT key={}", key.substring(0, 16));
    }

    /**
     * Invalidate all cached entries.
     */
    public void invalidateAll() {
        long size = cache.estimatedSize();
        cache.invalidateAll();
        log.info("PromptCache invalidated: {} entries cleared", size);
    }

    /**
     * Get current cache statistics (hit rate, eviction count, etc.).
     */
    public CacheStats stats() {
        return cache.stats();
    }

    /**
     * Current estimated cache size.
     */
    public long size() {
        return cache.estimatedSize();
    }

    // ── Internal ────────────────────────────────────────

    private String computeKey(String promptText, String model, double temperature) {
        String raw = promptText + "|" + model + "|" + temperature;
        return md5(raw);
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
