package com.mychat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 对话 API Key 解析：显式值优先，否则环境变量 {@code OPENAI_API_KEY}，再 {@code spring.ai.openai.api-key}。
 * 库中留空表示跟随部署环境（改 .env 后 recreate 即可，不必改 llm_model）。
 */
@Component
public class OpenAiApiKeyResolver {

    public static final String MISSING_KEY_MESSAGE =
            "API Key 未配置：请在设置中填写，或设置环境变量 OPENAI_API_KEY，或在 application.yaml 配置 spring.ai.openai.api-key";

    private final String yamlApiKey;

    public OpenAiApiKeyResolver(@Value("${spring.ai.openai.api-key:}") String yamlApiKey) {
        this.yamlApiKey = yamlApiKey != null ? yamlApiKey : "";
    }

    /**
     * @param storedOrBlank 目录行里的 key；空白则走环境变量 → YAML
     * @return 非空有效 key
     * @throws IllegalArgumentException 三级都没有时，提示去补充
     */
    public String resolve(String storedOrBlank) {
        if (StringUtils.hasText(storedOrBlank)) {
            return storedOrBlank.trim();
        }
        String env = System.getenv("OPENAI_API_KEY");
        if (StringUtils.hasText(env)) {
            return env.trim();
        }
        if (StringUtils.hasText(yamlApiKey)) {
            return yamlApiKey.trim();
        }
        throw new IllegalArgumentException(MISSING_KEY_MESSAGE);
    }

    /** 保存「留空」前确认至少有一层回退，否则直接报错。 */
    public void requireFallback() {
        resolve("");
    }
}
