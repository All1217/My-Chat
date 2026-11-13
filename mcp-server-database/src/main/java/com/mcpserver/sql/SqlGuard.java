package com.mcpserver.sql;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * SQL 安全校验：拦截多语句、注释绕过与只读模式下的写操作。
 */
public final class SqlGuard {

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("--.*?$", Pattern.MULTILINE);
    private static final Pattern DANGEROUS = Pattern.compile(
            "(?i)\\b(drop\\s+database|drop\\s+schema|alter\\s+system|copy\\s+.*\\bto\\b|pg_read_file|lo_import)\\b");

    private SqlGuard() {
    }

    /**
     * 规范化并校验 SQL；通过后返回可执行文本。
     *
     * @param sql      原始 SQL
     * @param readOnly 是否只读模式
     */
    public static String validate(String sql, boolean readOnly) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }

        // 1) 去掉注释，降低 -- / /* */ 绕过风险
        String cleaned = LINE_COMMENT.matcher(sql).replaceAll(" ");
        cleaned = BLOCK_COMMENT.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.trim();
        if (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }

        // 2) 禁止多语句
        if (cleaned.contains(";")) {
            throw new IllegalArgumentException("禁止一次执行多条 SQL");
        }

        // 3) 始终拦截明显危险语句
        if (DANGEROUS.matcher(cleaned).find()) {
            throw new IllegalArgumentException("禁止执行该危险 SQL");
        }

        String upper = cleaned.toUpperCase(Locale.ROOT);
        boolean readStmt = upper.startsWith("SELECT")
                || upper.startsWith("WITH")
                || upper.startsWith("EXPLAIN");

        // 4) 只读模式仅允许查询类语句
        if (readOnly && !readStmt) {
            throw new IllegalArgumentException("当前为只读模式，仅允许 SELECT / WITH / EXPLAIN");
        }
        return cleaned;
    }

    /** 是否为查询类语句（决定用 query 还是 update）。 */
    public static boolean isQuery(String validatedSql) {
        String upper = validatedSql.toUpperCase(Locale.ROOT);
        return upper.startsWith("SELECT")
                || upper.startsWith("WITH")
                || upper.startsWith("EXPLAIN");
    }
}
