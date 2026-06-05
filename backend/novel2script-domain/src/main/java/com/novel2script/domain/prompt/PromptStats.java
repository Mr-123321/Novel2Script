package com.novel2script.domain.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated statistics for a prompt template over a time window.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptStats {

    private String promptName;

    /** Total invocations in the window. */
    private long totalCalls;

    /** Successful calls count. */
    private long successCount;

    /** Failed calls count. */
    private long failureCount;

    /** Average latency in milliseconds. */
    private double avgLatencyMs;

    /** Average input tokens per call. */
    private double avgInputTokens;

    /** Average output tokens per call. */
    private double avgOutputTokens;

    /** Total tokens consumed (input + output). */
    private long totalTokens;

    /** Success rate as percentage (0.0 - 100.0). */
    public double getSuccessRate() {
        return totalCalls > 0 ? (double) successCount / totalCalls * 100.0 : 0.0;
    }

    /** Average total tokens per call. */
    public double getAvgTotalTokens() {
        return totalCalls > 0 ? (double) totalTokens / totalCalls : 0.0;
    }
}
