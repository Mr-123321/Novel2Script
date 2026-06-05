package com.novel2script.application.service.workflow.model;

/**
 * Status of an individual step within a workflow execution.
 */
public enum StepStatus {

    /** Step has not yet started */
    PENDING,

    /** Step is currently executing */
    RUNNING,

    /** Step completed successfully */
    COMPLETED,

    /** Step failed (may be retried if retryable) */
    FAILED,

    /** Step was skipped (e.g. due to a failed dependency) */
    SKIPPED
}
