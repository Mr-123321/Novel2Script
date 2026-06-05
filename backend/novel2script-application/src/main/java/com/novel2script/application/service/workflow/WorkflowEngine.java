package com.novel2script.application.service.workflow;

import com.novel2script.application.service.workflow.model.Step;
import com.novel2script.application.service.workflow.model.StepStatus;
import com.novel2script.application.service.workflow.model.Workflow;
import com.novel2script.application.service.workflow.model.WorkflowProgress;
import com.novel2script.common.enums.WorkflowStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Core workflow execution engine.
 * <p>
 * Drives a {@link Workflow} definition through its dependency graph,
 * executing independent steps in parallel, applying retry logic, and
 * broadcasting progress via {@link WorkflowStateManager}.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   CompletableFuture<String> execId = engine.execute(workflow);
 *   execId.thenAccept(id -> {
 *       WorkflowProgress p = engine.getProgress(id);
 *       ...
 *   });
 * }</pre>
 */
@Slf4j
@Service
public class WorkflowEngine {

    private final WorkflowStateManager stateManager;
    private final ExecutorService executor;

    // Cached references to allow resume (executionId → Workflow + current state)
    private final Map<String, Workflow> activeWorkflows = new java.util.concurrent.ConcurrentHashMap<>();

    public WorkflowEngine(WorkflowStateManager stateManager) {
        this.stateManager = stateManager;
        ThreadFactory tf = new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "workflow-" + count.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
        this.executor = Executors.newCachedThreadPool(tf);
    }

    // ==================================================================
    // Public API
    // ==================================================================

    /**
     * Execute a workflow synchronously (useful for testing).
     *
     * @param workflow the workflow definition to run
     * @return the execution id after the workflow has finished
     */
    public String executeSync(Workflow workflow) {
        String executionId = stateManager.init(workflow.getName(), workflow.getSteps().size());
        activeWorkflows.put(executionId, workflow);

        try {
            runLoopSync(executionId, workflow);
        } catch (Exception e) {
            stateManager.markFailed(executionId, e.getMessage());
            log.error("Workflow {} aborted", executionId, e);
        } finally {
            activeWorkflows.remove(executionId);
        }
        return executionId;
    }

    /**
     * Execute a workflow asynchronously (steps run in parallel via executor).
     *
     * @param workflow the workflow definition to run
     * @return a future that completes with the execution id once the
     *         entire workflow has finished (success or terminal failure)
     */
    public CompletableFuture<String> execute(Workflow workflow) {
        String executionId = stateManager.init(workflow.getName(), workflow.getSteps().size());
        activeWorkflows.put(executionId, workflow);
        return CompletableFuture.supplyAsync(() -> {
            try {
                runLoop(executionId, workflow);
            } catch (Exception e) {
                stateManager.markFailed(executionId, e.getMessage());
                log.error("Workflow {} aborted", executionId, e);
            } finally {
                activeWorkflows.remove(executionId);
            }
            return executionId;
        }, executor);
    }

    /**
     * Get the current progress snapshot for an execution.
     */
    public WorkflowProgress getProgress(String executionId) {
        return stateManager.getProgress(executionId);
    }

    /**
     * Attempt to resume a paused / partially-executed workflow.
     * Steps already marked COMPLETED or SKIPPED are skipped; remaining
     * steps are re-submitted through the dependency resolver.
     *
     * @param executionId the execution to resume
     * @return a future that completes when the resumed workflow finishes
     */
    public CompletableFuture<String> resume(String executionId) {
        Workflow workflow = activeWorkflows.get(executionId);
        if (workflow == null) {
            throw new IllegalArgumentException(
                    "Cannot resume execution '" + executionId + "': not found in active set. "
                            + "It may have already finished or been garbage-collected.");
        }

        Map<WorkflowStep, StepStatus> currentState = stateManager.getState(executionId);
        long completedOrSkipped = currentState.values().stream()
                .filter(s -> s == StepStatus.COMPLETED || s == StepStatus.SKIPPED)
                .count();
        if (completedOrSkipped == workflow.getSteps().size()) {
            return CompletableFuture.completedFuture(executionId); // already done
        }

        log.info("Resuming workflow {} ({} / {} steps done)",
                executionId, completedOrSkipped, workflow.getSteps().size());

        return CompletableFuture.supplyAsync(() -> {
            try {
                runLoopResume(executionId, workflow, currentState);
            } catch (Exception e) {
                stateManager.markFailed(executionId, e.getMessage());
                log.error("Resume of workflow {} aborted", executionId, e);
            } finally {
                activeWorkflows.remove(executionId);
            }
            return executionId;
        }, executor);
    }

