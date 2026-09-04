package com.mychat.config;

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 按连接隔离 listTools：一个 MCP 500 不得清空其它连接的工具。
 */
class TolerantMcpToolCallbackProviderTest {

    @Test
    void skipsFailedClientKeepsSuccessfulTools() {
        McpSyncClient failing = mock(McpSyncClient.class);
        McpSyncClient ok = mock(McpSyncClient.class);
        ToolCallback weather = mock(ToolCallback.class);
        when(weather.getToolDefinition()).thenReturn(
                ToolDefinition.builder().name("get_weather").description("weather").inputSchema("{}").build());

        var provider = new TolerantMcpToolCallbackProvider(List.of(failing, ok), client -> {
            if (client == failing) {
                throw new RuntimeException("Failed to send message: Internal Server Error");
            }
            return new ToolCallback[]{weather};
        });

        ToolCallback[] callbacks = provider.getToolCallbacks();
        assertEquals(1, callbacks.length);
        assertSame(weather, callbacks[0]);
    }

    @Test
    void emptyClientListYieldsNoTools() {
        var provider = new TolerantMcpToolCallbackProvider(List.of(),
                client -> new ToolCallback[]{mock(ToolCallback.class)});
        assertEquals(0, provider.getToolCallbacks().length);
    }
}
