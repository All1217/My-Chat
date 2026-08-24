package com.mychat.entity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 召回测试结果：实际使用的检索参数与命中片段。
 */
@Data
public class KnowledgeRetrieveTestResponse {

    /** 知识库 ID */
    private String kbId;

    /** 本次实际 topK */
    private int topK;

    /** 本次实际相似度阈值 */
    private double similarityThreshold;

    /** 命中片段，顺序与 VectorStore 一致 */
    private List<KnowledgeRetrieveHit> hits = new ArrayList<>();
}
