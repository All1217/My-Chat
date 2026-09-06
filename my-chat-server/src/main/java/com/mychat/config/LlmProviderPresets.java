package com.mychat.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * OpenAI 兼容供应商预设：选供应商时回填默认 Base URL 与常用模型提示。
 */
public final class LlmProviderPresets {

    /** 单个供应商的展示信息。 */
    public record Preset(String key, String label, String baseUrl, List<String> modelHints) {
    }

    public static final String DEEPSEEK = "deepseek";
    public static final String OPENAI = "openai";
    public static final String QWEN = "qwen";
    public static final String OLLAMA = "ollama";
    public static final String SILICONFLOW = "siliconflow";
    public static final String CUSTOM = "custom";

    private static final Set<String> KEYS = Set.of(DEEPSEEK, OPENAI, QWEN, OLLAMA, SILICONFLOW, CUSTOM);

    private static final List<Preset> ALL = List.of(
            new Preset(DEEPSEEK, "DeepSeek", "https://api.deepseek.com",
                    List.of("deepseek-v4-flash", "deepseek-v4-pro", "deepseek-chat")),
            new Preset(OPENAI, "OpenAI", "https://api.openai.com",
                    List.of("gpt-4.1", "gpt-4o", "gpt-4o-mini")),
            new Preset(QWEN, "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                    List.of("qwen-plus", "qwen-turbo", "qwen-max")),
            new Preset(OLLAMA, "Ollama", "http://localhost:11434/v1",
                    List.of("llama3.1", "qwen2.5", "mistral")),
            new Preset(SILICONFLOW, "硅基流动", "https://api.siliconflow.cn/v1",
                    List.of("deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-72B-Instruct")),
            new Preset(CUSTOM, "自定义", "", List.of())
    );

    private LlmProviderPresets() {
    }

    /** 返回全部预设（顺序固定，供设置页下拉）。 */
    public static List<Preset> all() {
        return ALL;
    }

    /** 校验 provider 是否为已知键。 */
    public static boolean isKnown(String provider) {
        return provider != null && KEYS.contains(provider.toLowerCase(Locale.ROOT));
    }

    /** 按 Base URL 猜测供应商，种子行与自定义填写时用。 */
    public static String inferProvider(String baseUrl) {
        if (baseUrl == null) {
            return CUSTOM;
        }
        String u = baseUrl.toLowerCase(Locale.ROOT);
        if (u.contains("deepseek")) {
            return DEEPSEEK;
        }
        if (u.contains("openai.com")) {
            return OPENAI;
        }
        if (u.contains("dashscope") || u.contains("aliyuncs.com")) {
            return QWEN;
        }
        if (u.contains("11434") || u.contains("ollama")) {
            return OLLAMA;
        }
        if (u.contains("siliconflow")) {
            return SILICONFLOW;
        }
        return CUSTOM;
    }
}
