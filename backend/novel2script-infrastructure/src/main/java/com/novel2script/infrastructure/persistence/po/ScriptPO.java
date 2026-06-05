package com.novel2script.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scripts")
public class ScriptPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long novelId;

    private String title;

    private Integer version;

    private Integer sceneCount;

    private Integer characterCount;

    private Integer dialogueCount;

    private String yamlContent;

    private String status;

    private BigDecimal progress;

    private String workflowState;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
