package com.novel2script.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("characters")
public class CharacterPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scriptId;

    private String canonicalName;

    private String aliases;

    private String roleType;

    private String gender;

    private String ageRange;

    private String description;

    private String personality;

    private String relationships;

    private Integer appearanceCount;

    private Long firstAppearance;

    private String embeddingId;

    private Integer isResolved;

    private String mergedFrom;

    private LocalDateTime createdAt;
}
