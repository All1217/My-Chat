package com.mychat.entity.dto;

import lombok.Data;

/**
 * 召回测试的单条命中：摘要、正文、分数、来源文档。
 */
@Data
public class KnowledgeRetrieveHit {

    /** 片段正文 */
    private String text;

    /** 相似度分数，越高越相关 */
    private Double score;

    /** 来源文件名 */
    private String filename;

    /** 文档元数据 ID */
    private String documentId;

    /** 入库时生成的 chunk 摘要；旧向量可能为空 */
    private String summary;
}
