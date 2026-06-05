package com.novel2script.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plot_events")
public class PlotEventPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scriptId;

    private Integer eventOrder;

    private String title;

    private String description;

    private String location;

    private String timePoint;

    private String conflictType;

    private String chapterIds;

    private String characterIds;

    private Integer importance;

    private LocalDateTime createdAt;
}
