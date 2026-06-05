package com.novel2script.domain.vector;

import java.util.Collections;
import java.util.Map;

/**
 * Result of a vector similarity search.
 *
 * @param characterId  the identifier of the matching character
 * @param score        cosine similarity score (0.0 – 1.0, higher = more similar)
 * @param metadata     optional metadata attached to the stored vector
 */
public record SimilarityResult(
        String characterId,
        float score,
        Map<String, Object> metadata
) {

    public SimilarityResult {
        if (metadata == null) {
            metadata = Collections.emptyMap();
        }
    }

    /**
     * Convenience constructor without metadata.
     */
    public SimilarityResult(String characterId, float score) {
        this(characterId, score, Collections.emptyMap());
    }

    /**
     * Whether this result meets the minimum confidence threshold.
     */
    public boolean meetsThreshold(float threshold) {
        return score >= threshold;
    }

    @Override
    public String toString() {
        return String.format("SimilarityResult{id='%s', score=%.4f}", characterId, score);
    }
}
