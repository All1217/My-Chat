package com.mychat.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/ai/normalChat")
public class ChatController {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model}")
    private String model;

    public ChatController() {
        this.webClient = WebClient.builder().build();
    }

    @RequestMapping(value = "/chat", produces = "text/plain;charset=utf-8")  // ← 改成 text/plain，避免 Spring MVC 加 data: 前缀
    public Flux<String> chat(
            @RequestParam("prompt") String prompt,
            @RequestParam("chatId") String chatId,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "stream", true,
                "max_tokens", 32768,
                "thinking", Map.of("type", "enabled")
        );

        AtomicBoolean thinkingStarted = new AtomicBoolean(false);
        AtomicBoolean thinkingEnded = new AtomicBoolean(false);

        return webClient.post()
                .uri(baseUrl + "/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(data -> !data.equals("[DONE]"))
                // 去掉 SSE 的 "data:" 前缀
                .map(data -> {
                    data = data.trim();
                    if (data.startsWith("data:")) {
                        return data.substring(5).trim();
                    }
                    return data;
                })
                .mapNotNull(data -> {
                    try {
                        JsonNode chunk = objectMapper.readTree(data);
                        JsonNode choices = chunk.get("choices");
                        if (choices == null || choices.isEmpty()) return "";

                        JsonNode delta = choices.get(0).get("delta");
                        if (delta == null) return "";

                        StringBuilder result = new StringBuilder();

                        // 处理 reasoning_content（思考过程）
                        if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                            String reasoning = delta.get("reasoning_content").asText();
                            if (!reasoning.isEmpty()) {
                                if (!thinkingStarted.get()) {
                                    thinkingStarted.set(true);
                                    result.append("[THINKING_START]");
                                }
                                result.append(reasoning);
                            }
                        }

                        // 处理 content（正文）
                        if (delta.has("content") && !delta.get("content").isNull()) {
                            String content = delta.get("content").asText();
                            if (!content.isEmpty()) {
                                if (thinkingStarted.get() && !thinkingEnded.get()) {
                                    thinkingEnded.set(true);
                                    result.append("[THINKING_END]");
                                }
                                result.append(content);
                            }
                        }
                        return result.toString();
                    } catch (Exception e) {
                        return "";
                    }
                })
                .filter(s -> !s.isEmpty());
    }
}