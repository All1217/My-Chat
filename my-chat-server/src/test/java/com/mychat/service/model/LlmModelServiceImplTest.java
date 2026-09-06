package com.mychat.service.model;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.mychat.config.ChatClientAssembler;
import com.mychat.config.ChatClientRegistry;
import com.mychat.config.ChatModelFactory;
import com.mychat.entity.dto.LlmModelUpsertRequest;
import com.mychat.entity.po.LlmModel;
import com.mychat.mapper.LlmModelMapper;
import com.mychat.service.model.impl.LlmModelServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 删除/设默认的业务约束。 */
class LlmModelServiceImplTest {

    private LlmModelMapper mapper;
    private ChatClientRegistry registry;
    private LlmModelServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(LlmModelMapper.class);
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatClientAssembler assembler = mock(ChatClientAssembler.class);
        registry = mock(ChatClientRegistry.class);
        ObjectProvider<ChatClientRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        service = new LlmModelServiceImpl(mapper, factory, assembler, provider);
    }

    @Test
    void cannotDeleteDefault() {
        LlmModel model = row("a", true, true);
        when(mapper.selectById("a")).thenReturn(model);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.deleteModel("a"));
        assertEquals("不能删除当前默认模型，请先指定其它默认", ex.getMessage());
        verify(mapper, never()).deleteById(any(String.class));
    }

    @Test
    void cannotDisableDefaultOnUpdate() {
        LlmModel model = row("a", true, true);
        when(mapper.selectById("a")).thenReturn(model);
        LlmModelUpsertRequest req = new LlmModelUpsertRequest();
        req.setId("a");
        req.setEnabled(false);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.updateModel(req));
        assertEquals("不能禁用当前默认模型，请先指定其它默认可再禁用", ex.getMessage());
    }

    @Test
    void setDefaultRejectsDisabled() {
        LlmModel model = row("b", false, false);
        when(mapper.selectById("b")).thenReturn(model);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.setDefault("b"));
        assertEquals("只能把启用中的模型设为默认", ex.getMessage());
        verify(registry, never()).evict();
    }

    @Test
    @SuppressWarnings("unchecked")
    void cannotDeleteLastEnabled() {
        LlmModel model = row("c", true, false);
        when(mapper.selectById("c")).thenReturn(model);
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.deleteModel("c"));
        assertEquals("不能删除最后一条启用中的模型", ex.getMessage());
    }

    private static LlmModel row(String id, boolean enabled, boolean isDefault) {
        LlmModel model = new LlmModel();
        model.setId(id);
        model.setName("n");
        model.setProvider("deepseek");
        model.setBaseUrl("https://api.deepseek.com");
        model.setApiKey("sk-test-key-xxxx");
        model.setModelId("deepseek-v4-flash");
        model.setMaxTokens(8192);
        model.setEnabled(enabled);
        model.setIsDefault(isDefault);
        return model;
    }
}
