package com.mychat.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 测通结果：成功时带截断回复。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmModelTestResultVO {
    private boolean ok;
    private String message;
    private String reply;
}
