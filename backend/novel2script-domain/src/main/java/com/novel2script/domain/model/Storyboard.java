package com.novel2script.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Storyboard / shot division for a scene — bonus feature.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Storyboard {

    private Long id;
    private Long sceneId;
    private int shotNumber;
    private String camera;       // WIDE / MEDIUM / CLOSE_UP / EXTREME_CU / POV / ...
    private String angle;        // EYE_LEVEL / HIGH_ANGLE / LOW_ANGLE / DUTCH / ...
    private Double durationSec;  // duration in seconds
    private String description;
    private String movement;     // STATIC / PAN / TILT / DOLLY / ZOOM / ...
    private String transition;   // CUT / DISSOLVE / FADE / WIPE

    private LocalDateTime createdAt;

    public String getShotLabel() {
        return String.format("Shot %d — %s (%s)", shotNumber, camera, angle);
    }
}
