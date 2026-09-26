package com.novel2script.infrastructure.vector;

import com.novel2script.domain.vector.SimilarityResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory vector store that simulates Milvus operations.
 *
 * <p>Stores embedding vectors with metadata and supports cosine-similarity
 * nearest-neighbor search. Designed for character deduplication use cases.
 *
 * <p>Also maintains a keyword inverted index for hybrid (vector + keyword)
 * retrieval, improving recall for plot event and entity-centric queries.
 *
 * <p>In production, this would be replaced with the real Milvus client
 * (e.g., {@code io.milvus:milvus-sdk-java}). The API is designed to be
 * compatible with a future Milvus migration.
 *
 * <p>本实现为进程内向量存储，用于替代 Milvus 以降低部署复杂度；
 * 接口设计与 Milvus SDK 兼容，可平滑迁移到真实的分布式向量数据库。
 */
@Slf4j
@Service
public class InMemoryVectorStore {

    /**
     * Internal storage: characterId → stored vector entry.
     */
    private final Map<String, StoredVector> store = new ConcurrentHashMap<>();

    /**
     * Inverted keyword index: token → set of chunk IDs containing that token.
     * Used for BM25-style keyword retrieval in hybrid search.
     */
    private final Map<String, Map<String, Integer>> keywordIndex = new ConcurrentHashMap<>();

    /**
     * Total number of documents in the keyword index (for IDF calculation).
     */
    private final java.util.concurrent.atomic.AtomicInteger docCount = new java.util.concurrent.atomic.AtomicInteger(0);

    /** Minimum token length to index (filters out single-char noise). */
    private static final int MIN_TOKEN_LENGTH = 2;

    /** Chinese/English token delimiter pattern. */
    private static final java.util.regex.Pattern TOKEN_DELIMITER =
            java.util.regex.Pattern.compile("[\\s，,。.!！?？;；:：、\n\r\t]+");

    /**
     * Insert a character embedding into the store and index its keywords.
     *
     * @param characterId unique identifier for the character/chunk
     * @param embedding   the embedding vector
     * @param metadata    optional key-value metadata (should include "content" for keyword indexing)
     */
    public void insertCharacter(String characterId, float[] embedding,
                                 Map<String, Object> metadata) {
        if (characterId == null || characterId.isBlank()) {
            log.warn("InMemoryVectorStore: insertCharacter called with blank characterId");
            return;
        }
        if (embedding == null || embedding.length == 0) {
            log.warn("InMemoryVectorStore: insertCharacter called with empty embedding for '{}'",
                    characterId);
            return;
        }

        store.put(characterId, new StoredVector(
                embedding.clone(),
                metadata != null ? new HashMap<>(metadata) : new HashMap<>()));

        // ── Keyword indexing ──
        indexKeywords(characterId, metadata);

        log.debug("InMemoryVectorStore: inserted vector for '{}', dim={}", characterId, embedding.length);
    }

    /**
     * Index the content text of a chunk into the keyword inverted index.
     * Tokenizes the content and records which tokens appear in which chunk.
     */
    private void indexKeywords(String characterId, Map<String, Object> metadata) {
        if (metadata == null) return;

        // Extract searchable text from metadata
        String content = null;
        Object contentObj = metadata.get("content");
        if (contentObj instanceof String s) {
            content = s;
        }

        if (content == null || content.isBlank()) return;

        // Tokenize and index
        String[] tokens = TOKEN_DELIMITER.split(content);
        for (String token : tokens) {
            token = token.trim();
            if (token.length() < MIN_TOKEN_LENGTH) continue;

            keywordIndex.computeIfAbsent(token, k -> new ConcurrentHashMap<>())
                    .merge(characterId, 1, Integer::sum);
        }

        docCount.incrementAndGet();
    }

