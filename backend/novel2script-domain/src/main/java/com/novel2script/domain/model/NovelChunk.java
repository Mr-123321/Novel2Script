package com.novel2script.domain.model;

import lombok.Builder;

import java.util.List;

/**
 * A chunk of novel text for vector storage and retrieval.
 *
 * <p>Chunks are the fundamental retrieval unit for long novel processing.
 * Each chunk carries enough context to be independently analyzable by
 * AI models, with overlap to prevent information loss at boundaries.
 *
 * @param chunkId      unique identifier (UUID)
 * @param chunkIndex   sequential index within the novel
 * @param novelId      parent novel ID
 * @param chapterIds   source chapter IDs this chunk spans
 * @param content      the chunk text (~2000 tokens)
 * @param tokenCount   estimated token count
 * @param startOffset  byte offset in source file
 * @param endOffset    byte offset in source file
 */
@Builder
public record NovelChunk(
        String chunkId,
        int chunkIndex,
        Long novelId,
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
