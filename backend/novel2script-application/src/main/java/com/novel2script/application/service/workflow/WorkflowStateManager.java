package com.novel2script.application.service.workflow;

import com.novel2script.application.service.workflow.model.StepStatus;
import com.novel2script.application.service.workflow.model.WorkflowProgress;
import com.novel2script.common.enums.WorkflowStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory state manager for workflow executions.
 * <p>
 * Uses {@link ConcurrentHashMap} so that state reads and writes are
 * safe across the async threads managed by {@link WorkflowEngine}.
 * Each execution is keyed by a unique execution id.
 */
@Slf4j
@Service
public class WorkflowStateManager {

    /**
     * Internal record capturing the mutable per-execution state.
     */
    private static class ExecutionState {
        String workflowName;
        WorkflowStep currentStep;
        double overallProgress;
        Map<WorkflowStep, StepStatus> stepStatuses = new EnumMap<>(WorkflowStep.class);
        String startedAt;
        String estimatedCompletion;
        String message;
        boolean terminal;   // true when completed or failed

        ExecutionState(String workflowName) {
            this.workflowName = workflowName;
            this.overallProgress = 0.0;
            this.message = "Initialised";
            for (WorkflowStep ws : WorkflowStep.values()) {
                this.stepStatuses.put(ws, StepStatus.PENDING);
            }
        }
    }

    private final ConcurrentHashMap<String, ExecutionState> states = new ConcurrentHashMap<>();

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private String now() {
        return LocalDateTime.now().format(ISO);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Register a new execution and return its unique id.
     */
    public String init(String workflowName, int totalSteps) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        ExecutionState state = new ExecutionState(workflowName);
        state.startedAt = now();
        state.message = "Workflow started";
        states.put(id, state);
        log.info("WorkflowStateManager init: id={}, workflow={}, steps={}", id, workflowName, totalSteps);
        return id;
    }

    // ------------------------------------------------------------------
    // Step transitions
    // ------------------------------------------------------------------

    public void markStepRunning(String executionId, WorkflowStep step) {
        ExecutionState s = require(executionId);
        s.currentStep = step;
        s.stepStatuses.put(step, StepStatus.RUNNING);
        s.message = "Running " + step.getAgentName();
        log.debug("{} -> RUNNING  ({})", step, executionId);
    }

    public void markStepCompleted(String executionId, WorkflowStep step) {
        ExecutionState s = require(executionId);
        s.stepStatuses.put(step, StepStatus.COMPLETED);
        s.message = step.getAgentName() + " completed";
        log.debug("{} -> COMPLETED ({})", step, executionId);
    }

    public void markStepFailed(String executionId, WorkflowStep step, String error) {
        ExecutionState s = require(executionId);
        s.stepStatuses.put(step, StepStatus.FAILED);
        s.message = step.getAgentName() + " failed: " + error;
        log.warn("{} -> FAILED ({}) — {}", step, executionId, error);
    }

    public void markStepSkipped(String executionId, WorkflowStep step, String reason) {
        ExecutionState s = require(executionId);
        s.stepStatuses.put(step, StepStatus.SKIPPED);
        s.message = step.getAgentName() + " skipped: " + reason;
        log.info("{} -> SKIPPED ({}) — {}", step, executionId, reason);
    }

    // ------------------------------------------------------------------
    // Progress
    // ------------------------------------------------------------------

    public void updateProgress(String executionId, double percent, String message) {
        ExecutionState s = require(executionId);
        s.overallProgress = Math.min(100.0, Math.max(0.0, percent));
        if (message != null) {
            s.message = message;
        }
    }

    // ------------------------------------------------------------------
    // Terminal states
    // ------------------------------------------------------------------

    public void markCompleted(String executionId) {
        ExecutionState s = require(executionId);
        s.overallProgress = 100.0;
        s.currentStep = null;
        s.terminal = true;
        s.message = "Workflow completed successfully";
        s.estimatedCompletion = now();
        log.info("Workflow {} COMPLETED", executionId);
    }

    public void markFailed(String executionId, String error) {
        ExecutionState s = require(executionId);
        s.terminal = true;
        s.message = "Workflow failed: " + error;
        s.estimatedCompletion = now();
        log.error("Workflow {} FAILED — {}", executionId, error);
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public WorkflowProgress getProgress(String executionId) {
        ExecutionState s = require(executionId);
        return new WorkflowProgress(
                executionId,
                s.workflowName,
                s.currentStep,
                s.overallProgress,
                Collections.unmodifiableMap(new EnumMap<>(s.stepStatuses)),
                s.startedAt,
                s.estimatedCompletion,
                s.message
        );
    }

    public Map<WorkflowStep, StepStatus> getState(String executionId) {
        ExecutionState s = require(executionId);
        return Collections.unmodifiableMap(new EnumMap<>(s.stepStatuses));
    }

    /**
     * Internal lookup — throws if the execution id is unknown.
     */
    private ExecutionState require(String executionId) {
        ExecutionState s = states.get(executionId);
        if (s == null) {
            throw new IllegalArgumentException("Unknown execution id: " + executionId);
        }
        return s;
    }
}
