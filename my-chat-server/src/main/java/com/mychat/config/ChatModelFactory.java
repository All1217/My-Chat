package com.mychat.config;

import com.mychat.entity.po.LlmModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 按目录行构建 OpenAiChatModel（Spring AI 2.0：options 上设 baseUrl / apiKey / model）。
 */
@Component
public class ChatModelFactory {

    private final OpenAiChatModel baseChatModel;

    /** YAML 自动配置的基座，用于兜底和复用 extra-body / 超时。 */
    public ChatModelFactory(OpenAiChatModel baseChatModel) {
        this.baseChatModel = baseChatModel;
    }

    /** 返回 YAML 基座模型，表空或种子未就绪时兜底。 */
    public OpenAiChatModel baseModel() {
        return baseChatModel;
    }

    /** 用目录行的 url / key / model 新建一个 ChatModel。 */
    public OpenAiChatModel build(LlmModel model) {
        return OpenAiChatModel.builder()
                .options(buildOptions(model))
                .build();
    }

    /** 写入连接参数与生成上限，并尽量保留 YAML 的 extra-body（如 thinking:disabled）。 */
    private OpenAiChatOptions buildOptions(LlmModel model) {
        OpenAiChatOptions base = baseChatModel.getOptions();
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .baseUrl(model.getBaseUrl())
                .apiKey(model.getApiKey())
                .model(model.getModelId())
                .maxTokens(model.getMaxTokens());
        if (base != null && base.getTimeout() != null) {
            builder.timeout(base.getTimeout());
        } else {
            builder.timeout(Duration.ofMinutes(10));
        }
        if (base != null && base.getExtraBody() != null) {
            builder.extraBody(base.getExtraBody());
        } else {
            builder.extraBody(Map.of("thinking", Map.of("type", "disabled")));
        }
        return builder.build();
    }
}
