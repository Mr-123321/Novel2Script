package com.novel2script.domain.model;

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
public class Dialogue {

    private Long id;
    private Long sceneId;
    private Long characterId;
    private int sequence;
    private String speaker;
    private Emotion emotion;
    private String content;
    private String parenthetical;  // e.g., (低声), (冷笑)
    private Long replyTo;          // ID of dialogue this is replying to

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