    /**
     * Get a summary view for the WorkflowVisualizer.
     */
    public Map<WorkflowStep, StepStatus> getState(String executionId) {
        return stateManager.getState(executionId);
    }

    // ==================================================================
    // Execution loop
    // ==================================================================

    private void runLoop(String executionId, Workflow workflow) {
        runLoopInternal(executionId, workflow, true);
    }

    /**
     * Synchronous execution loop — steps run inline on the calling thread.
     */
    private void runLoopSync(String executionId, Workflow workflow) {
        runLoopInternal(executionId, workflow, false);
    }

    private void runLoopInternal(String executionId, Workflow workflow, boolean async) {
        int totalSteps = workflow.getSteps().size();
        Map<WorkflowStep, StepStatus> statusMap = new EnumMap<>(WorkflowStep.class);

        while (true) {
            // Phase 1 — find runnable steps
            List<Step> runnable = findRunnableSteps(workflow, statusMap);
            if (runnable.isEmpty()) {
                break; // no more work
            }

            // Phase 2 — execute steps
            if (async && runnable.size() > 1) {
                // Execute in parallel via executor
                List<CompletableFuture<Void>> futures = runnable.stream()
                        .map(step -> CompletableFuture.runAsync(
                                () -> executeStep(executionId, step, statusMap), executor))
                        .collect(Collectors.toList());
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } else {
                // Execute inline — avoids thread visibility issues
                for (Step step : runnable) {
                    executeStep(executionId, step, statusMap);
                }
            }

            // Phase 3 — update progress
            long done = statusMap.values().stream()
                    .filter(s -> s == StepStatus.COMPLETED || s == StepStatus.SKIPPED)
                    .count();
            double pct = (done * 100.0) / totalSteps;
            stateManager.updateProgress(executionId, pct, done + " / " + totalSteps + " steps done");

            // Phase 4 — check for terminal failure
            boolean anyAbort = statusMap.values().stream().anyMatch(s -> s == StepStatus.FAILED);
            if (anyAbort) {
                // Determine if the failure is terminal (an ABORT step or exceeded retries)
                boolean terminal = runnable.stream().anyMatch(step -> "ABORT".equals(step.getOnFailure()));
                if (terminal) {
                    stateManager.markFailed(executionId, "Terminal step failure");
                    return;
                }
            }

            // Check if all steps are done (COMPLETED or SKIPPED)
            boolean allDone = statusMap.values().stream()
                    .allMatch(s -> s == StepStatus.COMPLETED || s == StepStatus.SKIPPED);
            if (allDone) {
                break;
            }
        }

        stateManager.markCompleted(executionId);
    }

    /**
     * Resume variant — reads pre-existing statusMap and only runs remaining steps.
     */
    private void runLoopResume(String executionId, Workflow workflow,
                               Map<WorkflowStep, StepStatus> statusMap) {
        int totalSteps = workflow.getSteps().size();

        while (true) {
            List<Step> runnable = findRunnableSteps(workflow, statusMap);
            if (runnable.isEmpty()) {
                break;
            }

            List<CompletableFuture<Void>> futures = runnable.stream()
                    .map(step -> CompletableFuture.runAsync(() -> executeStep(executionId, step, statusMap), executor))
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            long done = statusMap.values().stream()
                    .filter(s -> s == StepStatus.COMPLETED || s == StepStatus.SKIPPED)
                    .count();
            stateManager.updateProgress(executionId, (done * 100.0) / totalSteps,
                    done + " / " + totalSteps + " steps done");

            boolean anyAbort = statusMap.values().stream().anyMatch(s -> s == StepStatus.FAILED);
            if (anyAbort) {
                stateManager.markFailed(executionId, "Terminal step failure");
                return;
            }

            boolean allDone = statusMap.values().stream()
                    .allMatch(s -> s == StepStatus.COMPLETED || s == StepStatus.SKIPPED);
            if (allDone) {
                break;
            }
        }

        stateManager.markCompleted(executionId);
    }

