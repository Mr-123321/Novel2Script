package com.novel2script.application.service.processor;

import com.novel2script.domain.model.Novel;
import com.novel2script.domain.model.NovelChunk;
import com.novel2script.infrastructure.vector.EmbeddingService;
import com.novel2script.infrastructure.vector.MilvusVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processes long novels (500K+ characters) by chunking, embedding, and using
 * vector retrieval to build targeted context windows for each analysis task.
 *
 * <h3>Pipeline</h3>
 * <pre>
 *   Novel → Chunking → Embedding → Milvus Storage
 *     ↓
 *   For each analysis task:
 *     Task Query → Vector Search → Context Assembly → AI Call → Result
 *     ↓
 *   Results → Aggregation → Final Output
 * </pre>
 *
 * <h3>Performance Targets</h3>
 * <ul>
 *   <li>100万字: chunking &lt; 5s, embedding &lt; 30s, search &lt; 100ms/query</li>
 *   <li>Memory: &lt; 2GB for 50万+ word novels</li>
 *   <li>Context: never exceeds AI model window (max 64K tokens)</li>
 * </ul>
 */
@Slf4j
@Service
public class LongNovelProcessor {

    /** Collection name in vector store for novel chunks. */
    private static final String CHUNK_COLLECTION = "novel_chunks";

    /** Maximum context tokens per AI call. */
    private static final int MAX_CONTEXT_TOKENS = 60_000;

    /** Batch size for embedding calls. */
    private static final int EMBEDDING_BATCH_SIZE = 100;

    private final NovelChunker chunker;
    private final EmbeddingService embeddingService;
    private final MilvusVectorStore vectorStore;
    private final ContextBuilder contextBuilder;

    public LongNovelProcessor(NovelChunker chunker,
                              EmbeddingService embeddingService,
                              MilvusVectorStore vectorStore,
                              ContextBuilder contextBuilder) {
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.contextBuilder = contextBuilder;
    }

    // ── Public API ──────────────────────────────────────

    /**
     * Process a long novel through the complete chunking → embedding → storage pipeline.
     *
     * <p>This is Step 1+2 of the long novel workflow. After this, individual
     * analysis agents can use {@link ContextBuilder} to retrieve relevant
     * chunks for their specific tasks.
     *
     * @param novel the novel to process
     * @return processing summary with chunk count and performance metrics
     */
    public ProcessResult preprocess(Novel novel) {
        long startTime = System.currentTimeMillis();

        // Step 1: Chunking
        List<NovelChunk> chunks = chunker.chunk(novel);
        log.info("Step 1 — Chunking: {} chunks from {} chapters ({:.1f}s)",
                chunks.size(), novel.getChapterCount(),
                (System.currentTimeMillis() - startTime) / 1000.0);

        // Step 2: Embedding + Store
        long embedStart = System.currentTimeMillis();
        embedAndStore(chunks);
        long embedTime = System.currentTimeMillis() - embedStart;

        long totalTime = System.currentTimeMillis() - startTime;

        ProcessResult result = new ProcessResult(
                chunks.size(),
                novel.getTotalChars(),
                embedTime,
                totalTime,
                chunks.stream().mapToInt(NovelChunk::tokenCount).sum()
        );

        log.info("LongNovelProcessor: preprocessing complete — {} chunks, {} chars, {:.1f}s total",
                result.chunkCount(), novel.getTotalChars(), totalTime / 1000.0);
        return result;
    }

    /**
     * Get the context for a specific task by searching relevant chunks.
     * Call this after {@link #preprocess(Novel)}.
     *
     * @param taskQuery  natural language task description
     * @param allChunks  all chunks from preprocessing
     * @return assembled context string ready for AI prompt
     */
    public String getTaskContext(String taskQuery, List<NovelChunk> allChunks) {
        return contextBuilder.buildContext(taskQuery, allChunks, MAX_CONTEXT_TOKENS);
    }

    /**
     * Get a broader context for tasks that need wider coverage.
     * Uses lower similarity threshold and higher top-K.
     */
    public String getBroadTaskContext(String taskQuery, List<NovelChunk> allChunks) {
        return contextBuilder.buildBroadContext(taskQuery, allChunks, MAX_CONTEXT_TOKENS);
    }

