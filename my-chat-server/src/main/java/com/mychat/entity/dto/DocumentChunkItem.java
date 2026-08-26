package com.mychat.entity.dto;

import lombok.Data;

/**
 * 只读分段列表的单条：位置、原文、摘要（无检索分数）。
 */
@Data
public class DocumentChunkItem {

    /** 切段下标，从 0 起 */
    private int position;

    /** 切段原文 */
    private String content;

    /** chunk 摘要；旧文档或摘要失败可空 */
    private String summary;
}
