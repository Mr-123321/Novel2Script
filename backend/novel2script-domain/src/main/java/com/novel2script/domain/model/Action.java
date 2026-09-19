package com.novel2script.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.novel2script.common.enums.ContentSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * An action or movement description within a scene.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("actions")
public class Action {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("scene_id")
    private Long sceneId;

    /** null means environmental description */
    @TableField("character_id")
    private Long characterId;

    private int sequence;

    @TableField("action_type")
    private String actionType;    // ACTION / REACTION / BEAT / BUSINESS

    private String description;

    /**
     * How this action was produced: AI / REGEX / MANUAL.
     *
     * <p>The DB column defaults to {@link ContentSource#AI}; manual edits are
     * marked by {@code ScriptService}.
     */
    @TableField("source")
    private ContentSource source;

    @TableField("duration_ms")
    private Integer durationMs;   // estimated duration in milliseconds

    @TableField("created_at")
    private LocalDateTime createdAt;

    public boolean isEnvironmental() {
        return characterId == null;
    }
}
