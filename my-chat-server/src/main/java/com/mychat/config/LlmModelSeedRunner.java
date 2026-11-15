package com.mychat.config;

import com.mychat.entity.po.LlmModel;
import com.mychat.mapper.LlmModelMapper;
import com.mychat.service.model.LlmModelSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/** 空表时用 YAML 的 spring.ai.openai 写入一条默认模型，保证未打开设置页也能聊。 */
@Component
@Order(1)
public class LlmModelSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LlmModelSeedRunner.class);

    private final LlmModelMapper llmModelMapper;

    @Value("${spring.ai.openai.base-url:}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String modelId;

    @Value("${spring.ai.openai.chat.options.max-tokens:8192}")
    private Integer maxTokens;

    /** 只依赖 Mapper，启动最早一批执行。 */
    public LlmModelSeedRunner(LlmModelMapper llmModelMapper) {
        this.llmModelMapper = llmModelMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long count;
        try {
            count = llmModelMapper.selectCount(null);
        } catch (Exception e) {
            log.warn("llm_model 不可用（请检查 Flyway V1 迁移），跳过种子：{}", e.getMessage());
            return;
        }
        if (count != null && count > 0) {
            return;
        }
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(modelId)) {
            log.warn("llm_model 为空且 YAML 缺少 base-url/model，跳过种子");
            return;
        }
        String provider = LlmProviderPresets.inferProvider(baseUrl);
        LlmModel seed = new LlmModel();
        seed.setId(UUID.randomUUID().toString());
        seed.setName(labelOf(provider) + " (YAML)");
        seed.setProvider(provider);
        seed.setBaseUrl(baseUrl);
        // 库内留空：运行时读 OPENAI_API_KEY / YAML，云上改 .env 即可，不必再改库
        seed.setApiKey("");
        seed.setModelId(modelId);
        seed.setMaxTokens(maxTokens != null ? maxTokens : LlmModelSupport.DEFAULT_MAX_TOKENS);
        seed.setEnabled(true);
        seed.setIsDefault(true);
        LlmModelSupport.applyDefaults(seed);
        llmModelMapper.insert(seed);
        if (!StringUtils.hasText(apiKey) && !StringUtils.hasText(System.getenv("OPENAI_API_KEY"))) {
            log.warn("已种子默认模型但 OPENAI_API_KEY / YAML api-key 均为空，对话前请配置");
        }
        log.info("已从 YAML 种子默认模型 provider={} modelId={}（api_key 跟随环境）", provider, modelId);
    }

    /** 把预设键转成中文/英文展示名。 */
    private static String labelOf(String provider) {
        return LlmProviderPresets.all().stream()
                .filter(p -> p.key().equals(provider))
                .map(LlmProviderPresets.Preset::label)
                .findFirst()
                .orElse(provider);
    }
}
