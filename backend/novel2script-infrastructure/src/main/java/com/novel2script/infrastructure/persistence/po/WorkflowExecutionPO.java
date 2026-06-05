package com.novel2script.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_executions")
public class WorkflowExecutionPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scriptId;

    private String workflowType;

    private String currentStep;

    private String state;

    private String stateSnapshot;

    private String errorDetail;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;
}
