package com.novel2script.application.service.workflow.model;

import com.novel2script.common.enums.WorkflowStep;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * Definition of a single step within a workflow.
 * Describes what the step is, its dependencies, retry policy, and the action to run.
 */
@Data
@Builder
public class Step {

    /** The workflow step type this instance represents */
    private WorkflowStep stepType;

    /** Steps that must be completed before this step can run */
    @Builder.Default
    private List<WorkflowStep> dependsOn = Collections.emptyList();

    /** Strategy when the step fails: RETRY, SKIP, or ABORT */
    @Builder.Default
    private String onFailure = "RETRY";

    /** Maximum number of retry attempts (only meaningful when onFailure is RETRY) */
    @Builder.Default
    private int maxRetries = 3;

    /** The actual work to perform; injected via factory or builder */
    private Runnable action;

    /**
     * Static factory: creates a step with no dependencies that runs the given action.
     */
    public static Step of(WorkflowStep stepType, Runnable action) {
        return Step.builder()
                .stepType(stepType)
                .action(action)
                .build();
    }

    /**
     * Static factory: creates a step with one dependency that runs the given action.
     */
    public static Step of(WorkflowStep stepType, WorkflowStep dependsOn, Runnable action) {
        return Step.builder()
                .stepType(stepType)
                .dependsOn(List.of(dependsOn))
                .action(action)
                .build();
    }
}
