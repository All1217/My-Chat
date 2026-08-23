package com.mychat.entity.po;

/**
 * 知识库切分/检索参数的默认值与范围校验（入库切分与问答检索共用）。
 */
public final class KnowledgeBaseSettings {

    public static final int DEFAULT_CHUNK_SIZE = 800;
    public static final int DEFAULT_CHUNK_OVERLAP = 0;
    public static final int DEFAULT_TOP_K = 5;
    public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;

    public static final int MIN_CHUNK_SIZE = 64;
    public static final int MAX_CHUNK_SIZE = 4000;
    public static final int MIN_TOP_K = 1;
    public static final int MAX_TOP_K = 20;

    private KnowledgeBaseSettings() {
    }

    /** 缺省补齐四项，避免 INSERT NULL 盖掉数据库 DEFAULT。 */
    public static void applyDefaults(KnowledgeBase kb) {
        if (kb.getChunkSize() == null) {
            kb.setChunkSize(DEFAULT_CHUNK_SIZE);
        }
        if (kb.getChunkOverlap() == null) {
            kb.setChunkOverlap(DEFAULT_CHUNK_OVERLAP);
        }
        if (kb.getTopK() == null) {
            kb.setTopK(DEFAULT_TOP_K);
        }
        if (kb.getSimilarityThreshold() == null) {
            kb.setSimilarityThreshold(DEFAULT_SIMILARITY_THRESHOLD);
        }
    }

    public static int chunkSizeOrDefault(Integer value) {
        return value == null ? DEFAULT_CHUNK_SIZE : value;
    }

    public static int chunkOverlapOrDefault(Integer value) {
        return value == null ? DEFAULT_CHUNK_OVERLAP : value;
    }

    public static int topKOrDefault(Integer value) {
        return value == null ? DEFAULT_TOP_K : value;
    }

    public static double thresholdOrDefault(Double value) {
        return value == null ? DEFAULT_SIMILARITY_THRESHOLD : value;
    }

    /** 校验四项参数；不合法抛 {@link IllegalArgumentException}。 */
    public static void validate(int chunkSize, int chunkOverlap, int topK, double similarityThreshold) {
        if (chunkSize < MIN_CHUNK_SIZE || chunkSize > MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException(
                    "chunkSize 须在 " + MIN_CHUNK_SIZE + "–" + MAX_CHUNK_SIZE + " 之间");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("chunkOverlap 须 ≥ 0 且小于 chunkSize");
        }
        if (topK < MIN_TOP_K || topK > MAX_TOP_K) {
            throw new IllegalArgumentException("topK 须在 " + MIN_TOP_K + "–" + MAX_TOP_K + " 之间");
        }
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("similarityThreshold 须在 0–1 之间");
        }
    }

    /** 对实体上的四字段做校验（先补默认再验）。 */
    public static void validate(KnowledgeBase kb) {
        applyDefaults(kb);
        validate(kb.getChunkSize(), kb.getChunkOverlap(), kb.getTopK(), kb.getSimilarityThreshold());
    }
}
