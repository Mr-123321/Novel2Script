package com.novel2script.infrastructure.aop;

import com.novel2script.infrastructure.annotation.AiMonitored;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;

/**
 * AOP aspect that intercepts {@link AiMonitored} methods and writes
 * audit records to the {@code prompt_audits} table.
 *
 * <p>Records: model name, token usage, latency, success/failure, retry count.
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

        Instant start = Instant.now();
        int retryCount = 0;
        boolean success = true;
        String errorMessage = null;
        int inputTokens = 0;
        int outputTokens = 0;

        try {
            Object result = joinPoint.proceed();

            // ── Extract token usage from ChatResponse ──────
            modelName = extractModelName(joinPoint, result);
            if (result instanceof ChatResponse chatResponse) {
                ChatResponseMetadata metadata = chatResponse.getMetadata();
                if (metadata != null && metadata.getUsage() != null) {
                    inputTokens = (int) metadata.getUsage().getPromptTokens();
                    outputTokens = (int) metadata.getUsage().getCompletionTokens();
                }
            }

            return result;
        } catch (Exception e) {
            success = false;
            errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (errorMessage.length() > 500) {
                errorMessage = errorMessage.substring(0, 500);
            }
            throw e; // re-throw — let retry / global handler deal with it
        } finally {
            long latencyMs = Duration.between(start, Instant.now()).toMillis();

            try {
                jdbcTemplate.update(INSERT_SQL,
                        promptName, promptVersion, modelName,
                        inputTokens, outputTokens, latencyMs,
                        retryCount, success ? 1 : 0, errorMessage);
            } catch (Exception dbEx) {
                // Audit failure must not break business flow
                log.warn("Failed to write AI audit record for '{}': {}", promptName, dbEx.getMessage());
            }

            log.debug("AI call [{}] model={} latency={}ms tokens={}/{} success={}",
                    promptName, modelName, latencyMs, inputTokens, outputTokens, success);
        }
    }

    // ── Helpers ───────────────────────────────────────────

    /**
     * Try to determine the model name from method arguments or the ChatResponse result.
     */
    private String extractModelName(ProceedingJoinPoint joinPoint, Object result) {
        // First try to find a ChatModel in the method args and read its toString()
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < paramTypes.length; i++) {
            if (args[i] != null && paramTypes[i].getName().contains("ChatModel")) {
                String s = args[i].toString();
                // Extract model name from toString (format varies by implementation)
                if (s.contains("model=")) {
                    return s.replaceAll(".*model=([^,\\)]+).*", "$1").trim();
                }
                return paramTypes[i].getSimpleName();
            }
        }

        // Fallback: try to get from ChatResponse metadata
        if (result instanceof ChatResponse chatResponse) {
            ChatResponseMetadata metadata = chatResponse.getMetadata();
            if (metadata != null && metadata.getModel() != null) {
                return metadata.getModel();
            }
        }

        return "unknown";
    }
}
