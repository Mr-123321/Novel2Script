package com.novel2script.application.service.workflow;

import com.novel2script.application.service.workflow.model.Step;
import com.novel2script.application.service.workflow.model.Workflow;
import com.novel2script.common.enums.WorkflowStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pre-built workflow definitions.
 * <p>
 * Each factory method returns a fully-configured {@link Workflow} graph
 * that the {@link WorkflowEngine} can execute.
 */
@Slf4j
@Component
public class WorkflowDefinitions {

    /**
     * The full 10-step script-generation workflow with all dependencies.
     *
     * <pre>
     *   CHAPTER_PARSE
     *    ├── CHARACTER_EXTRACT
     *    │    └── CHARACTER_RESOLVE
     *    ├── PLOT_EXTRACT
     *    │    └── SCENE_SEGMENT
     *    │         ├── DIALOGUE_GENERATE
     *    │         └── ACTION_GENERATE
     *    ├── SCRIPT_COMPOSE   (waits for all parallel branches)
     *    └── YAML_EXPORT → STORYBOARD_GENERATE
     * </pre>
     */
    public Workflow fullGenerationWorkflow() {
        return Workflow.builder()
                .name("fullGeneration")
                .description("Full pipeline: novel → parsed chapters → characters → plot → scenes → dialogue/action → composed script → YAML export → storyboard")
                .steps(List.of(
                        step(WF.CHAPTER_PARSE),

                        step(WF.CHARACTER_EXTRACT, WF.CHAPTER_PARSE),
                        step(WF.CHARACTER_RESOLVE, WF.CHARACTER_EXTRACT),

                        step(WF.PLOT_EXTRACT, WF.CHAPTER_PARSE),
                        step(WF.SCENE_SEGMENT, WF.PLOT_EXTRACT),

                        step(WF.DIALOGUE_GENERATE, WF.SCENE_SEGMENT),
                        step(WF.ACTION_GENERATE, WF.SCENE_SEGMENT),

                        step(WF.SCRIPT_COMPOSE, WF.CHARACTER_RESOLVE, WF.DIALOGUE_GENERATE, WF.ACTION_GENERATE),

                        step(WF.YAML_EXPORT, WF.SCRIPT_COMPOSE),
                        step(WF.STORYBOARD_GENERATE, WF.YAML_EXPORT)
                ))
                .build();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Alias for cleaner in-line references. */
    private static final class WF {
        static final WorkflowStep CHAPTER_PARSE       = WorkflowStep.CHAPTER_PARSE;
        static final WorkflowStep CHARACTER_EXTRACT   = WorkflowStep.CHARACTER_EXTRACT;
        static final WorkflowStep CHARACTER_RESOLVE   = WorkflowStep.CHARACTER_RESOLVE;
        static final WorkflowStep PLOT_EXTRACT        = WorkflowStep.PLOT_EXTRACT;
        static final WorkflowStep SCENE_SEGMENT       = WorkflowStep.SCENE_SEGMENT;
        static final WorkflowStep DIALOGUE_GENERATE   = WorkflowStep.DIALOGUE_GENERATE;
        static final WorkflowStep ACTION_GENERATE     = WorkflowStep.ACTION_GENERATE;
        static final WorkflowStep SCRIPT_COMPOSE      = WorkflowStep.SCRIPT_COMPOSE;
        static final WorkflowStep YAML_EXPORT         = WorkflowStep.YAML_EXPORT;
        static final WorkflowStep STORYBOARD_GENERATE = WorkflowStep.STORYBOARD_GENERATE;
    }

    private static Step step(WorkflowStep type) {
        return Step.builder()
                .stepType(type)
                .dependsOn(List.of())
                .maxRetries(type.isRetryable() ? 3 : 0)
                .onFailure(type.isRetryable() ? "RETRY" : "ABORT")
                .action(() -> log.info("[NO-OP] {}  placeholder — inject real agent call", type.getAgentName()))
                .build();
    }

    private static Step step(WorkflowStep type, WorkflowStep dep) {
        return Step.builder()
                .stepType(type)
                .dependsOn(List.of(dep))
                .maxRetries(type.isRetryable() ? 3 : 0)
                .onFailure(type.isRetryable() ? "RETRY" : "ABORT")
                .action(() -> log.info("[NO-OP] {}  placeholder — inject real agent call", type.getAgentName()))
                .build();
    }

    private static Step step(WorkflowStep type, WorkflowStep dep1, WorkflowStep dep2, WorkflowStep dep3) {
        return Step.builder()
                .stepType(type)
                .dependsOn(List.of(dep1, dep2, dep3))
                .maxRetries(type.isRetryable() ? 3 : 0)
                .onFailure(type.isRetryable() ? "RETRY" : "ABORT")
                .action(() -> log.info("[NO-OP] {}  placeholder — inject real agent call", type.getAgentName()))
                .build();
    }
}
