package com.mychat.entity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 召回测试命中，以及聊天引用（来源文件名）共用的单条结构。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KnowledgeRetrieveHit {

    /** 向量命中引用 */
    public static final String KIND_CHUNK = "chunk";

    /** 文档目录兜底引用（总览问 / 0 hit） */
    public static final String KIND_CATALOG = "catalog";

    /** 片段正文（聊天引用侧会截短） */
    private String text;

    /** 相似度分数，越高越相关；目录引用为空 */
    private Double score;

    /** 来源文件名 */
    private String filename;

    /** 文档元数据 ID */
    private String documentId;

    /** 入库时生成的 chunk 摘要；旧向量可能为空 */
    private String summary;

    /**
     * 引用来源类型：{@link #KIND_CHUNK} 或 {@link #KIND_CATALOG}。
     * 召回测试可不填，缺省视为 chunk。
     */
    private String kind;
}
