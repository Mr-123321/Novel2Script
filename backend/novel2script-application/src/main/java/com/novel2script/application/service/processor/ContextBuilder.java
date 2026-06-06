package com.novel2script.application.service.processor;

import com.novel2script.domain.model.NovelChunk;
import com.novel2script.domain.vector.SimilarityResult;
import com.novel2script.infrastructure.vector.EmbeddingService;
import com.novel2script.infrastructure.vector.MilvusVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds AI context windows by retrieving the most relevant novel chunks
 * for a given analysis task using vector similarity search.
 *
 * <h3>Process</h3>
 * <ol>
 *   <li>Embed the task query (e.g., "提取所有角色信息")</li>
 *   <li>Search Milvus for top-K relevant chunks</li>
 *   <li>Re-order by original chunk index (preserving narrative order)</li>
 *   <li>Concatenate up to the max token limit</li>
 * </ol>
 *
 * <h3>Default Context Window: 64K tokens</h3>
 * This fits within most modern LLM context windows (Claude: 200K, GPT-4: 128K,
 * DeepSeek: 64K) while leaving room for the system prompt and few-shot examples.
 */
@Slf4j
@Service
public class ContextBuilder {

    /** Default max context tokens (64K leaves room for system prompt + response). */
    private static final int DEFAULT_MAX_TOKENS = 60_000;

    /** Minimum similarity score to include a chunk (0.0 – 1.0). */
    private static final float MIN_SIMILARITY = 0.35f;

    /** Default number of chunks to retrieve. */
    private static final int DEFAULT_TOP_K = 25;

    private final EmbeddingService embeddingService;
    private final MilvusVectorStore vectorStore;

    public ContextBuilder(EmbeddingService embeddingService, MilvusVectorStore vectorStore) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    // ── Public API ──────────────────────────────────────

