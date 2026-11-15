package com.mcpserver.service;

import com.mcpserver.config.DatabaseMcpProperties;
import com.mcpserver.sql.SqlGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 执行受 SqlGuard 约束的 SQL，并限制行数、超时、脱敏与截断。 */
@Service
public class DatabaseQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final DatabaseMcpProperties properties;
    private final ConnectionInfoService connectionInfoService;
    private final ResultSanitizer resultSanitizer;

    public DatabaseQueryService(JdbcTemplate jdbcTemplate,
                                DataSource dataSource,
                                DatabaseMcpProperties properties,
                                ConnectionInfoService connectionInfoService,
                                ResultSanitizer resultSanitizer) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.properties = properties;
        this.connectionInfoService = connectionInfoService;
        this.resultSanitizer = resultSanitizer;
    }

    /** 校验并执行 SQL；maxRowsOverride 为空则用全局上限。 */
    public Map<String, Object> execute(String sql, Integer maxRowsOverride) {
        String validated = SqlGuard.validate(sql, properties.isReadOnly());
        if (SqlGuard.isQuery(validated)) {
            return executeQuery(validated, resolveMaxRows(maxRowsOverride));
        }
        int updated = jdbcTemplate.update(validated);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", "update");
        out.put("updatedRows", updated);
        out.put("readOnly", properties.isReadOnly());
        out.put("connection", connectionInfoService.snapshot());
        return out;
    }

    private int resolveMaxRows(Integer maxRowsOverride) {
        int global = Math.max(1, properties.getMaxRows());
        if (maxRowsOverride == null || maxRowsOverride <= 0) {
            return global;
        }
        return Math.min(maxRowsOverride, global);
    }

    /** 查询类执行：手动 Statement 以设置超时与 maxRows，再脱敏截断。 */
    private Map<String, Object> executeQuery(String sql, int maxRows) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(Math.max(1, properties.getQueryTimeoutSeconds()));
            statement.setMaxRows(maxRows);
            boolean hasResult = statement.execute(sql);
            List<Map<String, Object>> rows = new ArrayList<>();
            if (hasResult) {
                try (ResultSet rs = statement.getResultSet()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(meta.getColumnLabel(i), rs.getObject(i));
                        }
                        rows.add(row);
                    }
                }
            }
            Map<String, Object> sanitizeMeta = resultSanitizer.sanitize(rows);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("kind", "query");
            out.put("count", rows.size());
            out.put("maxRows", maxRows);
            out.putAll(sanitizeMeta);
            out.put("rows", rows);
            out.put("connection", connectionInfoService.snapshot());
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("SQL 执行失败: " + e.getMessage(), e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
