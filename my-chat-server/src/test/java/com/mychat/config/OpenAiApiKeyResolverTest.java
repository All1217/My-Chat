package com.mychat.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 显式 key → 环境变量 → YAML 的解析顺序。 */
class OpenAiApiKeyResolverTest {

    @Test
    void explicitKeyWins() {
        OpenAiApiKeyResolver resolver = new OpenAiApiKeyResolver("yaml-key");
        assertEquals("sk-explicit", resolver.resolve("sk-explicit"));
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void blankFallsBackToYamlWhenEnvAbsent() {
        OpenAiApiKeyResolver resolver = new OpenAiApiKeyResolver("yaml-key");
        assertEquals("yaml-key", resolver.resolve(""));
        assertEquals("yaml-key", resolver.resolve("   "));
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void missingAllThrowsHelpfulMessage() {
        OpenAiApiKeyResolver resolver = new OpenAiApiKeyResolver("");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(""));
        assertEquals(OpenAiApiKeyResolver.MISSING_KEY_MESSAGE, ex.getMessage());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void blankPrefersEnvOverYaml() {
        OpenAiApiKeyResolver resolver = new OpenAiApiKeyResolver("yaml-should-not-win");
        assertEquals(System.getenv("OPENAI_API_KEY"), resolver.resolve(""));
    }
}