    // ==================================================================
    // Step execution
    // ==================================================================

    private void executeStep(String executionId, Step step,
                             Map<WorkflowStep, StepStatus> statusMap) {
        WorkflowStep type = step.getStepType();

        // Check if dependencies are healthy — skip if a dep failed
        for (WorkflowStep dep : step.getDependsOn()) {
            StepStatus depStatus = statusMap.getOrDefault(dep, StepStatus.PENDING);
            if (depStatus == StepStatus.FAILED) {
                stateManager.markStepSkipped(executionId, type, "Dependency " + dep.getAgentName() + " failed");
                statusMap.put(type, StepStatus.SKIPPED);
                return;
            }
        }

        stateManager.markStepRunning(executionId, type);
        int attempt = 0;

        while (attempt <= step.getMaxRetries()) {
            long start = System.currentTimeMillis();
            try {
                step.getAction().run();
                long elapsed = System.currentTimeMillis() - start;
                log.debug("{} completed in {} ms (attempt {})", type, elapsed, attempt + 1);
                stateManager.markStepCompleted(executionId, type);
                statusMap.put(type, StepStatus.COMPLETED);
                return;
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                attempt++;
                if (attempt > step.getMaxRetries()) {
                    // Exhausted retries
                    String msg = "Failed after " + attempt + " attempts: " + e.getMessage();
                    log.warn("{} {}", type, msg);
                    stateManager.markStepFailed(executionId, type, msg);
                    statusMap.put(type, StepStatus.FAILED);

                    if ("SKIP".equals(step.getOnFailure())) {
                        statusMap.put(type, StepStatus.SKIPPED); // downgrade
                        stateManager.markStepSkipped(executionId, type, msg);
                    }
                    // ABORT / RETRY (exhausted) → keep FAILED
                    return;
                }

                // Retry
                long delayMs = (long) Math.pow(2, attempt) * 100; // 200, 400, 800 ms
                log.info("{} attempt {} failed ({} ms), retrying in {} ms: {}",
                        type, attempt, elapsed, delayMs, e.getMessage());
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    stateManager.markStepFailed(executionId, type, "Interrupted during retry");
                    statusMap.put(type, StepStatus.FAILED);
                    return;
                }
            }
        }
    }

    // ==================================================================
    // Dependency resolution
    // ==================================================================

    /**
     * Find steps whose dependencies are all satisfied (COMPLETED) and
     * which themselves are still PENDING.
     */
    private List<Step> findRunnableSteps(Workflow workflow,
                                         Map<WorkflowStep, StepStatus> statusMap) {
        Set<WorkflowStep> completedOrSkipped = statusMap.entrySet().stream()
                .filter(e -> e.getValue() == StepStatus.COMPLETED || e.getValue() == StepStatus.SKIPPED)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        List<Step> runnable = new ArrayList<>();
        for (Step step : workflow.getSteps()) {
            WorkflowStep type = step.getStepType();
            StepStatus current = statusMap.getOrDefault(type, StepStatus.PENDING);
            if (current != StepStatus.PENDING) {
                continue; // already running / done / failed
            }

            boolean depsSatisfied = step.getDependsOn().isEmpty()
                    || completedOrSkipped.containsAll(step.getDependsOn());
            if (depsSatisfied) {
                runnable.add(step);
            }
        }
        return runnable;
    }
}
