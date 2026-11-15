package com.mcpserver.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** JDBC URL 解析。 */
class ConnectionInfoServiceTest {

    @Test
    void parsesPostgresUrlWithSchema() {
        ConnectionInfoService.Parsed p = ConnectionInfoService.parse(
                "jdbc:postgresql://localhost:5432/postgres?currentSchema=public");
        assertEquals("localhost", p.host());
        assertEquals(5432, p.port());
        assertEquals("postgres", p.database());
        assertEquals("public", p.schemaHint());
    }

    @Test
    void parsesHostWithoutPort() {
        ConnectionInfoService.Parsed p = ConnectionInfoService.parse(
                "jdbc:postgresql://db.example.com/mychat");
        assertEquals("db.example.com", p.host());
        assertNull(p.port());
        assertEquals("mychat", p.database());
        assertEquals("public", p.schemaHint());
    }
}
