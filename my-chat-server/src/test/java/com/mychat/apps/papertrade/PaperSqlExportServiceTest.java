package com.mychat.apps.papertrade;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 导出表白名单与 INSERT 拼装。 */
class PaperSqlExportServiceTest {

    @Test
    void rejectsUnknownTable() {
        PaperSqlExportService svc = new PaperSqlExportService(mock(DataSource.class));
        assertThrows(IllegalArgumentException.class, () -> svc.exportTable("users"));
    }

    @Test
    void buildsInsertForOneRow() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        Statement st = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(st);
        when(st.executeQuery(anyString())).thenReturn(rs);
        when(rs.getMetaData()).thenReturn(meta);
        when(meta.getColumnCount()).thenReturn(2);
        when(meta.getColumnName(1)).thenReturn("id");
        when(meta.getColumnName(2)).thenReturn("status");
        when(meta.getColumnType(1)).thenReturn(Types.VARCHAR);
        when(meta.getColumnType(2)).thenReturn(Types.VARCHAR);
        when(rs.next()).thenReturn(true, false);
        when(rs.getObject(1)).thenReturn("r1");
        when(rs.getObject(2)).thenReturn("FAILED");

        var vo = new PaperSqlExportService(ds).exportTable("paper_research_run");
        assertEquals("paper_research_run", vo.getTableName());
        assertTrue(vo.getFilename().startsWith("paper_research_run_"));
        assertTrue(vo.getFilename().endsWith(".sql"));
        assertTrue(vo.getContent().contains(
                "INSERT INTO paper_research_run (\"id\", \"status\") VALUES ('r1', 'FAILED');"));
    }
}
