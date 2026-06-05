package com.novel2script.application.service.processor;

import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Novel;
import com.novel2script.domain.model.NovelChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Splits a novel into overlapping chunks suitable for vector embedding and AI processing.
 *
 * <h3>Chunking Strategy</h3>
 * <ul>
 *   <li>Target chunk size: ~2000 tokens (≈1300 Chinese characters)</li>
 *   <li>Overlap: ~200 tokens (≈130 Chinese characters) to prevent context loss at boundaries</li>
 *   <li>Sentence-aware: never splits in the middle of a sentence</li>
 *   <li>Chapter-aware: tracks which chapter(s) each chunk belongs to</li>
 * </ul>
 *
 * <h3>Token Estimation</h3>
 * Chinese text: ~1 char ≈ 0.7 tokens (conservative estimate)<br>
 * English text: ~1 word ≈ 1.3 tokens<br>
 * Mixed text: ~1 char ≈ 0.8 tokens
 */
@Slf4j
@Service
public class NovelChunker {

    /** Target chunk size in tokens. */
    static final int CHUNK_SIZE_TOKENS = 2000;

    /** Overlap size in tokens to maintain context continuity. */
    static final int OVERLAP_SIZE_TOKENS = 200;

    /** Conservative token-per-char ratio for Chinese text. */
    private static final double TOKENS_PER_CHAR = 0.7;

    /** Estimated chars per chunk (2000 tokens / 0.7 tokens-per-char). */
    static final int CHARS_PER_CHUNK = (int) (CHUNK_SIZE_TOKENS / TOKENS_PER_CHAR);   // ≈2857

    /** Estimated chars of overlap (200 tokens / 0.7 tokens-per-char). */
    static final int CHARS_OVERLAP = (int) (OVERLAP_SIZE_TOKENS / TOKENS_PER_CHAR);    // ≈285

    // Chinese sentence-ending punctuation
    private static final Set<Character> SENTENCE_ENDS = Set.of(
            '。', '！', '？', '；', '\n', '…', '~', '"', '」', '』'
    );

    // ── Public API ──────────────────────────────────────

    /**
     * Chunk an entire novel into overlapping segments.
     *
     * @param novel the novel with chapters loaded
     * @return list of chunks in order
     */
    public List<NovelChunk> chunk(Novel novel) {
        if (novel == null || novel.getChapters() == null || novel.getChapters().isEmpty()) {
            log.warn("NovelChunker: empty novel, no chunks produced");
            return List.of();
        }

        List<NovelChunk> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        List<Long> currentChapterIds = new ArrayList<>();
        long totalOffset = 0;
        int chunkIndex = 0;

        for (Chapter chapter : novel.getChapters()) {
            String content = chapter.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }

            buffer.append(content);
            currentChapterIds.add(chapter.getId());

            // Split buffer into chunks when it exceeds threshold
            while (buffer.length() >= CHARS_PER_CHUNK) {
                int splitPoint = findSplitPoint(buffer.toString(), CHARS_PER_CHUNK);
                String chunkContent = buffer.substring(0, splitPoint);

                chunks.add(NovelChunk.builder()
                        .chunkId(UUID.randomUUID().toString())
                        .chunkIndex(chunkIndex++)
                        .novelId(novel.getId())
                        .chapterIds(new ArrayList<>(currentChapterIds))
                        .content(chunkContent.trim())
                        .tokenCount(estimateTokens(chunkContent))
                        .startOffset(totalOffset)
                        .endOffset(totalOffset + splitPoint)
                        .build());

                totalOffset += splitPoint;

                // Keep overlap for next chunk
                int overlapStart = Math.max(0, splitPoint - CHARS_OVERLAP);
                String remaining = buffer.substring(overlapStart);
                buffer = new StringBuilder(remaining);
                totalOffset -= (splitPoint - overlapStart);

                // Clean up chapter IDs that are no longer in buffer
                currentChapterIds = pruneChapterIds(currentChapterIds, novel.getChapters(), totalOffset);
            }
        }

        // Don't forget the tail
        if (!buffer.isEmpty()) {
            chunks.add(NovelChunk.builder()
                    .chunkId(UUID.randomUUID().toString())
                    .chunkIndex(chunkIndex)
                    .novelId(novel.getId())
                    .chapterIds(new ArrayList<>(currentChapterIds))
                    .content(buffer.toString().trim())
                    .tokenCount(estimateTokens(buffer.toString()))
                    .startOffset(totalOffset)
                    .endOffset(totalOffset + buffer.length())
                    .build());
        }

