package com.mcpserver.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** JDBC URL 解析（PostgreSQL / MySQL）。 */
class ConnectionInfoServiceTest {

    @Test
    void parsesPostgresUrlWithSchema() {
        ConnectionInfoService.Parsed p = ConnectionInfoService.parse(
                "jdbc:postgresql://localhost:5432/postgres?currentSchema=public");
        assertEquals("postgresql", p.dialect());
        assertEquals("localhost", p.host());
        assertEquals(5432, p.port());
        assertEquals("postgres", p.database());
        assertEquals("public", p.schemaHint());
    }

    @Test
    void parsesHostWithoutPort() {
        ConnectionInfoService.Parsed p = ConnectionInfoService.parse(
                "jdbc:postgresql://db.example.com/mychat");
        assertEquals("postgresql", p.dialect());
        assertEquals("db.example.com", p.host());
        assertNull(p.port());
        assertEquals("mychat", p.database());
        assertEquals("public", p.schemaHint());
    }

    @Test
    void parsesMysqlUrl() {
        ConnectionInfoService.Parsed p = ConnectionInfoService.parse(
                "jdbc:mysql://192.168.150.102:3306/appdb?useSSL=true&sslMode=PREFERRED");
        assertEquals("mysql", p.dialect());
        assertEquals("192.168.150.102", p.host());
        assertEquals(3306, p.port());
        assertEquals("appdb", p.database());
        assertEquals("appdb", p.schemaHint());
    }

    @Test
    void parsesMysqlUrlWithoutDatabase() {
        ConnectionInfoService.Parsed p = ConnectionInfoService.parse(
                "jdbc:mysql://192.168.150.102:3306/?allowPublicKeyRetrieval=true");
        assertEquals("mysql", p.dialect());
        assertEquals("192.168.150.102", p.host());
        assertEquals(3306, p.port());
        assertEquals("", p.database());
        assertEquals("", p.schemaHint());
    }
}
