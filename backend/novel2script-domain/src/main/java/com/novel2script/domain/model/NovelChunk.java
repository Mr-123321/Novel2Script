package com.novel2script.domain.model;

import com.novel2script.domain.handler.LongListTypeHandler;
import lombok.Builder;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.List;

/**
 * A chunk of novel text for vector storage and retrieval.
 *
 * <p>Chunks are the fundamental retrieval unit for long novel processing.
 * Each chunk carries enough context to be independently analyzable by
 * AI models, with overlap to prevent information loss at boundaries.
 *
 * <p>Note: This is a record class. MyBatis-Plus annotation support for
 * records is limited; this entity is primarily used through manual
 * mapper XML rather than automatic CRUD.
 */
@Builder
public record NovelChunk(
        String chunkId,
        int chunkIndex,
        Long novelId,
        @TableField(typeHandler = LongListTypeHandler.class)
        List<Long> chapterIds,
        String content,
        int tokenCount,
        long startOffset,
        long endOffset
) {

    /**
     * Returns a compact identifier for logging.
     */
    public String label() {
        return String.format("Chunk-%d[id=%s, ch=%s, tokens=%d]",
                chunkIndex,
                chunkId != null && chunkId.length() > 8 ? chunkId.substring(0, 8) : chunkId,
                chapterIds != null ? chapterIds.toString() : "[]",
                tokenCount);
    }

    /**
     * Whether this chunk is within the given chapter range.
     */
    public boolean overlapsChapter(long chapterId) {
        return chapterIds != null && chapterIds.contains(chapterId);
    }
}
