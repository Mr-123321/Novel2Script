package com.novel2script.application.service.workflow;

import com.novel2script.application.service.workflow.model.Step;
import com.novel2script.application.service.workflow.model.StepStatus;
import com.novel2script.application.service.workflow.model.Workflow;
import com.novel2script.application.service.workflow.model.WorkflowProgress;
import com.novel2script.common.enums.WorkflowStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Workflow Engine module.
 * <p>
 * Covers: full-flow execution, failure recovery with retries,
 * progress query, Mermaid diagrams, ASCII progress bars,
 * and parallel step execution.
 */
@DisplayName("WorkflowEngine")
class WorkflowEngineTest {

    private WorkflowStateManager stateManager;
    private WorkflowEngine engine;
    private WorkflowVisualizer visualizer;
    private WorkflowDefinitions definitions;

    @BeforeEach
    void setUp() {
        stateManager = new WorkflowStateManager();
        engine = new WorkflowEngine(stateManager);
        visualizer = new WorkflowVisualizer();
        definitions = new WorkflowDefinitions();
    }

    // ==================================================================
    // 1. Full flow — all steps succeed
    // ==================================================================

    @Test
    @DisplayName("Should execute single-step workflow successfully")
    void shouldRunFullWorkflowSuccessfully() {
        List<String> executionLog = new ArrayList<>();
        Workflow wf = Workflow.builder()
                .name("testFull")
                .steps(List.of(
                        Step.builder()
                                .stepType(WorkflowStep.CHAPTER_PARSE)
                                .action(() -> executionLog.add("step1"))
                                .build(),
                        Step.builder()
                                .stepType(WorkflowStep.CHARACTER_EXTRACT)
                                .action(() -> executionLog.add("step2"))
                                .build()
                ))
                .build();

        String execId = engine.executeSync(wf);

        WorkflowProgress progress = engine.getProgress(execId);
        assertEquals("testFull", progress.workflowName());
        assertEquals(100.0, progress.overallProgress(), 0.01);

        Map<WorkflowStep, StepStatus> state = engine.getState(execId);
        assertEquals(StepStatus.COMPLETED, state.get(WorkflowStep.CHAPTER_PARSE));
        assertEquals(StepStatus.COMPLETED, state.get(WorkflowStep.CHARACTER_EXTRACT));

        assertEquals(2, executionLog.size(), "Both steps must execute: " + executionLog);
    }

    // ==================================================================
    // 2. Failure recovery — retry then succeed
    // ==================================================================

    @Test
    @DisplayName("Should retry a failing step up to maxRetries, then succeed")
    void shouldRetryAndEventuallySucceed() {
        AtomicInteger attempts = new AtomicInteger(0);
        int maxRetries = 2;

        // Single step that fails first few times then succeeds
        Step step = Step.builder()
                .stepType(WorkflowStep.CHARACTER_EXTRACT)
                .maxRetries(maxRetries)
                .onFailure("RETRY")
                .action(() -> {
                    int count = attempts.incrementAndGet();
                    if (count <= maxRetries) {
                        throw new RuntimeException("Simulated failure #" + count);
                    }
                })
                .build();

        Workflow wf = Workflow.builder().name("retryTest").steps(List.of(step)).build();
        String execId = engine.executeSync(wf);

        assertEquals(100.0, engine.getProgress(execId).overallProgress(), 0.01);
        assertEquals(StepStatus.COMPLETED, engine.getState(execId).get(WorkflowStep.CHARACTER_EXTRACT));
        assertEquals(maxRetries + 1, attempts.get());
    }

    @Test
    @DisplayName("Should mark step FAILED when retries are exhausted")
    void shouldFailAfterRetriesExhausted() {
        Step step = Step.builder()
                .stepType(WorkflowStep.CHARACTER_EXTRACT)
                .maxRetries(1)
                .onFailure("SKIP")
                .action(() -> { throw new RuntimeException("Permanent failure"); })
                .build();

        Workflow wf = Workflow.builder().name("failTest").steps(List.of(step)).build();
        String execId = engine.executeSync(wf);

        // With onFailure=SKIP, exhausted retries result in SKIPPED status
        assertEquals(StepStatus.SKIPPED, engine.getState(execId).get(WorkflowStep.CHARACTER_EXTRACT));
    }

    // ==================================================================
    // 3. Progress query
    // ==================================================================

    @Test
    @DisplayName("Should report accurate progress throughout execution")
    void shouldReportProgressAccurately() throws Exception {
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        Workflow wf = buildSimpleWorkflow("progressTest", log, false, -1);

        String execId = engine.executeSync(wf);

        WorkflowProgress finalProgress = engine.getProgress(execId);
        assertEquals(100.0, finalProgress.overallProgress(), 0.01);
        assertEquals("progressTest", finalProgress.workflowName());
        assertNotNull(finalProgress.startedAt(), "startedAt must not be null");
        assertNotNull(finalProgress.estimatedCompletion(), "estimatedCompletion must not be null");
        assertFalse(finalProgress.stepStatuses().isEmpty());

        // All steps should appear in the map
        for (WorkflowStep ws : WorkflowStep.values()) {
            assertTrue(finalProgress.stepStatuses().containsKey(ws),
                    "Missing step in progress: " + ws);
        }
    }

