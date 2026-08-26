package com.mychat.entity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 某文档的只读分段列表，带文件名方便抽屉标题。
 */
@Data
public class DocumentChunkListResponse {

    /** 文档 ID */
    private String documentId;

    /** 原始文件名 */
    private String filename;

    /** 按 position 升序的切段 */
    private List<DocumentChunkItem> chunks = new ArrayList<>();
}
