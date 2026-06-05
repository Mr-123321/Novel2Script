package com.novel2script.domain.model;

import com.novel2script.common.enums.SourceReason;
import com.novel2script.common.enums.TimeOfDay;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A scene in the script — the primary structural unit for film/video production.
 *
 * <p>Scenes are created by segmenting novel chapters based on changes in
 * location, time, characters, and conflict.
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
    private TimeOfDay timeOfDay;
    private boolean interior;    // true = INT (interior), false = EXT (exterior)
    private String summary;
    private String mood;
    private List<Long> chapterIds;
    private List<Long> characterIds;
    private SourceReason sourceReason;
    private String sceneHeading;

    @Builder.Default
    private List<Dialogue> dialogues = new ArrayList<>();

    @Builder.Default
    private List<Action> actions = new ArrayList<>();

    private LocalDateTime createdAt;

    /**
     * Generate a standard film script scene header.
     * Format: {@code INT/EXT. LOCATION - TIME_OF_DAY}
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code INT. 教室 - MORNING}</li>
     *   <li>{@code EXT. 操场 - AFTERNOON}</li>
     *   <li>{@code INT. 家中 - NIGHT}</li>
     * </ul>
     */
    public String getSceneHeader() {
        String intExt = interior ? "INT" : "EXT";
        String timeLabel = timeOfDay != null ? timeOfDay.getScriptLabel() : "UNKNOWN";
        String loc = location != null ? location : "未知地点";
        return String.format("%s. %s - %s", intExt, loc, timeLabel);
    }

    /**
     * Get the legacy-style header with scene number.
     */
    public String getFullHeader() {
        return String.format("%s. %s - %s - %d",
                interior ? "INT" : "EXT",
                location != null ? location : "未知地点",
                timeOfDay != null ? timeOfDay.getScriptLabel() : "UNKNOWN",
                sceneNumber);
    }

    /**
     * Returns true if this scene takes place during daytime hours.
     */
    public boolean isDayScene() {
        return timeOfDay != null && timeOfDay.isDaytime();
    }

    /**
     * Returns true if this scene takes place at night.
     */
    public boolean isNightScene() {
        return timeOfDay != null && timeOfDay.isNighttime();
    }

    /**
     * Returns a compact one-line description for storyboarding.
     */
    public String toStoryboardLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("S%03d | ", sceneNumber));
        sb.append(getSceneHeader());
        if (title != null && !title.isBlank()) {
            sb.append(" | ").append(title);
        }
        if (summary != null && !summary.isBlank()) {
            sb.append(" | ").append(summary.length() > 60
                    ? summary.substring(0, 57) + "..."
                    : summary);
        }
        return sb.toString();
    }
}
