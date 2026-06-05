package com.novel2script.infrastructure.aop;

import com.novel2script.infrastructure.annotation.AiMonitored;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * AOP aspect that intercepts {@link AiMonitored} methods and writes
 * audit records to the {@code prompt_audits} table.
 *
 * <p>Records: model name, latency, success/failure, prompt/response size estimation,
 * and granular timing breakdown (method-level).
 *
 * <p><b>Note:</b> Token usage is estimated from character counts since
 * {@link org.springframework.ai.chat.model.ChatResponse} is consumed internally
 * by agent methods and not returned to this aspect. For accurate token counts,
 * agents should call {@link com.novel2script.infrastructure.prompt.PromptAuditService}
 * directly after receiving the ChatResponse.
 */
@Slf4j
@Aspect
@Component
public class AiMonitorAspect {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = """
            INSERT INTO prompt_audits
                (prompt_name, prompt_version, model_name,
                 input_tokens, output_tokens, latency_ms,
                 retry_count, is_success, error_message, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            """;

    public AiMonitorAspect(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Around("@annotation(aiMonitored)")
    public Object monitor(ProceedingJoinPoint joinPoint, AiMonitored aiMonitored) throws Throwable {
        String promptName = aiMonitored.value();
        String promptVersion = aiMonitored.version();
        String modelName = "unknown";
        String methodName = joinPoint.getSignature().toShortString();

        Instant start = Instant.now();
        boolean success = true;
        String errorMessage = null;
        int estimatedInputChars = 0;
        int estimatedOutputChars = 0;

        // ── Estimate input prompt size from method arguments ──
        estimatedInputChars = estimateInputSize(joinPoint);

        log.info("⏱️  AI call START [{}] {} — prompt est. {} chars",
                promptName, methodName, estimatedInputChars);

        try {
            Object result = joinPoint.proceed();

            // Estimate output size from result
            estimatedOutputChars = estimateOutputSize(result);

            long latencyMs = Duration.between(start, Instant.now()).toMillis();

            // Log granular timing
            log.info("⏱️  AI call END   [{}] {} — latency {}ms | prompt~{} chars → response~{} chars | ✅ SUCCESS",
                    promptName, methodName, latencyMs, estimatedInputChars, estimatedOutputChars);

            return result;
        } catch (Exception e) {
            success = false;
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (errorMessage.length() > 500) {
                errorMessage = errorMessage.substring(0, 500);
            }

            log.error("⏱️  AI call FAIL [{}] {} — latency {}ms | prompt~{} chars | ❌ {}",
                    promptName, methodName, latencyMs, estimatedInputChars, errorMessage);

            throw e; // re-throw — let retry / global handler deal with it
        } finally {
            long latencyMs = Duration.between(start, Instant.now()).toMillis();

            try {
                jdbcTemplate.update(INSERT_SQL,
                        promptName, promptVersion, modelName,
                        estimatedInputChars, estimatedOutputChars, latencyMs,
                        0, success ? 1 : 0, errorMessage);
            } catch (Exception dbEx) {
                // Audit failure must not break business flow
                log.warn("Failed to write AI audit record for '{}': {}", promptName, dbEx.getMessage());
            }
        }
    }

    // ── Size estimation helpers ────────────────────────────

    /**
     * Estimate the input prompt size by examining method arguments.
     * Looks for common types: String (prompt text), List (chapters/characters),
     * Map (template variables), Scene (scene data).
     */
    private int estimateInputSize(ProceedingJoinPoint joinPoint) {
        int total = 0;
        for (Object arg : joinPoint.getArgs()) {
            if (arg == null) continue;
            if (arg instanceof String s) {
                total += s.length();
            } else if (arg instanceof List<?> list) {
                for (Object item : list) {
                    total += estimateObjectSize(item);
                }
            } else if (arg instanceof Map<?, ?> map) {
                for (Object val : map.values()) {
                    if (val instanceof String s) total += s.length();
                    else if (val instanceof List<?> l) total += l.size() * 200;
                }
            } else {
                total += estimateObjectSize(arg);
            }
        }
        return total;
    }

    /**
     * Estimate the output size from the method return value.
     */
    private int estimateOutputSize(Object result) {
        if (result == null) return 0;
        if (result instanceof String s) return s.length();
        if (result instanceof List<?> list) {
            int total = 0;
            for (Object item : list) {
                total += estimateObjectSize(item);
            }
            return total;
        }
        if (result instanceof Map<?, ?> map) return map.size() * 500;
        // For domain objects, use toString length as rough estimate
        String str = result.toString();
        return Math.min(str.length(), 50000); // cap at 50K
    }

    private int estimateObjectSize(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof String s) return s.length();
        // Try common methods for getting content
        try {
            Method m = obj.getClass().getMethod("getContent");
            Object content = m.invoke(obj);
            if (content instanceof String s) return s.length();
        } catch (Exception ignored) {}
        try {
            Method m = obj.getClass().getMethod("getSummary");
            Object summary = m.invoke(obj);
            if (summary instanceof String s) return s.length();
        } catch (Exception ignored) {}
        return 200; // rough estimate for unknown objects
    }
}
