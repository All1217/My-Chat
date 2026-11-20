package com.mychat.apps.papertrade;

import com.mychat.apps.papertrade.entity.vo.PaperSqlExportVO;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 历史表白名单全表导出为 PostgreSQL INSERT 脚本。 */
@Service
public class PaperSqlExportService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter SQL_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ALLOWED = Set.of(
            "paper_stage_summary",
            "paper_ai_decision",
            "paper_ai_run",
            "paper_research_run",
            "paper_research_note",
            "paper_warehouse_analyst_run",
            "paper_warehouse_cleanup_run",
            "paper_trade_evaluator_run");

    private final DataSource dataSource;

    public PaperSqlExportService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 导出指定表白名单内全表 INSERT。 */
    public PaperSqlExportVO exportTable(String rawTable) {
        String table = rawTable == null ? "" : rawTable.trim().toLowerCase();
        if (!ALLOWED.contains(table)) {
            throw new IllegalArgumentException("不支持导出该表");
        }
        StringBuilder sql = new StringBuilder();
        sql.append("-- export ").append(table).append(' ')
                .append(LocalDateTime.now().format(SQL_TS)).append('\n');
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + table)) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            String[] names = new String[cols];
            for (int i = 1; i <= cols; i++) {
                names[i - 1] = meta.getColumnName(i);
            }
            int rows = 0;
            while (rs.next()) {
                rows++;
                sql.append("INSERT INTO ").append(table).append(" (");
                for (int i = 0; i < cols; i++) {
                    if (i > 0) {
                        sql.append(", ");
                    }
                    sql.append(quoteIdent(names[i]));
                }
                sql.append(") VALUES (");
                for (int i = 1; i <= cols; i++) {
                    if (i > 1) {
                        sql.append(", ");
                    }
                    sql.append(literal(rs, i, meta.getColumnType(i)));
                }
                sql.append(");\n");
            }
            if (rows == 0) {
                sql.append("-- empty table\n");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("导出失败: " + e.getMessage(), e);
        }
        String filename = table + "_" + LocalDateTime.now().format(FILE_TS) + ".sql";
        return new PaperSqlExportVO(table, filename, sql.toString());
    }

    /** 允许导出的表清单（只读）。 */
    public Map<String, String> allowedTables() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String t : ALLOWED) {
            map.put(t, t);
        }
        return map;
    }

    private static String quoteIdent(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    /** 按 JDBC 类型写成 PG 字面量。 */
    private static String literal(ResultSet rs, int index, int jdbcType) throws Exception {
        Object value = rs.getObject(index);
        if (value == null) {
            return "NULL";
        }
        return switch (jdbcType) {
            case Types.BOOLEAN, Types.BIT -> rs.getBoolean(index) ? "TRUE" : "FALSE";
            case Types.SMALLINT, Types.INTEGER, Types.BIGINT -> String.valueOf(rs.getLong(index));
            case Types.NUMERIC, Types.DECIMAL, Types.REAL, Types.FLOAT, Types.DOUBLE -> {
                BigDecimal n = rs.getBigDecimal(index);
                yield n == null ? "NULL" : n.toPlainString();
            }
            case Types.DATE -> {
                Date d = rs.getDate(index);
                yield d == null ? "NULL" : "'" + d.toLocalDate() + "'";
            }
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
                Timestamp ts = rs.getTimestamp(index);
                if (ts == null) {
                    yield "NULL";
                }
                yield "'" + ts.toLocalDateTime().format(SQL_TS) + "'";
            }
            default -> quoteLiteral(String.valueOf(value));
        };
    }

    private static String quoteLiteral(String s) {
        return "'" + s.replace("'", "''") + "'";
    }
}