    /**
     * Search for the top-K most similar characters to the query embedding.
     *
     * @param queryEmbedding the embedding to search against
     * @param topK           maximum number of results
     * @param minScore       minimum cosine similarity threshold (0.0 – 1.0)
     * @return list of similarity results, sorted by score descending
     */
    public List<SimilarityResult> searchSimilar(float[] queryEmbedding, int topK,
                                                 float minScore) {
        if (queryEmbedding == null || queryEmbedding.length == 0 || store.isEmpty()) {
            return Collections.emptyList();
        }

        // Compute cosine similarity against all stored vectors
        List<SimilarityResult> allResults = new ArrayList<>();

        for (Map.Entry<String, StoredVector> entry : store.entrySet()) {
            double similarity = EmbeddingService.cosineSimilarity(
                    queryEmbedding, entry.getValue().embedding);

            if (similarity >= minScore) {
                allResults.add(new SimilarityResult(
                        entry.getKey(),
                        (float) similarity,
                        Collections.unmodifiableMap(entry.getValue().metadata)));
            }
        }

        // Sort by score descending, take top-K
        allResults.sort((a, b) -> Float.compare(b.score(), a.score()));

        if (allResults.size() > topK) {
            return allResults.subList(0, topK);
        }
        return allResults;
    }

    /**
     * Keyword search using BM25-inspired TF-IDF scoring.
     * Tokenizes the query and scores each document by the sum of term frequencies,
     * weighted by inverse document frequency.
     *
     * @param query  natural language query
     * @param topK   max results to return
     * @return list of results sorted by keyword relevance score (descending)
     */
    public List<SimilarityResult> searchByKeyword(String query, int topK) {
        if (query == null || query.isBlank() || keywordIndex.isEmpty()) {
            return Collections.emptyList();
        }

        // Tokenize query
        String[] queryTokens = TOKEN_DELIMITER.split(query);
        if (queryTokens.length == 0) return Collections.emptyList();

        // Aggregate BM25-style scores per document
        Map<String, Float> docScores = new HashMap<>();
        int totalDocs = docCount.get();

        for (String token : queryTokens) {
            token = token.trim();
            if (token.length() < MIN_TOKEN_LENGTH) continue;

            Map<String, Integer> postings = keywordIndex.get(token);
            if (postings == null || postings.isEmpty()) continue;

            // IDF: log(1 + (N - df + 0.5) / (df + 0.5))
            int df = postings.size();
            float idf = (float) Math.log(1.0 + (totalDocs - df + 0.5) / (df + 0.5));

            for (Map.Entry<String, Integer> entry : postings.entrySet()) {
                int tf = entry.getValue();
                // BM25-inspired: tf * idf
                float score = tf * idf;
                docScores.merge(entry.getKey(), score, Float::sum);
            }
        }

        if (docScores.isEmpty()) return Collections.emptyList();

        // Sort by score descending, take top-K
        return docScores.entrySet().stream()
                .sorted(Map.Entry.<String, Float>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    StoredVector sv = store.get(e.getKey());
                    Map<String, Object> meta = sv != null ? sv.metadata() : Collections.emptyMap();
                    return new SimilarityResult(e.getKey(), e.getValue(), meta);
                })
                .toList();
    }

    /**
     * Hybrid search combining vector similarity and keyword relevance
     * using Reciprocal Rank Fusion (RRF).
     *
     * <p>RRF formula: score(d) = Σ 1/(k + rank_i(d))
     * where k = 60 (standard constant) and rank_i is the rank of document d
     * in the i-th result list.
     *
     * <p>This approach requires no score normalization and works well
     * with heterogeneous relevance signals (dense vectors + sparse keywords).
     *
     * @param queryEmbedding  the embedding vector for semantic search
     * @param queryText       the natural-language query for keyword search
     * @param topK            max results to return
     * @param minVectorScore  minimum cosine similarity for vector results
     * @param vectorWeight    weight of vector scores in fusion (0.0–1.0)
     * @return fused results sorted by RRF score descending
     */
    public List<SimilarityResult> searchHybrid(float[] queryEmbedding,
                                                String queryText,
                                                int topK,
                                                float minVectorScore,
                                                float vectorWeight) {
        // Run both searches in parallel (conceptually — single-threaded here for simplicity)
        List<SimilarityResult> vectorResults = searchSimilar(queryEmbedding,
                Math.max(topK * 2, 50), minVectorScore);
        List<SimilarityResult> keywordResults = searchByKeyword(queryText,
                Math.max(topK * 2, 50));

        if (vectorResults.isEmpty() && keywordResults.isEmpty()) {
            return Collections.emptyList();
        }

        // ── Reciprocal Rank Fusion ──
        final double k = 60.0; // RRF constant
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Set<String> seenIds = new LinkedHashSet<>();

        // Fuse vector ranks
        for (int i = 0; i < vectorResults.size(); i++) {
            String id = vectorResults.get(i).characterId();
            double rrf = vectorWeight / (k + i + 1);
            rrfScores.merge(id, rrf, Double::sum);
            seenIds.add(id);
        }

        // Fuse keyword ranks
        float keywordWeight = 1.0f - vectorWeight;
        for (int i = 0; i < keywordResults.size(); i++) {
            String id = keywordResults.get(i).characterId();
            double rrf = keywordWeight / (k + i + 1);
            rrfScores.merge(id, rrf, Double::sum);
            seenIds.add(id);
        }

        // Build results with metadata from the store
        List<SimilarityResult> fused = new ArrayList<>();
        for (String id : seenIds) {
            Double score = rrfScores.get(id);
            if (score == null) continue;

            StoredVector sv = store.get(id);
            if (sv == null) continue;

            fused.add(new SimilarityResult(
                    id,
                    (float) (double) score,
                    Collections.unmodifiableMap(sv.metadata())));
        }

        // Sort by RRF score descending, take top-K
        fused.sort((a, b) -> Float.compare(b.score(), a.score()));
        if (fused.size() > topK) {
            return fused.subList(0, topK);
        }
        return fused;
    }

    /**
     * Delete a character's vector from the store.
     */
    public void deleteCharacter(String characterId) {
        StoredVector removed = store.remove(characterId);
        if (removed != null) {
            log.debug("InMemoryVectorStore: removed vector for '{}'", characterId);
        }
    }

    /**
     * Check if a character is already stored.
     */
    public boolean exists(String characterId) {
        return store.containsKey(characterId);
    }

    /**
     * Get all stored character IDs.
     */
    public Set<String> getAllCharacterIds() {
        return Collections.unmodifiableSet(store.keySet());
    }

    /**
     * Number of stored vectors.
     */
    public int size() {
        return store.size();
    }

    /**
     * Clear all stored vectors and keyword index.
     */
    public void clear() {
        int vectorCount = store.size();
        int keywordCount = keywordIndex.size();
        store.clear();
        keywordIndex.clear();
        docCount.set(0);
        log.info("InMemoryVectorStore: cleared {} vectors and {} keyword entries", vectorCount, keywordCount);
    }

    /**
     * Get keyword index statistics for monitoring.
     */
    public Map<String, Object> getKeywordIndexStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("uniqueTokens", keywordIndex.size());
        stats.put("totalDocs", docCount.get());
        stats.put("avgPostingsPerToken",
                keywordIndex.values().stream()
                        .mapToInt(Map::size)
                        .average()
                        .orElse(0));
        return stats;
    }

    /**
     * Get the embedding for a stored character (for debugging/testing).
     */
    public Optional<float[]> getEmbedding(String characterId) {
        StoredVector sv = store.get(characterId);
        return sv != null ? Optional.of(sv.embedding.clone()) : Optional.empty();
    }

    // ── Inner class ──────────────────────────────────────

    /**
     * A stored vector entry with its embedding and metadata.
     */
    private record StoredVector(float[] embedding, Map<String, Object> metadata) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StoredVector that)) return false;
            return Arrays.equals(embedding, that.embedding)
                    && Objects.equals(metadata, that.metadata);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(embedding) + Objects.hashCode(metadata);
        }
    }
}
