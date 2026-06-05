package com.novel2script.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("novels")
public class NovelPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String author;

    private String fileName;

    private Long fileSize;

    private Integer totalChars;

    private Integer chapterCount;

    private String status;

    private String metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
