package com.mychat.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 结构化输出解析与单次 Schema 修复服务。 */
@Component
public class StructuredOutputRepairService {

    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    /** 注入 JSON 编解码器和无工具修复客户端。 */
    public StructuredOutputRepairService(
            ObjectMapper objectMapper,
            @Qualifier("agentWorkflowChatClient") ChatClient chatClient) {
        this.objectMapper = objectMapper;
        this.chatClient = chatClient;
    }

    /** 优先直接解析，失败后让模型按目标类型修复一次。 */
    public <T> T parseOrRepair(String raw, Class<T> outputType, String schemaRules) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("结构化输出为空");
        }
        try {
            return objectMapper.readValue(raw, outputType);
        } catch (Exception firstFailure) {
            T repaired = chatClient.prompt()
                    .system("""
                            你是 JSON Schema 修复器。只修复结构和字段类型，不新增事实。
                            证据引用、代码、价格、数量和评分不得凭空生成。
                            """)
                    .user("Schema 规则：\n" + schemaRules + "\n\n待修复 JSON：\n" + raw)
                    .call()
                    .entity(outputType);
            if (repaired == null) {
                throw new IllegalStateException(
                        "结构化输出修复失败: " + firstFailure.getMessage(), firstFailure);
            }
            return repaired;
        }
    }
}
