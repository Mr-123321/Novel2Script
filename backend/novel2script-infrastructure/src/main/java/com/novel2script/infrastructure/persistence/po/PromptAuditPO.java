package com.novel2script.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prompt_audits")
public class PromptAuditPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String promptName;

    private String promptVersion;

    private String modelName;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer latencyMs;

    private Integer retryCount;

    private Integer isSuccess;

    private String errorMessage;

    private String fullPrompt;

    private String fullResponse;

    private LocalDateTime createdAt;
}
