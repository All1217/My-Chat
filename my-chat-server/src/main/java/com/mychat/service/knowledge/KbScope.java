package com.mychat.service.knowledge;

/**
 * 知识库检索范围：目录清单或向量片段。由编排器 kbScope 决定，不再用关键词猜测。
 */
public enum KbScope {

    /** 文档目录（库级总览） */
    CATALOG,

    /** 向量相似度检索 */
    VECTOR;

    /**
     * 解析编排器输出；仅 catalog 走目录，其余（含空/非法）默认向量。
     */
    public static KbScope from(String raw) {
        if (raw != null && "catalog".equalsIgnoreCase(raw.trim())) {
            return CATALOG;
        }
        return VECTOR;
    }

    /** 写入 NDJSON args.kbScope 的小写标签。 */
    public String wireValue() {
        return this == CATALOG ? "catalog" : "vector";
    }
}
