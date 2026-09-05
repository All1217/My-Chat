package com.mychat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本地联网搜索密钥与端点（直连供应商 REST，不经 Smithery MCP）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.search")
public class SearchProperties {

    /** Exa Search REST（主路径） */
    private Provider exa = Provider.exaDefault();

    /** 博查，国内可选回退 */
    private Provider bocha = Provider.bochaDefault();

    /** Tavily，可选回退 */
    private Provider tavily = Provider.tavilyDefault();

    /**
     * 单个搜索供应商的密钥与根地址。
     */
    @Data
    public static class Provider {

        /** 环境变量注入的 API Key，空则跳过该供应商 */
        private String apiKey = "";

        /** REST 根地址，不含具体 path */
        private String baseUrl = "";

        /**
         * Exa 默认端点：https://api.exa.ai
         */
        static Provider exaDefault() {
            Provider p = new Provider();
            p.baseUrl = "https://api.exa.ai";
            return p;
        }

        /**
         * 博查默认端点。
         */
        static Provider bochaDefault() {
            Provider p = new Provider();
            p.baseUrl = "https://api.bocha.cn";
            return p;
        }

        /**
         * Tavily 默认端点。
         */
        static Provider tavilyDefault() {
            Provider p = new Provider();
            p.baseUrl = "https://api.tavily.com";
            return p;
        }
    }
}
