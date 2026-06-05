package com.novel2script.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dialogues")
public class DialoguePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sceneId;

    private Long characterId;

    private Integer sequence;

    private String speaker;

    private String emotion;

    private String content;

    private String parenthetical;

    private Long replyTo;

    private LocalDateTime createdAt;
}
