package com.mychat.service.model;

import com.mychat.config.LlmProviderPresets;
import com.mychat.entity.po.LlmModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 脱敏、校验与供应商推断。 */
class LlmModelSupportTest {

    @Test
    void maskKeepsHeadAndTail() {
        assertEquals("sk-****cdef", LlmModelSupport.maskApiKey("sk-abcdefghcdef"));
    }

    @Test
    void maskShortKeyIsStars() {
        assertEquals("****", LlmModelSupport.maskApiKey("short"));
    }

    @Test
    void validateRejectsUnknownProvider() {
        LlmModel model = valid();
        model.setProvider("anthropic");
        assertThrows(IllegalArgumentException.class, () -> LlmModelSupport.validate(model));
    }

    @Test
    void validateAcceptsDeepSeek() {
        assertDoesNotThrow(() -> LlmModelSupport.validate(valid()));
    }

    @Test
    void inferProviderFromDeepSeekUrl() {
        assertEquals(LlmProviderPresets.DEEPSEEK, LlmProviderPresets.inferProvider("https://api.deepseek.com"));
    }

    @Test
    void trimSlashRemovesTrailing() {
        assertEquals("https://api.deepseek.com", LlmModelSupport.trimSlash("https://api.deepseek.com/"));
    }

    @Test
    void toVoNeverExposesRawKey() {
        LlmModel model = valid();
        var vo = LlmModelSupport.toVo(model);
        assertTrue(vo.getApiKeyMasked().contains("****"));
        assertEquals("deepseek-v4-flash", vo.getModelId());
    }

    private static LlmModel valid() {
        LlmModel model = new LlmModel();
        model.setId("1");
        model.setName("DeepSeek");
        model.setProvider("deepseek");
        model.setBaseUrl("https://api.deepseek.com/");
        model.setApiKey("sk-abcdefghijklmnop");
        model.setModelId("deepseek-v4-flash");
        model.setMaxTokens(8192);
        model.setEnabled(true);
        model.setIsDefault(true);
        return model;
    }
}
