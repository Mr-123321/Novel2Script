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
 * Storyboard / shot division for a scene — bonus feature.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("shots")
public class Storyboard {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("scene_id")
    private Long sceneId;

    @TableField("shot_number")
    private int shotNumber;

    /** WIDE / MEDIUM / CLOSE_UP / EXTREME_CU / POV / ... */
    private String camera;

    /** EYE_LEVEL / HIGH_ANGLE / LOW_ANGLE / DUTCH / ... */
    private String angle;

    @TableField("duration_sec")
    private Double durationSec;

    private String description;

    /** STATIC / PAN / TILT / DOLLY / ZOOM / ... */
    private String movement;

    /** CUT / DISSOLVE / FADE / WIPE */
    private String transition;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public String getShotLabel() {
        return String.format("Shot %d — %s (%s)", shotNumber, camera, angle);
    }
}
