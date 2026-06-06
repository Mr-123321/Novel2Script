package com.novel2script.domain.model;

import com.novel2script.common.enums.ScriptStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Script aggregate root — the main output of the Novel2Script pipeline.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Script {

    private Long id;
    private Long novelId;
    private String title;
    private int version;
    private int sceneCount;
    private int characterCount;
    private int dialogueCount;
    private String yamlContent;
    private ScriptStatus status;
    private double progress;       // 0.00 - 100.00
    private Map<String, Object> workflowState;

    @Builder.Default
    private List<Character> characters = new ArrayList<>();

    @Builder.Default
    private List<Scene> scenes = new ArrayList<>();

    @Builder.Default
    private List<PlotEvent> plotEvents = new ArrayList<>();

    @Builder.Default
    private List<PlotInsertion> plotInsertions = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- Domain logic ---

    public void startGeneration() {
        this.status = ScriptStatus.GENERATING;
        this.progress = 0.0;
    }

    public void updateProgress(double progress) {
        this.progress = Math.min(100.0, Math.max(0.0, progress));
        if (this.progress >= 100.0) {
            this.status = ScriptStatus.COMPLETED;
        }
    }

    public void markFailed() {
        this.status = ScriptStatus.FAILED;
    }

    public boolean isGenerated() {
        return status == ScriptStatus.COMPLETED;
    }

    public boolean isInProgress() {
        return status == ScriptStatus.GENERATING;
    }
}
