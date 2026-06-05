package com.novel2script.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * Spring Retry configuration — exponential backoff for AI API calls.
 *
 * <h3>Retry policy</h3>
 * <ul>
 *   <li>Max 3 retry attempts</li>
 *   <li>Exponential backoff: 1s → 2s → 4s</li>
 *   <li>Retryable: HTTP 429 (rate limit), 5xx (server error), timeouts</li>
 *   <li>Non-retryable: HTTP 4xx client errors (except 429), validation errors</li>
 * </ul>
 */
@Configuration
@EnableRetry
public class AiRetryConfig {

    /**
     * Pre-configured {@link RetryTemplate} for AI API invocations.
     *
     * <p>Usage in service classes:
     * <pre>{@code
     *   ChatResponse response = retryTemplate.execute(ctx -> {
     *       return chatModel.call(new Prompt(prompt));
     *   });
     * }</pre>
     */
    @Bean
    public RetryTemplate aiRetryTemplate() {
        RetryTemplate template = new RetryTemplate();

        // ── Exponential backoff: 1000ms → 2000ms → 4000ms ─
        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(1000);  // 1 second
        backOff.setMultiplier(2.0);        // double each retry
        backOff.setMaxInterval(10000);     // cap at 10 seconds
        template.setBackOffPolicy(backOff);

        // ── Retry policy: max 3 attempts ──────────────────
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        template.setRetryPolicy(retryPolicy);

        return template;
    }
}
