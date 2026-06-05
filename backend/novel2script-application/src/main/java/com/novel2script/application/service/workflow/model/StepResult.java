package com.novel2script.application.service.workflow.model;

/**
 * Immutable result produced by a single workflow step execution.
 *
 * @param success     whether the step completed without error
 * @param data        optional output data produced by the step
 * @param errorMessage description of the failure (null when success is true)
 * @param retryCount  number of retry attempts that were made
 * @param durationMs  wall-clock time the step took in milliseconds
 */
public record StepResult(
        boolean success,
        Object data,
        String errorMessage,
        int retryCount,
        long durationMs
) {

    /**
     * Convenience factory for a successful result.
     */
    public static StepResult success(Object data, long durationMs) {
        return new StepResult(true, data, null, 0, durationMs);
    }

    /**
     * Convenience factory for a successful result with a known retry count.
     */
    public static StepResult success(Object data, int retryCount, long durationMs) {
        return new StepResult(true, data, null, retryCount, durationMs);
    }

    /**
     * Convenience factory for a failed result.
     */
    public static StepResult failure(String errorMessage, int retryCount, long durationMs) {
        return new StepResult(false, null, errorMessage, retryCount, durationMs);
    }
}
