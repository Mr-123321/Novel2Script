package com.novel2script.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shots")
public class ShotPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sceneId;

    private Integer shotNumber;

    private String camera;

    private String angle;

    private Double durationSec;

    private String description;

    private String movement;

    private String transition;

    private LocalDateTime createdAt;
}
