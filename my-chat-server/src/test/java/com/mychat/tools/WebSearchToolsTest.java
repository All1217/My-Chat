package com.mychat.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.config.SearchProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.HttpStatus;

/**
 * searchWeb：无密钥、Exa 200/401、请求体字段约束。
 */
class WebSearchToolsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 未配置任何密钥时直接返回说明，不发起 HTTP。
     */
    @Test
    void noKeyReturnsHintWithoutHttp() {
        SearchProperties props = new SearchProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WebSearchTools tools = new WebSearchTools(props, builder.build(), mapper);

        String out = tools.searchWeb("美国西部发达城市");

        assertTrue(out.contains("未配置搜索密钥"));
        assertTrue(out.contains("EXA_API_KEY"));
        server.verify();
    }

    /**
     * Exa 200 时解析 title / url / highlights。
     */
    @Test
    void exa200ParsesTitleUrlHighlights() {
        SearchProperties props = new SearchProperties();
        props.getExa().setApiKey("exa-test-key");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.exa.ai/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer exa-test-key"))
                .andRespond(withSuccess("""
                        {"results":[{"title":"Los Angeles","url":"https://example.com/la","highlights":["entertainment capital"]}]}
                        """, MediaType.APPLICATION_JSON));
        WebSearchTools tools = new WebSearchTools(props, builder.build(), mapper);

        String out = tools.searchWeb("western US cities");

        assertTrue(out.contains("provider=exa-rest"));
        assertTrue(out.contains("status=200"));
        assertTrue(out.contains("Los Angeles"));
        assertTrue(out.contains("https://example.com/la"));
        assertTrue(out.contains("entertainment capital"));
        server.verify();
    }

    /**
     * Exa 401 时文案含 Invalid API key，并回退到已配置的 Bocha。
     */
    @Test
    void exa401FallsBackToBocha() {
        SearchProperties props = new SearchProperties();
        props.getExa().setApiKey("wrong-exa");
        props.getBocha().setApiKey("bocha-ok");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.exa.ai/search"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Invalid API key\"}"));
        server.expect(requestTo("https://api.bocha.cn/v1/web-search"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"data":{"webPages":{"value":[{"name":"西雅图","url":"https://example.com/sea","summary":"科技与航空"}]}}}
                        """, MediaType.APPLICATION_JSON));
        WebSearchTools tools = new WebSearchTools(props, builder.build(), mapper);

        String out = tools.searchWeb("美国西部城市");

        assertTrue(out.contains("provider=bocha-rest"));
        assertTrue(out.contains("status=200"));
        assertTrue(out.contains("西雅图"));
        server.verify();
    }

    /**
     * 仅 Exa 且 401 时，结果必须带上 Invalid API key。
     */
    @Test
    void exa401WithoutFallbackKeepsErrorText() {
        SearchProperties props = new SearchProperties();
        props.getExa().setApiKey("smithery-key-by-mistake");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.exa.ai/search"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Invalid API key\"}"));
        WebSearchTools tools = new WebSearchTools(props, builder.build(), mapper);

        String out = tools.searchWeb("test");

        assertTrue(out.contains("provider=exa-rest"));
        assertTrue(out.contains("401"));
        assertTrue(out.contains("Invalid API key"));
        server.verify();
    }

    /**
     * Exa 请求体含 contents.highlights，不含已废弃的 useAutoprompt。
     */
    @Test
    void exaRequestBodyUsesContentsHighlights() throws Exception {
        SearchProperties props = new SearchProperties();
        RestClient.Builder builder = RestClient.builder();
        WebSearchTools tools = new WebSearchTools(props, builder.build(), mapper);

        String json = tools.buildExaRequestBody("latest LLM news");
        JsonNode root = mapper.readTree(json);

        assertTrue(root.path("contents").path("highlights").asBoolean());
        assertFalse(root.has("useAutoprompt"));
        assertFalse(root.has("text"));
        assertFalse(root.has("highlights"));
    }

    /**
     * 空 query 不发请求。
     */
    @Test
    void blankQueryRejected() {
        SearchProperties props = new SearchProperties();
        props.getExa().setApiKey("k");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WebSearchTools tools = new WebSearchTools(props, builder.build(), mapper);

        assertTrue(tools.searchWeb("  ").contains("query 为空"));
        server.verify();
    }
}