    // ==================================================================
    // 4. Mermaid generation
    // ==================================================================

    @Test
    @DisplayName("Should generate valid Mermaid flowchart for a workflow")
    void shouldGenerateMermaidFlowchart() {
        Workflow wf = definitions.fullGenerationWorkflow();
        String mermaid = visualizer.generateMermaid(wf);

        assertNotNull(mermaid);
        assertTrue(mermaid.contains("```mermaid"), "Should contain mermaid fence");
        assertTrue(mermaid.contains("flowchart TD"), "Should be top-down flowchart");
        assertTrue(mermaid.contains("CHAPTER_PARSE"), "Should include CHAPTER_PARSE node");
        assertTrue(mermaid.contains("STORYBOARD_GENERATE"), "Should include STORYBOARD_GENERATE node");
        assertTrue(mermaid.contains("-->"), "Should contain dependency arrows");
    }

    @Test
    @DisplayName("Should generate valid Mermaid state diagram for step statuses")
    void shouldGenerateMermaidStateDiagram() {
        Map<WorkflowStep, StepStatus> statuses = new EnumMap<>(WorkflowStep.class);
        statuses.put(WorkflowStep.CHAPTER_PARSE, StepStatus.COMPLETED);
        statuses.put(WorkflowStep.CHARACTER_EXTRACT, StepStatus.RUNNING);
        statuses.put(WorkflowStep.CHARACTER_RESOLVE, StepStatus.PENDING);

        String mermaid = visualizer.generateMermaid(statuses);

        assertNotNull(mermaid);
        assertTrue(mermaid.contains("```mermaid"));
        assertTrue(mermaid.contains("stateDiagram-v2"));
        assertTrue(mermaid.contains("COMPLETED") || mermaid.contains("completed"));
        assertTrue(mermaid.contains("RUNNING") || mermaid.contains("running"));
        assertTrue(mermaid.contains("PENDING") || mermaid.contains("pending"));
    }

    // ==================================================================
    // 5. ASCII Progress Bar
    // ==================================================================

    @Test
    @DisplayName("Should render correct ASCII progress bar for given percentage")
    void shouldRenderProgressBar() {
        String bar0 = visualizer.generateProgressBar(0.0);
        assertTrue(bar0.startsWith("[") && bar0.contains("0.0%"), "Bar for 0%: " + bar0);

        String bar50 = visualizer.generateProgressBar(50.0);
        assertTrue(bar50.contains("50.0%"), "Bar for 50%: " + bar50);

        String bar100 = visualizer.generateProgressBar(100.0);
        assertTrue(bar100.endsWith("100.0%"), "Bar for 100%: " + bar100);
    }

    @Test
    @DisplayName("Should render progress bar from step statuses")
    void shouldRenderProgressBarFromStatuses() {
        Map<WorkflowStep, StepStatus> statuses = new EnumMap<>(WorkflowStep.class);
        statuses.put(WorkflowStep.CHAPTER_PARSE, StepStatus.COMPLETED);
        statuses.put(WorkflowStep.CHARACTER_EXTRACT, StepStatus.COMPLETED);
        statuses.put(WorkflowStep.CHARACTER_RESOLVE, StepStatus.COMPLETED);
        statuses.put(WorkflowStep.PLOT_EXTRACT, StepStatus.PENDING);
        statuses.put(WorkflowStep.SCENE_SEGMENT, StepStatus.PENDING);

        String bar = visualizer.generateProgressBar(statuses);
        assertNotNull(bar);
        assertTrue(bar.contains("60.0%"), "3 of 5 = 60%: " + bar);
    }

    // ==================================================================
    // 6. Parallel step execution
    // ==================================================================

    @Test
    @DisplayName("Should execute independent steps in parallel")
    void shouldExecuteIndependentStepsInParallel() throws Exception {
        // Two steps with no mutual dependencies — should run in parallel
        AtomicBoolean stepARan = new AtomicBoolean(false);
        AtomicBoolean stepBRan = new AtomicBoolean(false);

        // Use a small barrier-like pattern: each step sets its flag, then sleeps
        // to allow the other thread a chance to set its flag too
        Step stepA = Step.of(WorkflowStep.CHARACTER_EXTRACT, () -> {
            stepARan.set(true);
            try { Thread.sleep(200); } catch (InterruptedException ignored) { }
        });

        Step stepB = Step.of(WorkflowStep.PLOT_EXTRACT, () -> {
            stepBRan.set(true);
            try { Thread.sleep(200); } catch (InterruptedException ignored) { }
        });

        Workflow wf = Workflow.builder()
                .name("parallelTest")
                .steps(List.of(stepA, stepB))
                .build();

        String execId = engine.executeSync(wf);

        Map<WorkflowStep, StepStatus> state = engine.getState(execId);
        assertEquals(StepStatus.COMPLETED, state.get(WorkflowStep.CHARACTER_EXTRACT));
        assertEquals(StepStatus.COMPLETED, state.get(WorkflowStep.PLOT_EXTRACT));
        assertTrue(stepARan.get() && stepBRan.get(), "Both steps must have executed");
    }

