package com.novel2script.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scenes")
public class ScenePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scriptId;

    private Integer sceneNumber;

    private String title;

    private String location;

    private String timeOfDay;

    private Integer isInterior;

    private String summary;

    private String mood;

    private String chapterIds;

    private String sourceReason;

    private LocalDateTime createdAt;
}
