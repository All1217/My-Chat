package com.mychat.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mychat.entity.po.LlmModel;
import com.mychat.mapper.LlmModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;

/**
 * 按当前默认模型缓存三个能力层 ChatClient；默认变更后 evict 即可热切换。
 */
@Component
public class ChatClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChatClientRegistry.class);
    private static final String YAML_FALLBACK_ID = "yaml";

    private final LlmModelMapper llmModelMapper;
    private final ChatModelFactory chatModelFactory;
    private final ChatClientAssembler assembler;

    private final Object lock = new Object();
    private volatile Cached cached;

    /** 直接读 Mapper，避免与 LlmModelService 循环依赖。 */
    public ChatClientRegistry(LlmModelMapper llmModelMapper,
                              ChatModelFactory chatModelFactory,
                              ChatClientAssembler assembler) {
        this.llmModelMapper = llmModelMapper;
        this.chatModelFactory = chatModelFactory;
        this.assembler = assembler;
    }

    /** 当前默认的工具客户端。 */
    public ChatClient tool() {
        return current().tool;
    }

    /** 当前默认的 RAG 客户端。 */
    public ChatClient rag() {
        return current().rag;
    }

    /** 当前默认的编排客户端。 */
    public ChatClient workflow() {
        return current().workflow;
    }

    /** 默认切换或默认行关键字段变更后丢弃缓存。 */
    public void evict() {
        cached = null;
        log.info("ChatClient 缓存已清空，下次调用按新默认重建");
    }

    /** 取缓存或按默认行（无则 YAML 兜底）重建三个 Client。 */
    private Cached current() {
        LlmModel defaultModel = findDefault();
        String key = defaultModel != null ? defaultModel.getId() : YAML_FALLBACK_ID;
        Cached local = cached;
        if (local != null && key.equals(local.modelId)) {
            return local;
        }
        synchronized (lock) {
            local = cached;
            if (local != null && key.equals(local.modelId)) {
                return local;
            }
            OpenAiChatModel chatModel = defaultModel != null
                    ? chatModelFactory.build(defaultModel)
                    : chatModelFactory.baseModel();
            Cached created = new Cached(
                    key,
                    assembler.tool(chatModel),
                    assembler.rag(chatModel),
                    assembler.workflow(chatModel));
            cached = created;
            if (defaultModel != null) {
                log.info("已按默认模型重建 ChatClient: name={} modelId={}",
                        defaultModel.getName(), defaultModel.getModelId());
            } else {
                log.warn("llm_model 无默认行，使用 YAML OpenAiChatModel 兜底");
            }
            return created;
        }
    }

    /** 查启用中的默认行；表未建时返回 null，走 YAML 兜底。 */
    private LlmModel findDefault() {
        try {
            return llmModelMapper.selectOne(new LambdaQueryWrapper<LlmModel>()
                    .eq(LlmModel::getIsDefault, true)
                    .eq(LlmModel::getEnabled, true)
                    .last("LIMIT 1"));
        } catch (Exception e) {
            log.warn("读取默认模型失败，回退 YAML：{}", e.getMessage());
            return null;
        }
    }

    /** 一组与某模型 id 绑定的三个 Client。 */
    private record Cached(String modelId, ChatClient tool, ChatClient rag, ChatClient workflow) {
    }
}