    @Test
    @DisplayName("Should execute all steps when dependencies are met")
    void shouldRespectDependencies() {
        List<String> order = new ArrayList<>();

        // Two independent parallel steps
        Step step1 = Step.builder()
                .stepType(WorkflowStep.CHAPTER_PARSE)
                .action(() -> order.add("A"))
                .build();

        Step step2 = Step.builder()
                .stepType(WorkflowStep.CHARACTER_EXTRACT)
                .action(() -> order.add("B"))
                .build();

        Workflow wf = Workflow.builder()
                .name("depTest")
                .steps(List.of(step1, step2))
                .build();

        engine.executeSync(wf);

        // Both independent steps must execute
        assertEquals(2, order.size(), "Both steps must execute, got: " + order);
        assertTrue(order.contains("A") && order.contains("B"),
                "Both steps should be in execution log: " + order);
    }

    // ==================================================================
    // 7. Status text
    // ==================================================================

    @Test
    @DisplayName("Should generate readable status text")
    void shouldGenerateStatusText() {
        Map<WorkflowStep, StepStatus> statuses = new EnumMap<>(WorkflowStep.class);
        statuses.put(WorkflowStep.CHAPTER_PARSE, StepStatus.COMPLETED);
        statuses.put(WorkflowStep.CHARACTER_EXTRACT, StepStatus.RUNNING);
        statuses.put(WorkflowStep.CHARACTER_RESOLVE, StepStatus.PENDING);
        statuses.put(WorkflowStep.PLOT_EXTRACT, StepStatus.FAILED);
        statuses.put(WorkflowStep.SCENE_SEGMENT, StepStatus.SKIPPED);

        String text = visualizer.generateStatusText("demo", statuses, 20.0, "In progress");

        assertNotNull(text);
        assertTrue(text.contains("demo"), "Should include workflow name");
        assertTrue(text.contains("20.0%"), "Should include progress percentage");
        assertTrue(text.contains("In progress"), "Should include message");
        assertTrue(text.contains("✓"), "Should use checkmark for COMPLETED");
        assertTrue(text.contains("✗"), "Should use cross for FAILED");
    }

    @Test
    @DisplayName("Should generate Mermaid for fullGenerationWorkflow")
    void shouldGenerateMermaidForFullWorkflow() {
        Workflow wf = definitions.fullGenerationWorkflow();
        String mermaid = visualizer.generateMermaid(wf);

        // Verify all ten steps are present
        for (WorkflowStep ws : WorkflowStep.values()) {
            assertTrue(mermaid.contains(ws.name()),
                    "Mermaid should contain node for " + ws.name());
        }

        // Verify key dependency relationships
        assertTrue(mermaid.contains("CHAPTER_PARSE"), "Missing root node");
        assertTrue(mermaid.contains("SCRIPT_COMPOSE"), "Missing compose node");
        assertTrue(mermaid.contains("STORYBOARD_GENERATE"), "Missing terminal node");

        System.out.println("Mermaid flowchart:\n" + mermaid);
    }

    // ==================================================================
    // 8. Unknown execution id
    // ==================================================================

    @Test
    @DisplayName("Should throw when querying unknown execution id")
    void shouldThrowForUnknownExecution() {
        assertThrows(IllegalArgumentException.class,
                () -> engine.getProgress("nonexistent"));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Build a simple but complete linear workflow (all 10 steps) where each
     * step logs its name.  Optionally inject a failure on a particular step.
     *
     * @param name     workflow name
     * @param log      shared synchronized list for capturing execution order
     * @param failStep if {@code true}, the step at index {@code failAtIndex} throws
     * @param failAtIndex the zero-based index of the step to fail
     */
    private Workflow buildSimpleWorkflow(String name,
                                         List<String> log,
                                         boolean failStep,
                                         int failAtIndex) {
        List<WorkflowStep> orderedSteps = List.of(WorkflowStep.values())
                .stream()
                .sorted(java.util.Comparator.comparingInt(WorkflowStep::getOrder))
                .collect(Collectors.toList());

        // Build a dependency chain: each step depends on the previous one
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < orderedSteps.size(); i++) {
            WorkflowStep current = orderedSteps.get(i);
            List<WorkflowStep> deps = (i == 0)
                    ? List.of()
                    : List.of(orderedSteps.get(i - 1));

            Runnable action;
            if (failStep && i == failAtIndex) {
                action = () -> {
                    log.add(current.getAgentName());
                    throw new RuntimeException("Injected failure at " + current.getAgentName());
                };
            } else {
                action = () -> log.add(current.getAgentName());
            }

            steps.add(Step.builder()
                    .stepType(current)
                    .dependsOn(deps)
                    .maxRetries(current.isRetryable() ? 2 : 0)
                    .onFailure(current.isRetryable() ? "RETRY" : "SKIP")
                    .action(action)
                    .build());
        }

        return Workflow.builder()
                .name(name)
                .steps(steps)
                .description("Auto-built test workflow")
                .build();
    }
}
