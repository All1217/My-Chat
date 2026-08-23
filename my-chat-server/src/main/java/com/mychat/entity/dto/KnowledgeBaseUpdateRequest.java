package com.mychat.entity.dto;

import lombok.Data;

/**
 * 更新知识库名称、描述与切分/检索参数。
 */
@Data
public class KnowledgeBaseUpdateRequest {

    /** 知识库 ID（必填） */
    private String id;

    /** 名称；不传则保持原值 */
    private String name;

    /** 描述；不传则保持原值 */
    private String description;

    /** 入库切分目标 token 数 */
    private Integer chunkSize;

    /** 相邻分片重叠 token 数 */
    private Integer chunkOverlap;

    /** 检索返回片段上限 */
    private Integer topK;

    /** 检索相似度下限 0~1 */
    private Double similarityThreshold;
}
