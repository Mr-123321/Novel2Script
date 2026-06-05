package com.novel2script.domain.model;

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
public class Action {

    private Long id;
    private Long sceneId;
    private Long characterId;     // null means environmental description
    private int sequence;
    private String actionType;    // ACTION / REACTION / BEAT / BUSINESS
    private String description;
    private Integer durationMs;   // estimated duration in milliseconds

    private LocalDateTime createdAt;

    public boolean isEnvironmental() {
        return characterId == null;
    }
}