    /**
     * Filter chunks that are relevant to the given task query.
     * Returns the chunks themselves (not just the context text) for
     * cases where agents need per-chunk processing.
     */
    public List<NovelChunk> getRelevantChunks(String taskQuery, List<NovelChunk> allChunks) {
        float[] queryEmbedding = embeddingService.embed(taskQuery);
        var results = vectorStore.searchSimilar(queryEmbedding, 25, 0.35f);

        Map<String, NovelChunk> chunkMap = new HashMap<>();
        for (NovelChunk c : allChunks) {
            chunkMap.put(c.chunkId(), c);
        }

        return results.stream()
                .map(r -> chunkMap.get(r.characterId()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(NovelChunk::chunkIndex))
                .toList();
    }

    /**
     * Get chunks by chapter range — useful when agents need full chapter context.
     */
    public List<NovelChunk> getChunksByChapters(List<NovelChunk> allChunks,
                                                long startChapterId, long endChapterId) {
        return allChunks.stream()
                .filter(c -> c.chapterIds() != null && c.chapterIds().stream()
                        .anyMatch(chId -> chId >= startChapterId && chId <= endChapterId))
                .sorted(Comparator.comparingInt(NovelChunk::chunkIndex))
                .toList();
    }

    /**
     * Return a progress report for monitoring.
     */
    public Map<String, Object> getProgressReport(List<NovelChunk> allChunks) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalChunks", allChunks.size());
        report.put("totalTokens", allChunks.stream().mapToInt(NovelChunk::tokenCount).sum());
        report.put("avgTokensPerChunk", allChunks.stream()
                .mapToInt(NovelChunk::tokenCount).average().orElse(0));
        report.put("uniqueChapters", allChunks.stream()
                .flatMap(c -> c.chapterIds() != null ? c.chapterIds().stream() : java.util.stream.Stream.empty())
                .distinct().count());
        report.put("vectorsStored", vectorStore.size());
        report.put("estimatedMemoryMB", estimateMemoryUsage(allChunks));
        return report;
    }

    /**
     * Clear all stored vectors — call before reprocessing a novel.
     */
    public void reset() {
        log.info("LongNovelProcessor: resetting vector store ({} vectors)", vectorStore.size());
        vectorStore.clear();
    }

    // ── Internal ────────────────────────────────────────

    /**
     * Batch-embed all chunks and store in Milvus.
     */
    private void embedAndStore(List<NovelChunk> chunks) {
        AtomicInteger stored = new AtomicInteger(0);

        for (int i = 0; i < chunks.size(); i += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(i + EMBEDDING_BATCH_SIZE, chunks.size());
            List<NovelChunk> batch = chunks.subList(i, end);
            List<String> texts = batch.stream().map(NovelChunk::content).toList();

            List<float[]> embeddings = embeddingService.embedBatch(texts);

            for (int j = 0; j < batch.size(); j++) {
                NovelChunk chunk = batch.get(j);
                float[] embedding = embeddings.get(j);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("chunk_index", chunk.chunkIndex());
                metadata.put("chapter_ids", chunk.chapterIds());
                metadata.put("token_count", chunk.tokenCount());
                metadata.put("novel_id", chunk.novelId());

                vectorStore.insertCharacter(chunk.chunkId(), embedding, metadata);
                stored.incrementAndGet();
            }

            if (i % (EMBEDDING_BATCH_SIZE * 5) == 0 && i > 0) {
                log.debug("LongNovelProcessor: embedded {}/{} chunks", i, chunks.size());
            }
        }

        log.info("LongNovelProcessor: stored {} vectors in Milvus", stored.get());
    }

    /**
     * Rough memory usage estimate for the chunk list.
     */
    private long estimateMemoryUsage(List<NovelChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return 0;
        return chunks.stream()
                .mapToLong(c -> (long) c.content().length() * 2) // UTF-16 ≈ 2 bytes/char
                .sum() / (1024 * 1024); // Convert to MB
    }

    // ── Result record ───────────────────────────────────

    /**
     * Result of the preprocessing pipeline.
     */
    public record ProcessResult(
            int chunkCount,
            int totalChars,
            long embedTimeMs,
            long totalTimeMs,
            int totalTokens
    ) {
        /** Chars per second processed. */
        public double charsPerSecond() {
            return totalTimeMs > 0 ? (double) totalChars / totalTimeMs * 1000 : 0;
        }

        /** Tokens per second for embedding. */
        public double embeddingThroughput() {
            return embedTimeMs > 0 ? (double) totalTokens / embedTimeMs * 1000 : 0;
        }

        @Override
        public String toString() {
            return String.format(
                    "ProcessResult{chunks=%d, chars=%d, embedTime=%dms, totalTime=%dms, throughput=%.1f chars/s}",
                    chunkCount, totalChars, embedTimeMs, totalTimeMs, charsPerSecond());
        }
    }
}
