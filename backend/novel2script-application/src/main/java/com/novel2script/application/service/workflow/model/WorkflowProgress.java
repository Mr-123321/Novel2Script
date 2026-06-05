package com.novel2script.application.service.workflow.model;

import com.novel2script.common.enums.WorkflowStep;

import java.util.Map;

/**
 * Snapshot of a workflow execution's current progress, suitable for
 * polling by frontend clients and progress-bar rendering.
 *
 * @param executionId         unique identifier for this execution
 * @param workflowName        human-readable name of the workflow
 * @param currentStep         the step currently being executed (may be null)
 * @param overallProgress     percentage 0.0–100.0
 * @param stepStatuses        per-step status map
 * @param startedAt           ISO-8601 timestamp when execution began
 * @param estimatedCompletion ISO-8601 estimated finish timestamp
 * @param message             human-readable status message
 */
public record WorkflowProgress(
        String executionId,
        String workflowName,
        WorkflowStep currentStep,
        double overallProgress,
        Map<WorkflowStep, StepStatus> stepStatuses,
        String startedAt,
        String estimatedCompletion,
        String message
) {
}
