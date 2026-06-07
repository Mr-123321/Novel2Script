package com.novel2script.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.novel2script.common.enums.Emotion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A line of dialogue spoken by a character within a scene.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("dialogues")
public class Dialogue {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("scene_id")
    private Long sceneId;

    @TableField("character_id")
    private Long characterId;

    private int sequence;
    private String speaker;
    private Emotion emotion;
    private String content;

    /** e.g., (低声), (冷笑) */
    private String parenthetical;

    /** ID of dialogue this is replying to */
    @TableField("reply_to")
    private Long replyTo;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public String toScriptFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-15s", speaker.toUpperCase()));
        if (parenthetical != null && !parenthetical.isBlank()) {
            sb.append("\n").append(String.format("%-15s", "")).append(parenthetical);
        }
        sb.append("\n").append(String.format("%-15s", "")).append(content);
        return sb.toString();
    }
}
