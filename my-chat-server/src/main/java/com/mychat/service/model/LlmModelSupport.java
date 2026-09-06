package com.mychat.service.model;

import com.mychat.config.LlmProviderPresets;
import com.mychat.entity.po.LlmModel;
import com.mychat.vo.LlmModelVO;
import org.springframework.util.StringUtils;

/** 模型配置的默认值、校验与脱敏。 */
public final class LlmModelSupport {

    public static final int DEFAULT_MAX_TOKENS = 8192;
    public static final int MIN_MAX_TOKENS = 256;
    public static final int MAX_MAX_TOKENS = 128000;

    private LlmModelSupport() {
    }

    /** 补齐 enabled / maxTokens / provider。 */
    public static void applyDefaults(LlmModel model) {
        if (model.getMaxTokens() == null) {
            model.setMaxTokens(DEFAULT_MAX_TOKENS);
        }
        if (model.getEnabled() == null) {
            model.setEnabled(true);
        }
        if (model.getIsDefault() == null) {
            model.setIsDefault(false);
        }
        if (!StringUtils.hasText(model.getProvider())) {
            model.setProvider(LlmProviderPresets.inferProvider(model.getBaseUrl()));
        }
    }

    /** 校验必填与取值范围。 */
    public static void validate(LlmModel model) {
        applyDefaults(model);
        if (!StringUtils.hasText(model.getName())) {
            throw new IllegalArgumentException("名称不能为空");
        }
        if (!LlmProviderPresets.isKnown(model.getProvider())) {
            throw new IllegalArgumentException("不支持的供应商: " + model.getProvider());
        }
        if (!StringUtils.hasText(model.getBaseUrl())) {
            throw new IllegalArgumentException("Base URL 不能为空");
        }
        if (!StringUtils.hasText(model.getApiKey())) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        if (!StringUtils.hasText(model.getModelId())) {
            throw new IllegalArgumentException("模型 ID 不能为空");
        }
        int tokens = model.getMaxTokens();
        if (tokens < MIN_MAX_TOKENS || tokens > MAX_MAX_TOKENS) {
            throw new IllegalArgumentException("maxTokens 须在 " + MIN_MAX_TOKENS + "–" + MAX_MAX_TOKENS + " 之间");
        }
        model.setName(model.getName().trim());
        model.setProvider(model.getProvider().trim().toLowerCase());
        model.setBaseUrl(trimSlash(model.getBaseUrl().trim()));
        model.setModelId(model.getModelId().trim());
    }

    /** 列表/详情用：密钥只留头尾。 */
    public static String maskApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        String key = apiKey.trim();
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 3) + "****" + key.substring(key.length() - 4);
    }

    /** 实体转脱敏 VO。 */
    public static LlmModelVO toVo(LlmModel model) {
        LlmModelVO vo = new LlmModelVO();
        vo.setId(model.getId());
        vo.setName(model.getName());
        vo.setProvider(model.getProvider());
        vo.setBaseUrl(model.getBaseUrl());
        vo.setApiKeyMasked(maskApiKey(model.getApiKey()));
        vo.setModelId(model.getModelId());
        vo.setMaxTokens(model.getMaxTokens());
        vo.setEnabled(Boolean.TRUE.equals(model.getEnabled()));
        vo.setIsDefault(Boolean.TRUE.equals(model.getIsDefault()));
        vo.setCreatedAt(model.getCreatedAt());
        vo.setUpdatedAt(model.getUpdatedAt());
        return vo;
    }

    /** 去掉末尾多余斜杠，避免和 Spring AI 拼接路径重复。 */
    public static String trimSlash(String baseUrl) {
        if (baseUrl == null) {
            return null;
        }
        String u = baseUrl;
        while (u.endsWith("/") && u.length() > 1) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
