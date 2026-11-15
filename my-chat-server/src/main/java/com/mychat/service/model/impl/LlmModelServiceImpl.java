package com.mychat.service.model.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mychat.config.CacheConfiguration;
import com.mychat.config.ChatClientAssembler;
import com.mychat.config.ChatClientRegistry;
import com.mychat.config.ChatModelFactory;
import com.mychat.config.LlmProviderPresets;
import com.mychat.config.OpenAiApiKeyResolver;
import com.mychat.entity.dto.LlmModelTestRequest;
import com.mychat.entity.dto.LlmModelUpsertRequest;
import com.mychat.entity.po.LlmModel;
import com.mychat.mapper.LlmModelMapper;
import com.mychat.service.model.LlmModelService;
import com.mychat.service.model.LlmModelSupport;
import com.mychat.vo.LlmModelTestResultVO;
import com.mychat.vo.LlmModelVO;
import com.mychat.vo.LlmProviderPresetVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * 模型目录持久化与测通；改默认后通过 Registry 热切换三个 ChatClient。
 */
@Slf4j
@Service
public class LlmModelServiceImpl extends ServiceImpl<LlmModelMapper, LlmModel> implements LlmModelService {

    private final LlmModelMapper llmModelMapper;
    private final ChatModelFactory chatModelFactory;
    private final ChatClientAssembler chatClientAssembler;
    private final ObjectProvider<ChatClientRegistry> chatClientRegistry;
    private final OpenAiApiKeyResolver apiKeyResolver;

    /** Registry 用 ObjectProvider，打破与缓存层的循环依赖。 */
    public LlmModelServiceImpl(LlmModelMapper llmModelMapper,
                               ChatModelFactory chatModelFactory,
                               ChatClientAssembler chatClientAssembler,
                               ObjectProvider<ChatClientRegistry> chatClientRegistry,
                               OpenAiApiKeyResolver apiKeyResolver) {
        this.llmModelMapper = llmModelMapper;
        this.chatModelFactory = chatModelFactory;
        this.chatClientAssembler = chatClientAssembler;
        this.chatClientRegistry = chatClientRegistry;
        this.apiKeyResolver = apiKeyResolver;
    }

    @Override
    @Cacheable(cacheNames = CacheConfiguration.LLM_MODELS, key = "'list'")
    public List<LlmModelVO> listMasked() {
        return llmModelMapper.selectList(new LambdaQueryWrapper<LlmModel>()
                        .orderByDesc(LlmModel::getIsDefault)
                        .orderByDesc(LlmModel::getUpdatedAt))
                .stream()
                .map(LlmModelSupport::toVo)
                .toList();
    }

