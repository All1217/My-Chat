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
 * Bridge 按 database / crawler / smithery 子开关与 API Key 过滤连接。
 */
class DefaultMcpToolBridgeTest {

    @Test
    void skipsSmitheryWhenApiKeyBlank() {
        McpProperties props = new McpProperties();
        props.getSmithery().setEnabled(true);
        props.getSmithery().setApiKey("");
        props.getDatabase().setEnabled(true);

        McpSyncClient smithery = clientNamed("smithery-toolbox");
        McpSyncClient database = clientNamed("mcp-database-server");

        var bridge = new DefaultMcpToolBridge(props, List.of(smithery, database), null);
        List<McpSyncClient> allowed = bridge.filterClients(List.of(smithery, database));

        assertEquals(1, allowed.size());
        assertTrue(bridge.isAllowed(database));
        assertFalse(bridge.isAllowed(smithery));
    }

    @Test
    void skipsDatabaseWhenDisabled() {
        McpProperties props = new McpProperties();
        props.getDatabase().setEnabled(false);
        props.getSmithery().setEnabled(true);
        props.getSmithery().setApiKey("sk-test");

        McpSyncClient database = clientNamed("mcp-database-server");
        McpSyncClient smithery = clientNamed("smithery-toolbox");

        var bridge = new DefaultMcpToolBridge(props, List.of(database, smithery), null);
        List<McpSyncClient> allowed = bridge.filterClients(List.of(database, smithery));

        assertEquals(1, allowed.size());
        assertFalse(bridge.isAllowed(database));
        assertTrue(bridge.isAllowed(smithery));
    }

    @Test
    void skipsCrawlerWhenDisabled() {
        McpProperties props = new McpProperties();
        props.getCrawler().setEnabled(false);
        props.getDatabase().setEnabled(true);

        McpSyncClient crawler = clientNamed("mcp-crawler-server");
        McpSyncClient database = clientNamed("mcp-database-server");

        var bridge = new DefaultMcpToolBridge(props, List.of(crawler, database), null);
        List<McpSyncClient> allowed = bridge.filterClients(List.of(crawler, database));

        assertEquals(1, allowed.size());
        assertFalse(bridge.isAllowed(crawler));
        assertTrue(bridge.isAllowed(database));
    }

    /** mock 一个带 title 的 client，便于 describe() 命中 connection 名。 */
    private static McpSyncClient clientNamed(String title) {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.getClientInfo()).thenReturn(
                new McpSchema.Implementation("name", title, "1.0.0"));
        return client;
    }
}