    /**
     * Build a context window for an analysis task.
     *
     * @param taskQuery  human-readable task description (e.g., "提取角色信息")
     * @param allChunks  all pre-embedded novel chunks
     * @param maxTokens  maximum context size in tokens (default: 60000)
     * @return assembled context text, ready to insert into AI prompt
     */
    public String buildContext(String taskQuery, List<NovelChunk> allChunks, int maxTokens) {
        if (allChunks == null || allChunks.isEmpty()) {
            log.warn("ContextBuilder: no chunks available");
            return "";
        }

        int effectiveMaxTokens = maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS;
        int topK = Math.min(DEFAULT_TOP_K, allChunks.size());

        // 1. Build chunk lookup map
        Map<String, NovelChunk> chunkMap = allChunks.stream()
                .collect(Collectors.toMap(NovelChunk::chunkId, c -> c, (a, b) -> a));

        // 2. Embed the task query
        float[] queryEmbedding = embeddingService.embed(taskQuery);

        // 3. Retrieve relevant chunks from Milvus
        List<SimilarityResult> results = vectorStore.searchSimilar(queryEmbedding, topK, MIN_SIMILARITY);

        if (results.isEmpty()) {
            log.warn("ContextBuilder: no relevant chunks found for query '{}', returning first chunks",
                    taskQuery.length() > 50 ? taskQuery.substring(0, 50) + "..." : taskQuery);
            // Fallback: return first N chunks in order
            return fallbackContext(allChunks, effectiveMaxTokens);
        }

        // 4. Get matching chunks
        List<NovelChunk> relevantChunks = results.stream()
                .map(r -> chunkMap.get(r.characterId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.debug("ContextBuilder: retrieved {} relevant chunks from top-{} search",
                relevantChunks.size(), topK);

        // 5. Sort by original chunk index (preserving narrative order)
        relevantChunks.sort(Comparator.comparingInt(NovelChunk::chunkIndex));

        // 6. Assemble context within token budget
        return assembleContext(relevantChunks, effectiveMaxTokens);
    }

    /**
     * Build context with default max tokens (60K).
     */
    public String buildContext(String taskQuery, List<NovelChunk> allChunks) {
        return buildContext(taskQuery, allChunks, DEFAULT_MAX_TOKENS);
    }

    /**
     * Build context using hybrid retrieval (vector + keyword) for higher recall.
     * Especially effective for plot event extraction, character-centric queries,
     * and entity-heavy tasks where keyword matching complements semantic search.
     *
     * <p>Uses Reciprocal Rank Fusion (RRF, k=60) to merge vector similarity
     * and BM25-style keyword scores without requiring score normalization.
     *
     * @param taskQuery     human-readable task description
     * @param allChunks     all pre-embedded novel chunks
     * @param maxTokens     maximum context size in tokens
     * @param vectorWeight  weight of vector scores (0.0–1.0), default 0.6
     * @return assembled context text with higher recall
     */
    public String buildHybridContext(String taskQuery, List<NovelChunk> allChunks,
                                      int maxTokens, float vectorWeight) {
        if (allChunks == null || allChunks.isEmpty()) {
            log.warn("ContextBuilder: no chunks available for hybrid search");
            return "";
        }

        int effectiveMaxTokens = maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS;

        // 1. Build chunk lookup map
        Map<String, NovelChunk> chunkMap = allChunks.stream()
                .collect(Collectors.toMap(NovelChunk::chunkId, c -> c, (a, b) -> a));

        // 2. Embed the task query
        float[] queryEmbedding = embeddingService.embed(taskQuery);

        // 3. Hybrid search: vector + keyword via RRF
        float minScore = 0.25f; // slightly lower threshold for hybrid (keyword boosts recall)
        int topK = Math.min(50, allChunks.size());

        List<SimilarityResult> hybridResults = vectorStore.searchHybrid(
                queryEmbedding, taskQuery, topK, minScore, vectorWeight);

        if (hybridResults.isEmpty()) {
            log.warn("ContextBuilder: hybrid search returned no results for '{}', using fallback",
                    taskQuery.length() > 50 ? taskQuery.substring(0, 50) + "..." : taskQuery);
            return fallbackContext(allChunks, effectiveMaxTokens);
        }

        // 4. Resolve to chunks and sort by original order
        List<NovelChunk> relevantChunks = hybridResults.stream()
                .map(r -> chunkMap.get(r.characterId()))
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparingInt(NovelChunk::chunkIndex))
                .toList();

        log.info("ContextBuilder: hybrid search → {} unique chunks (vectorWeight={})",
                relevantChunks.size(), vectorWeight);

        // 5. Assemble context within token budget
        return assembleContext(relevantChunks, effectiveMaxTokens);
    }

    /**
     * Build hybrid context with default parameters.
     */
    public String buildHybridContext(String taskQuery, List<NovelChunk> allChunks) {
        return buildHybridContext(taskQuery, allChunks, DEFAULT_MAX_TOKENS, 0.6f);
    }

    /**
     * Build context for a multi-step task that needs more breadth.
     * Uses a higher top-K for broader coverage.
     */
    public String buildBroadContext(String taskQuery, List<NovelChunk> allChunks, int maxTokens) {
        if (allChunks == null || allChunks.isEmpty()) {
            return "";
        }

        int effectiveMaxTokens = maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS;
        int broadTopK = Math.min(50, allChunks.size());

        float[] queryEmbedding = embeddingService.embed(taskQuery);
        List<SimilarityResult> results = vectorStore.searchSimilar(queryEmbedding, broadTopK, 0.2f);

        if (results.isEmpty()) {
            return fallbackContext(allChunks, effectiveMaxTokens);
        }

        Map<String, NovelChunk> chunkMap = allChunks.stream()
                .collect(Collectors.toMap(NovelChunk::chunkId, c -> c, (a, b) -> a));

        List<NovelChunk> relevantChunks = results.stream()
                .map(r -> chunkMap.get(r.characterId()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(NovelChunk::chunkIndex))
                .collect(Collectors.toList());

        if (relevantChunks.isEmpty()) {
            return fallbackContext(allChunks, effectiveMaxTokens);
        }

        return assembleContext(relevantChunks, effectiveMaxTokens);
    }

    /**
     * Estimate how many chunks will fit in the given token budget.
     */
    public int estimateChunkCapacity(int availableTokens, List<NovelChunk> candidates) {
        if (candidates == null || candidates.isEmpty()) return 0;

        int used = 0;
        int count = 0;
        for (NovelChunk chunk : candidates) {
            if (used + chunk.tokenCount() > availableTokens) break;
            used += chunk.tokenCount();
            count++;
        }
        return count;
    }

    // ── Internal ────────────────────────────────────────

    private String assembleContext(List<NovelChunk> chunks, int maxTokens) {
        StringBuilder context = new StringBuilder();
        int tokenCount = 0;

        for (NovelChunk chunk : chunks) {
            int chunkTokens = chunk.tokenCount();
            if (tokenCount + chunkTokens > maxTokens) {
                // If this is the first chunk and it's too big, trim it
                if (context.isEmpty()) {
                    String trimmed = trimToTokens(chunk.content(), maxTokens);
                    context.append(trimmed);
                    log.debug("ContextBuilder: trimmed oversized first chunk {} → {} tokens",
                            chunkTokens, estimateTrimmedTokens(trimmed));
                }
                break;
            }

            context.append(chunk.content()).append("\n\n");
            tokenCount += chunkTokens;
        }

        log.debug("ContextBuilder: assembled {} tokens across {} chunks (budget: {})",
                tokenCount, countChunksInContext(context.toString()), maxTokens);
        return context.toString();
    }

    private String fallbackContext(List<NovelChunk> allChunks, int maxTokens) {
        return assembleContext(
                allChunks.stream()
                        .sorted(Comparator.comparingInt(NovelChunk::chunkIndex))
                        .collect(Collectors.toList()),
                maxTokens);
    }

    private String trimToTokens(String text, int maxTokens) {
        int charLimit = (int) (maxTokens / 0.7);
        if (text.length() <= charLimit) return text;
        return text.substring(0, charLimit) + "\n...";
    }

    private int estimateTrimmedTokens(String text) {
        return NovelChunker.estimateTokens(text);
    }

    private int countChunksInContext(String context) {
        // rough: count paragraph separators
        return context.split("\n\n").length;
    }
}
