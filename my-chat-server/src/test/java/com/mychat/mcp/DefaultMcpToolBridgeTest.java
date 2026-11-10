package com.mychat.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bridge 按 weather / smithery 子开关与 API Key 过滤连接。
 */
class DefaultMcpToolBridgeTest {

    @Test
    void skipsSmitheryWhenApiKeyBlank() {
        McpProperties props = new McpProperties();
        props.getSmithery().setEnabled(true);
        props.getSmithery().setApiKey("");
        props.getWeather().setEnabled(true);

        McpSyncClient smithery = clientNamed("smithery-toolbox");
        McpSyncClient weather = clientNamed("mcp-weather-server");

        var bridge = new DefaultMcpToolBridge(props, List.of(smithery, weather), null);
        List<McpSyncClient> allowed = bridge.filterClients(List.of(smithery, weather));

        assertEquals(1, allowed.size());
        assertTrue(bridge.isAllowed(weather));
        assertFalse(bridge.isAllowed(smithery));
    }

    @Test
    void skipsWeatherWhenDisabled() {
        McpProperties props = new McpProperties();
        props.getWeather().setEnabled(false);
        props.getSmithery().setEnabled(true);
        props.getSmithery().setApiKey("sk-test");

        McpSyncClient weather = clientNamed("mcp-weather-server");
        McpSyncClient smithery = clientNamed("smithery-toolbox");

        var bridge = new DefaultMcpToolBridge(props, List.of(weather, smithery), null);
        List<McpSyncClient> allowed = bridge.filterClients(List.of(weather, smithery));

        assertEquals(1, allowed.size());
        assertFalse(bridge.isAllowed(weather));
        assertTrue(bridge.isAllowed(smithery));
    }

    /** mock 一个带 title 的 client，便于 describe() 命中 connection 名。 */
    private static McpSyncClient clientNamed(String title) {
        McpSyncClient client = mock(McpSyncClient.class);
        // Implementation(name, title, version)
        when(client.getClientInfo()).thenReturn(
                new McpSchema.Implementation("name", title, "1.0.0"));
        return client;
    }
}
