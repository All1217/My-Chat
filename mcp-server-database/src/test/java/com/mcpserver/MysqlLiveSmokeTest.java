package com.mcpserver;

import com.mcpserver.catalog.CatalogDialect;
import com.mcpserver.service.ConnectionInfoService;
import com.mcpserver.service.DatabaseQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL 活库冒烟：需 MYSQL_SMOKE=true 且能连上目标机。
 * 密码通过环境变量 MYSQL_PASS 注入，勿写进仓库。
 */
@SpringBootTest
@ActiveProfiles("mysql")
@EnabledIfEnvironmentVariable(named = "MYSQL_SMOKE", matches = "true")
class MysqlLiveSmokeTest {

    @Autowired
    private CatalogDialect catalogDialect;
    @Autowired
    private ConnectionInfoService connectionInfoService;
    @Autowired
    private DatabaseQueryService queryService;

    @Test
    void connectionInfoIsMysql() {
        Map<String, Object> snap = connectionInfoService.snapshot();
        assertEquals("mysql", snap.get("dialect"));
        assertEquals("192.168.150.102", snap.get("jdbcHost"));
        assertTrue(Boolean.TRUE.equals(snap.get("readOnly")));
    }

    @Test
    void listSchemasAndShowTables() {
        List<Map<String, Object>> schemas = catalogDialect.listSchemas();
        assertFalse(schemas.isEmpty(), "应至少有一个非系统库或为空列表也可，但调用本身应成功");
        Map<String, Object> show = queryService.execute("SHOW DATABASES", 50);
        assertEquals("query", show.get("kind"));
        assertTrue(((Number) show.get("count")).intValue() > 0);
    }

    @Test
    void rejectsWriteInReadOnly() {
        assertThrows(IllegalArgumentException.class,
                () -> queryService.execute("CREATE TABLE mcp_smoke_should_fail(id INT)", null));
    }

    @Test
    void flywaySoftWhenMissing() {
        Map<String, Object> flyway = catalogDialect.flywayStatus();
        assertTrue(flyway.containsKey("available"));
    }
}
