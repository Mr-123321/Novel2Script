package com.novel2script.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
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
@TableName(value = "scripts", autoResultMap = true)
public class Script {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("novel_id")
    private Long novelId;

    private String title;
    private int version;

    @TableField("scene_count")
    private int sceneCount;

    @TableField("character_count")
    private int characterCount;

    @TableField("dialogue_count")
    private int dialogueCount;

    @TableField("yaml_content")
    private String yamlContent;

    private ScriptStatus status;
    private double progress;       // 0.00 - 100.00

    @TableField(value = "workflow_state", typeHandler = JacksonTypeHandler.class)
    @Builder.Default
    private Map<String, Object> workflowState = new LinkedHashMap<>();

    /** Characters are stored in their own table */
    @TableField(exist = false)
    @Builder.Default
    private List<Character> characters = new ArrayList<>();

    /** Scenes are stored in their own table */
    @TableField(exist = false)
    @Builder.Default
    private List<Scene> scenes = new ArrayList<>();

    /** Plot events are stored in their own table */
    @TableField(exist = false)
    @Builder.Default
    private List<PlotEvent> plotEvents = new ArrayList<>();

    /** Plot insertions are stored in their own table */
    @TableField(exist = false)
    @Builder.Default
    private List<PlotInsertion> plotInsertions = new ArrayList<>();

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
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
