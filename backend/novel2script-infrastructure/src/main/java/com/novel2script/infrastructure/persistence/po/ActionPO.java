package com.novel2script.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("actions")
public class ActionPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sceneId;

    private Long characterId;

    private Integer sequence;

    private String actionType;

    private String description;

    private Integer durationMs;

    private LocalDateTime createdAt;
}
