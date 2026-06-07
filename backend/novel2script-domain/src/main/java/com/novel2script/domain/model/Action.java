package com.novel2script.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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

    @TableField("duration_ms")
    private Integer durationMs;   // estimated duration in milliseconds

    @TableField("created_at")
    private LocalDateTime createdAt;

    public boolean isEnvironmental() {
        return characterId == null;
    }
}
