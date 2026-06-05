package com.novel2script.common.constant;

/**
 * Application-wide constants.
 */
public final class Constants {

    private Constants() {
        // utility class — prevent instantiation
    }

    /** Application name */
    public static final String APP_NAME = "novel2script";

    /** API version prefix */
    public static final String API_V1 = "/api/v1";

    /** Maximum novel file size in bytes (10 MB) */
    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /** Maximum novel length in characters (1,000,000 ≈ 中文 50 万字) */
    public static final int MAX_NOVEL_CHARS = 1_000_000;

    /** Minimum chapter count to trigger processing */
    public static final int MIN_CHAPTER_COUNT = 3;

    /** Default embedding dimension (DeepSeek / OpenAI compatible) */
    public static final int EMBEDDING_DIMENSION = 1536;

    /** Default similarity threshold for character dedup */
    public static final float DEFAULT_SIMILARITY_THRESHOLD = 0.85f;

    /** Default top-K for vector search */
    public static final int DEFAULT_TOP_K = 10;

    /** Milvus collection names */
    public static final String COLLECTION_CHARACTERS = "character_embeddings";
    public static final String COLLECTION_CHAPTERS = "chapter_embeddings";
    public static final String COLLECTION_SCENES = "scene_embeddings";

    /** Default page size */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Maximum page size */
    public static final int MAX_PAGE_SIZE = 100;
}
