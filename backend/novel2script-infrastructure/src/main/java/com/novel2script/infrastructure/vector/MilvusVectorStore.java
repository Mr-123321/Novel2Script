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
 * <p>In production, this would be replaced with the real Milvus client
 * (e.g., {@code io.milvus:milvus-sdk-java}). The API is designed to be
 * compatible with a future Milvus migration.
 */
@Slf4j
@Service
public class MilvusVectorStore {

    /**
     * Internal storage: characterId → stored vector entry.
     */
    private final Map<String, StoredVector> store = new ConcurrentHashMap<>();

    /**
     * Insert a character embedding into the store.
     *
     * @param characterId unique identifier for the character
     * @param embedding   the embedding vector
     * @param metadata    optional key-value metadata
     */
    public void insertCharacter(String characterId, float[] embedding,
                                 Map<String, Object> metadata) {
        if (characterId == null || characterId.isBlank()) {
            log.warn("MilvusVectorStore: insertCharacter called with blank characterId");
            return;
        }
        if (embedding == null || embedding.length == 0) {
            log.warn("MilvusVectorStore: insertCharacter called with empty embedding for '{}'",
                    characterId);
            return;
        }

        store.put(characterId, new StoredVector(
                embedding.clone(),
                metadata != null ? new HashMap<>(metadata) : new HashMap<>()));

        log.debug("MilvusVectorStore: inserted vector for '{}', dim={}", characterId, embedding.length);
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
     * Delete a character's vector from the store.
     */
    public void deleteCharacter(String characterId) {
        StoredVector removed = store.remove(characterId);
        if (removed != null) {
            log.debug("MilvusVectorStore: removed vector for '{}'", characterId);
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
     * Clear all stored vectors.
     */
    public void clear() {
        int count = store.size();
        store.clear();
        log.info("MilvusVectorStore: cleared {} vectors", count);
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
