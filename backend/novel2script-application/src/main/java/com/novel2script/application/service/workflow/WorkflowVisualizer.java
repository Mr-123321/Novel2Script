package com.novel2script.application.service.workflow;

import com.novel2script.application.service.workflow.model.Step;
import com.novel2script.application.service.workflow.model.StepStatus;
import com.novel2script.application.service.workflow.model.Workflow;
import com.novel2script.common.enums.WorkflowStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Produces human-readable visual representations of a workflow's structure
 * and execution progress: Mermaid diagrams, ASCII progress bars, and
 * plain-text status summaries.
 */
@Slf4j
@Service
public class WorkflowVisualizer {

    private static final int BAR_LENGTH = 40;

    // ==================================================================
    // Mermaid (flowchart / stateDiagram)
    // ==================================================================

    /**
     * Build a Mermaid flowchart description for the given workflow structure.
     */
    public String generateMermaid(Workflow workflow) {
        StringBuilder sb = new StringBuilder();
        sb.append("```mermaid\n");
        sb.append("flowchart TD\n");

        // Map each step to a short id
        Map<WorkflowStep, String> ids = workflow.getSteps().stream()
                .collect(Collectors.toMap(Step::getStepType, s -> s.getStepType().name()));

        for (Step step : workflow.getSteps()) {
            String id = ids.get(step.getStepType());
            String label = step.getStepType().getAgentName();
            sb.append("    ").append(id).append("[\"").append(label).append("\"]\n");

            for (WorkflowStep dep : step.getDependsOn()) {
                String depId = ids.get(dep);
                if (depId != null) {
                    sb.append("    ").append(depId).append(" --> ").append(id).append("\n");
                }
            }
        }

        sb.append("```\n");
        return sb.toString();
    }

    /**
     * Build a Mermaid state diagram from current execution statuses.
     */
    public String generateMermaid(Map<WorkflowStep, StepStatus> statuses) {
        StringBuilder sb = new StringBuilder();
        sb.append("```mermaid\n");
        sb.append("stateDiagram-v2\n");

        for (Map.Entry<WorkflowStep, StepStatus> entry : statuses.entrySet()) {
            String stateName = entry.getValue().name().toLowerCase();
            sb.append("    state \"").append(entry.getKey().getAgentName())
                    .append("\" as ").append(entry.getKey().name())
                    .append(" : ").append(stateName).append("\n");
        }

        sb.append("```\n");
        return sb.toString();
    }

    // ==================================================================
    // ASCII Progress Bar
    // ==================================================================

    /**
     * Render an ASCII progress bar: {@code [=====>    ] 45.0%}.
     */
    public String generateProgressBar(double percent) {
        int filled = (int) Math.round(BAR_LENGTH * percent / 100.0);
        int empty = BAR_LENGTH - filled;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < filled; i++) {
            sb.append('=');
        }
        if (filled > 0 && filled < BAR_LENGTH) {
            sb.append('>');
            empty--;
        }
        for (int i = 0; i < empty; i++) {
            sb.append(' ');
        }
        sb.append("] ");
        sb.append(String.format("%5.1f%%", percent));
        return sb.toString();
    }

    /**
     * Render a progress bar using the given step statuses.
     * Progress is computed as (COMPLETED steps / total steps) * 100.
     */
    public String generateProgressBar(Map<WorkflowStep, StepStatus> statuses) {
        long completed = statuses.values().stream()
                .filter(s -> s == StepStatus.COMPLETED)
                .count();
        double percent = statuses.isEmpty()
                ? 0.0
                : (completed * 100.0) / statuses.size();
        return generateProgressBar(percent);
    }

    // ==================================================================
    // Status text
    // ==================================================================

    /**
     * Produce a multi-line plain-text status summary.
     */
    public String generateStatusText(String workflowName,
                                     Map<WorkflowStep, StepStatus> statuses,
                                     double overallProgress,
                                     String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("Workflow: ").append(workflowName).append("\n");
        sb.append("Progress: ").append(generateProgressBar(overallProgress)).append("\n");
        sb.append("Message:  ").append(message != null ? message : "—").append("\n");
        sb.append("Steps:\n");

        List<WorkflowStep> ordered = statuses.keySet().stream()
                .sorted(java.util.Comparator.comparingInt(WorkflowStep::getOrder))
                .collect(Collectors.toList());

        for (WorkflowStep ws : ordered) {
            StepStatus st = statuses.get(ws);
            String icon = switch (st) {
                case PENDING   -> "○";
                case RUNNING   -> "●";
                case COMPLETED -> "✓";
                case FAILED    -> "✗";
                case SKIPPED   -> "⤳";
            };
            sb.append(String.format("  %s %s — %s%n", icon, ws.getAgentName(), st));
        }

        return sb.toString();
    }
}
