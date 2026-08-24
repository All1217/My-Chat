package com.mychat.entity.dto;

import lombok.Data;

/**
 * 召回测试入参：query 必填；topK / 阈值缺省则用知识库已存值，不写库。
 */
@Data
public class KnowledgeRetrieveTestRequest {

    /** 知识库 ID */
    private String kbId;

    /** 模拟用户问题 */
    private String query;

    /** 本次返回条数；不传则用库设置 */
    private Integer topK;

    /** 本次相似度下限；不传则用库设置 */
    private Double similarityThreshold;
}
