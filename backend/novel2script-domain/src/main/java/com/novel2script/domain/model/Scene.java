package com.novel2script.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A scene in the script — the primary structural unit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Scene {

    private Long id;
    private Long scriptId;
    private int sceneNumber;
    private String title;
    private String location;
    private String timeOfDay;    // MORNING / AFTERNOON / EVENING / NIGHT / DAWN / DUSK
    private boolean interior;    // true = INT (interior), false = EXT (exterior)
    private String summary;
    private String mood;
    private List<Long> chapterIds;
    private String sourceReason; // LOCATION / TIME / CHARACTER / CONFLICT

    @Builder.Default
    private List<Dialogue> dialogues = new ArrayList<>();

    @Builder.Default
    private List<Action> actions = new ArrayList<>();

    private LocalDateTime createdAt;

    public String getSceneHeader() {
        String intExt = interior ? "INT" : "EXT";
        return String.format("%s. %s - %s - %s", intExt, location, timeOfDay, sceneNumber);
    }
}