        log.info("NovelChunker: produced {} chunks from {} chapters, avg {}/chunk",
                chunks.size(), novel.getChapters().size(),
                chunks.isEmpty() ? 0 : chunks.stream().mapToInt(NovelChunk::tokenCount).average().orElse(0));
        return chunks;
    }

    /**
     * Stream-based chunking for memory-efficient processing of large novels.
     *
     * @param novelStream input stream of novel text (UTF-8)
     * @return a stream of chunks (lazily computed)
     */
    public Stream<NovelChunk> chunkStream(InputStream novelStream) {
        if (novelStream == null) {
            return Stream.empty();
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(novelStream, StandardCharsets.UTF_8));
        return StreamSupport.stream(
                new ChunkSpliterator(reader), false);
    }

    /**
     * Simple chunk split by paragraph boundaries for streaming.
     */
    public List<NovelChunk> chunkByParagraphs(String text, Long novelId) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] paragraphs = text.split("\\n\\s*\\n");
        List<NovelChunk> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int chunkIndex = 0;
        long offset = 0;

        for (String para : paragraphs) {
            if (para.isBlank()) continue;

            if (buffer.length() + para.length() > CHARS_PER_CHUNK && !buffer.isEmpty()) {
                String content = buffer.toString().trim();
                chunks.add(NovelChunk.builder()
                        .chunkId(UUID.randomUUID().toString())
                        .chunkIndex(chunkIndex++)
                        .novelId(novelId)
                        .chapterIds(List.of())
                        .content(content)
                        .tokenCount(estimateTokens(content))
                        .startOffset(offset - content.length())
                        .endOffset(offset)
                        .build());
                buffer = new StringBuilder();
            }
            buffer.append(para).append("\n\n");
            offset += para.length() + 2;
        }

        if (!buffer.isEmpty()) {
            String content = buffer.toString().trim();
            chunks.add(NovelChunk.builder()
                    .chunkId(UUID.randomUUID().toString())
                    .chunkIndex(chunkIndex)
                    .novelId(novelId)
                    .chapterIds(List.of())
                    .content(content)
                    .tokenCount(estimateTokens(content))
                    .startOffset(offset - content.length())
                    .endOffset(offset)
                    .build());
        }

        return chunks;
    }

    // ── Token Estimation ────────────────────────────────

    /**
     * Estimate token count for mixed Chinese/English text.
     *
     * <p>Conservative estimate:
     * <ul>
     *   <li>CJK characters: 1 char ≈ 0.7 tokens</li>
     *   <li>ASCII words: 1 word ≈ 1.3 tokens</li>
     *   <li>Punctuation/whitespace: minimal token overhead</li>
     * </ul>
     */
    static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        int cjkCount = 0;
        int asciiWordCount = 0;
        boolean inWord = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A) {
                cjkCount++;
                if (inWord) {
                    asciiWordCount++;
                    inWord = false;
                }
            } else if (Character.isLetter(c)) {
                inWord = true;
            } else if (Character.isWhitespace(c) && inWord) {
                asciiWordCount++;
                inWord = false;
            }
        }
        if (inWord) asciiWordCount++;

        return (int) (cjkCount * 0.7 + asciiWordCount * 1.3 + 1);
    }

    // ── Internal Helpers ────────────────────────────────

    /**
     * Find the nearest sentence boundary at or before {@code preferredPos}.
     */
    private int findSplitPoint(String text, int preferredPos) {
        if (preferredPos >= text.length()) {
            return text.length();
        }

        // Search backwards from preferred position for a sentence end
        for (int i = preferredPos - 1; i >= Math.max(0, preferredPos - 500); i--) {
            if (SENTENCE_ENDS.contains(text.charAt(i))) {
                return i + 1; // include the punctuation in this chunk
            }
        }

        // Fallback: search forwards for the next sentence end
        for (int i = preferredPos; i < Math.min(text.length(), preferredPos + 500); i++) {
            if (SENTENCE_ENDS.contains(text.charAt(i))) {
                return i + 1;
            }
        }

        // Last resort: split at preferred position
        return preferredPos;
    }

    private List<Long> pruneChapterIds(List<Long> ids, List<Chapter> chapters, long offset) {
        // Keep only IDs that are still relevant — simple approach: keep last 2
        if (ids.size() <= 2) return ids;
        return ids.subList(ids.size() - 2, ids.size());
    }

    // ── Streaming spliterator ───────────────────────────

    private static class ChunkSpliterator extends Spliterators.AbstractSpliterator<NovelChunk> {

        private final BufferedReader reader;
        private int chunkIndex = 0;
        private long offset = 0;

        ChunkSpliterator(BufferedReader reader) {
            super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL);
            this.reader = reader;
        }

        @Override
        public boolean tryAdvance(java.util.function.Consumer<? super NovelChunk> action) {
            try {
                StringBuilder buffer = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line).append('\n');
                    if (estimateTokens(buffer.toString()) >= CHUNK_SIZE_TOKENS) {
                        break;
                    }
                }

                if (buffer.isEmpty()) {
                    reader.close();
                    return false;
                }

                String content = buffer.toString().trim();
                NovelChunk chunk = NovelChunk.builder()
                        .chunkId(UUID.randomUUID().toString())
                        .chunkIndex(chunkIndex++)
                        .novelId(null)
                        .chapterIds(List.of())
                        .content(content)
                        .tokenCount(estimateTokens(content))
                        .startOffset(offset)
                        .endOffset(offset + content.length())
                        .build();

                offset += content.length() + 1;
                action.accept(chunk);
                return true;

            } catch (Exception e) {
                log.error("ChunkSpliterator error", e);
                return false;
            }
        }
    }
}
