package com.mychat.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 设置页供应商下拉项。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmProviderPresetVO {
    private String key;
    private String label;
    private String baseUrl;
    private List<String> modelHints;
}
