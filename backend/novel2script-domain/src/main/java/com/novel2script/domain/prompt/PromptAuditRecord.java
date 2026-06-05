package com.novel2script.domain.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Audit record for a single AI model invocation.
 * Corresponds to the {@code prompt_audits} database table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptAuditRecord {

    private Long id;
    private String promptName;
    private String promptVersion;
    private String modelName;
    private int inputTokens;
    private int outputTokens;
    private int latencyMs;
    private int retryCount;
    private boolean success;
    private String errorMessage;
    private String fullPrompt;
    private String fullResponse;
    private LocalDateTime createdAt;
}
