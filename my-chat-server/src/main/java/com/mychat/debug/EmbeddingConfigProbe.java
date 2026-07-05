package com.mychat.debug;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class EmbeddingConfigProbe {

    @Autowired
    private Environment environment;

    @PostConstruct
    public void probeEmbeddingConfig() {
        // #region agent log
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("openaiBaseUrl", environment.getProperty("spring.ai.openai.base-url"));
        data.put("embeddingBaseUrl", environment.getProperty("spring.ai.openai.embedding.base-url"));
        data.put("embeddingOptionsBaseUrl", environment.getProperty("spring.ai.openai.embedding.options.base-url"));
        data.put("embeddingModel", environment.getProperty("spring.ai.openai.embedding.options.model"));
        data.put("openaiApiKey", AgentDebugLog.secretStatus(environment.getProperty("spring.ai.openai.api-key")));
        data.put("embeddingApiKey", AgentDebugLog.secretStatus(environment.getProperty("spring.ai.openai.embedding.api-key")));
        data.put("embeddingOptionsApiKey", AgentDebugLog.secretStatus(environment.getProperty("spring.ai.openai.embedding.options.api-key")));
        data.put("envOpenAiKey", AgentDebugLog.secretStatus(System.getenv("OPENAI_API_KEY")));
        data.put("envEmbeddingKey", AgentDebugLog.secretStatus(System.getenv("EMBEDDING_MODEL_API_KEY")));
        data.put("pgvectorDimensions", environment.getProperty("spring.ai.vectorstore.pgvector.dimensions"));
        AgentDebugLog.write("A", "EmbeddingConfigProbe.java:startup", "Resolved embedding configuration (post-fix)", data);
        // #endregion
    }
}
