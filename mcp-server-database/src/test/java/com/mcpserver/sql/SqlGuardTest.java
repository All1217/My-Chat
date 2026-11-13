package com.mcpserver.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SqlGuard 只读 / 写模式与多语句拦截。 */
class SqlGuardTest {

    @Test
    void allowsSelectInReadOnly() {
        String sql = SqlGuard.validate("SELECT id FROM users", true);
        assertEquals("SELECT id FROM users", sql);
        assertTrue(SqlGuard.isQuery(sql));
    }

    @Test
    void allowsWithAndExplainInReadOnly() {
        assertTrue(SqlGuard.isQuery(SqlGuard.validate("WITH x AS (SELECT 1) SELECT * FROM x", true)));
        assertTrue(SqlGuard.isQuery(SqlGuard.validate("EXPLAIN SELECT 1", true)));
    }

    @Test
    void rejectsWriteInReadOnly() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlGuard.validate("INSERT INTO users(username) VALUES ('a')", true));
    }

    @Test
    void allowsWriteWhenNotReadOnly() {
        String sql = SqlGuard.validate("UPDATE users SET email='a@b.c' WHERE id=1", false);
        assertFalse(SqlGuard.isQuery(sql));
    }

    @Test
    void rejectsMultiStatement() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlGuard.validate("SELECT 1; SELECT 2", true));
    }

    @Test
    void rejectsDangerousSqlEvenWhenWritable() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlGuard.validate("DROP DATABASE postgres", false));
    }
}
