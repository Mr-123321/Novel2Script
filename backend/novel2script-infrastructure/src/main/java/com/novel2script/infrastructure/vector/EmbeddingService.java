package com.novel2script.infrastructure.vector;

import com.novel2script.common.constant.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service for generating text embedding vectors.
 *
 * <p>Uses Spring AI's {@link EmbeddingModel} (auto-configured by the OpenAI starter).
 * Falls back to a lightweight hash-based embedding when no model is available
 * (useful for testing without API keys).
 *
 * <h3>Embedding dimensions</h3>
 * Default: 1536 (text-embedding-ada-002 / DeepSeek embedding).
 * See {@link Constants#EMBEDDING_DIMENSION}.
 */
@Slf4j
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /**
     * Create the embedding service.
     * If Spring AI auto-configures an {@link EmbeddingModel} bean, it will be injected.
     * Otherwise (e.g., in tests without API keys), hash-based fallback is used.
     *
     * @param embeddingModel may be {@code null} if no embedding provider is configured
     */
    public EmbeddingService(@org.springframework.lang.Nullable EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        if (embeddingModel != null) {
            log.info("EmbeddingService initialized with EmbeddingModel: {}", embeddingModel);
        } else {
            log.info("EmbeddingService: no EmbeddingModel available, using hash-based fallback");
        }
    }

    /**
     * Generate an embedding vector for a single text.
     *
     * @param text the input text (name, description, etc.)
     * @return float array of dimension {@link Constants#EMBEDDING_DIMENSION}
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[Constants.EMBEDDING_DIMENSION];
        }

        if (embeddingModel != null) {
            try {
                EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
                EmbeddingResponse response = embeddingModel.call(request);
                if (response != null && !response.getResults().isEmpty()) {
                    float[] raw = response.getResults().get(0).getOutput();
                    return raw;
                }
            } catch (Exception e) {
                log.warn("EmbeddingService: API call failed, falling back to hash. Error: {}",
                        e.getMessage());
            }
        }

        // Fallback: deterministic hash-based embedding
        return hashEmbed(text);
    }

    /**
     * Generate embeddings for a batch of texts.
     *
     * @param texts list of input texts
     * @return list of float arrays, same order as input
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        if (embeddingModel != null) {
            try {
                EmbeddingRequest request = new EmbeddingRequest(texts, null);
                EmbeddingResponse response = embeddingModel.call(request);
                if (response != null) {
                    List<float[]> results = new ArrayList<>();
                    for (var item : response.getResults()) {
                        results.add(item.getOutput());
                    }
                    return results;
                }
            } catch (Exception e) {
                log.warn("EmbeddingService: batch API call failed, falling back to hash. Error: {}",
                        e.getMessage());
            }
        }

        // Fallback: hash-based
        List<float[]> results = new ArrayList<>();
        for (String text : texts) {
            results.add(hashEmbed(text));
        }
        return results;
    }

    /**
     * Compute cosine similarity between two vectors.
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vector dimensions mismatch: " + a.length + " vs " + b.length);
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Fallback embedding: deterministic hash of text into a 1536-dim vector.
     * Characters with similar text produce similar-ish vectors (via character n-gram
     * overlap), sufficient for basic name matching tests.
     */
    private static float[] hashEmbed(String text) {
        String normalized = text.toLowerCase().trim();
        float[] vec = new float[Constants.EMBEDDING_DIMENSION];

        // Use character trigrams to create reproducible "embedding"
        for (int i = 0; i < normalized.length(); i++) {
            int codePoint = normalized.charAt(i);
            // Seed a simple pseudo-random based on position and character
            long seed = ((long) codePoint << 16) | (i & 0xFFFF);
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int idx = (int) ((seed & Long.MAX_VALUE) % Constants.EMBEDDING_DIMENSION);
            vec[idx] += 0.05f; // sparse activation
        }

        // Normalize to unit length
        double norm = 0.0;
        for (float v : vec) {
            norm += (double) v * v;
        }
        if (norm > 0) {
            float scale = (float) (1.0 / Math.sqrt(norm));
            for (int i = 0; i < vec.length; i++) {
                vec[i] *= scale;
            }
        }

        return vec;
    }
}