    @Override
    public List<LlmProviderPresetVO> listProviders() {
        return LlmProviderPresets.all().stream()
                .map(p -> new LlmProviderPresetVO(p.key(), p.label(), p.baseUrl(), p.modelHints()))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheConfiguration.LLM_MODELS, allEntries = true)
    public LlmModelVO create(LlmModelUpsertRequest request) {
        LlmModel model = new LlmModel();
        model.setId(UUID.randomUUID().toString());
        model.setName(request.getName());
        model.setProvider(request.getProvider());
        model.setBaseUrl(request.getBaseUrl());
        model.setApiKey(resolveStoredApiKey(request.getApiKey()));
        model.setModelId(request.getModelId());
        model.setMaxTokens(request.getMaxTokens());
        model.setEnabled(request.getEnabled());
        LlmModelSupport.validate(model);

        boolean empty = llmModelMapper.selectCount(new LambdaQueryWrapper<>()) == 0;
        boolean makeDefault = empty || Boolean.TRUE.equals(request.getIsDefault());
        if (makeDefault && Boolean.TRUE.equals(model.getEnabled())) {
            clearDefaultFlags();
            model.setIsDefault(true);
        } else {
            model.setIsDefault(false);
        }
        llmModelMapper.insert(model);
        if (Boolean.TRUE.equals(model.getIsDefault())) {
            evictRegistry();
        }
        log.info("Created llm model {} provider={} modelId={}", model.getId(), model.getProvider(), model.getModelId());
        return LlmModelSupport.toVo(llmModelMapper.selectById(model.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheConfiguration.LLM_MODELS, allEntries = true)
    public LlmModelVO updateModel(LlmModelUpsertRequest request) {
        if (request == null || !StringUtils.hasText(request.getId())) {
            throw new IllegalArgumentException("模型 ID 不能为空");
        }
        LlmModel existing = requireById(request.getId().trim());
        boolean wasDefault = Boolean.TRUE.equals(existing.getIsDefault());

        if (request.getName() != null) {
            existing.setName(request.getName());
        }
        if (request.getProvider() != null) {
            existing.setProvider(request.getProvider());
        }
        if (request.getBaseUrl() != null) {
            existing.setBaseUrl(request.getBaseUrl());
        }
        // 请求带了 apiKey 字段：有值则写入；空白则清空，运行时读 OPENAI_API_KEY / YAML
        if (request.getApiKey() != null) {
            existing.setApiKey(resolveStoredApiKey(request.getApiKey()));
        }
        if (request.getModelId() != null) {
            existing.setModelId(request.getModelId());
        }
        if (request.getMaxTokens() != null) {
            existing.setMaxTokens(request.getMaxTokens());
        }
        if (request.getEnabled() != null) {
            if (!request.getEnabled() && wasDefault) {
                throw new IllegalArgumentException("不能禁用当前默认模型，请先指定其它默认可再禁用");
            }
            existing.setEnabled(request.getEnabled());
        }
        LlmModelSupport.validate(existing);
        llmModelMapper.updateById(existing);
        if (wasDefault) {
            evictRegistry();
        }
        log.info("Updated llm model {}", existing.getId());
        return LlmModelSupport.toVo(llmModelMapper.selectById(existing.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheConfiguration.LLM_MODELS, allEntries = true)
    public void deleteModel(String id) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("模型 ID 不能为空");
        }
        LlmModel existing = requireById(id.trim());
        if (Boolean.TRUE.equals(existing.getIsDefault())) {
            throw new IllegalArgumentException("不能删除当前默认模型，请先指定其它默认");
        }
        long enabled = llmModelMapper.selectCount(new LambdaQueryWrapper<LlmModel>()
                .eq(LlmModel::getEnabled, true));
        if (Boolean.TRUE.equals(existing.getEnabled()) && enabled <= 1) {
            throw new IllegalArgumentException("不能删除最后一条启用中的模型");
        }
        llmModelMapper.deleteById(existing.getId());
        log.info("Deleted llm model {}", existing.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheConfiguration.LLM_MODELS, allEntries = true)
    public LlmModelVO setDefault(String id) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("模型 ID 不能为空");
        }
        LlmModel target = requireById(id.trim());
        if (!Boolean.TRUE.equals(target.getEnabled())) {
            throw new IllegalArgumentException("只能把启用中的模型设为默认");
        }
        clearDefaultFlags();
        target.setIsDefault(true);
        llmModelMapper.updateById(target);
        evictRegistry();
        log.info("Set default llm model {} modelId={}", target.getId(), target.getModelId());
        return LlmModelSupport.toVo(llmModelMapper.selectById(target.getId()));
    }

    @Override
    public LlmModelTestResultVO testConnection(LlmModelTestRequest request) {
        LlmModel probe = resolveProbe(request);
        LlmModelSupport.validate(probe);
        try {
            ChatClient client = chatClientAssembler.workflow(chatModelFactory.build(probe));
            String reply = client.prompt()
                    .user("Reply with exactly: ok")
                    .call()
                    .content();
            String text = reply == null ? "" : reply.trim();
            if (text.length() > 200) {
                text = text.substring(0, 200) + "…";
            }
            return new LlmModelTestResultVO(true, "连接成功", text);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("模型测通失败 modelId={}: {}", probe.getModelId(), msg);
            return new LlmModelTestResultVO(false, msg, null);
        }
    }

    /** 已存配置或表单字段拼成一条临时 LlmModel，供测通。 */
    private LlmModel resolveProbe(LlmModelTestRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("测通参数不能为空");
        }
        if (StringUtils.hasText(request.getId())) {
            LlmModel stored = requireById(request.getId().trim());
            if (request.getApiKey() != null) {
                // 空白 = 测「跟随环境变量」；非空 = 用表单临时 key（不落库）
                stored.setApiKey(request.getApiKey().trim());
            }
            if (StringUtils.hasText(request.getBaseUrl())) {
                stored.setBaseUrl(request.getBaseUrl());
            }
            if (StringUtils.hasText(request.getModelId())) {
                stored.setModelId(request.getModelId());
            }
            if (request.getMaxTokens() != null) {
                stored.setMaxTokens(request.getMaxTokens());
            }
            return stored;
        }
        LlmModel draft = new LlmModel();
        draft.setName("probe");
        draft.setProvider(request.getProvider());
        draft.setBaseUrl(request.getBaseUrl());
        draft.setApiKey(request.getApiKey() != null ? request.getApiKey().trim() : "");
        draft.setModelId(request.getModelId());
        draft.setMaxTokens(request.getMaxTokens());
        draft.setEnabled(true);
        return draft;
    }

    /**
     * 持久化用的 api_key：有值原样存；留空则存空串并确认环境变量 / YAML 可用。
     */
    private String resolveStoredApiKey(String raw) {
        if (StringUtils.hasText(raw)) {
            return raw.trim();
        }
        apiKeyResolver.requireFallback();
        return "";
    }

    /** 按 ID 取行，不存在则 400。 */
    private LlmModel requireById(String id) {
        LlmModel model = llmModelMapper.selectById(id);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在");
        }
        return model;
    }

    /** 先清掉其它行的默认标记。 */
    private void clearDefaultFlags() {
        llmModelMapper.update(null, new LambdaUpdateWrapper<LlmModel>()
                .set(LlmModel::getIsDefault, false)
                .eq(LlmModel::getIsDefault, true));
    }

    /** 通知运行时丢掉旧 ChatClient。 */
    private void evictRegistry() {
        ChatClientRegistry registry = chatClientRegistry.getIfAvailable();
        if (registry != null) {
            registry.evict();
        }
    }
}
