package com.mychat.mapper.typehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** PostgreSQL jsonb 与通用 List 的转换器。 */
public class JsonbListTypeHandler extends BaseTypeHandler<List<Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Object>> TYPE = new TypeReference<>() {
    };

    /** 以 PG jsonb 类型绑定 List。 */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int index, List<Object> parameter,
                                    JdbcType jdbcType) throws SQLException {
        PGobject json = new PGobject();
        json.setType("jsonb");
        try {
            json.setValue(MAPPER.writeValueAsString(parameter));
        } catch (JsonProcessingException exception) {
            throw new SQLException("序列化 List 为 jsonb 失败", exception);
        }
        ps.setObject(index, json);
    }

    /** 按列名读取。 */
    @Override
    public List<Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    /** 按列序号读取。 */
    @Override
    public List<Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    /** 从存储过程读取。 */
    @Override
    public List<Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    /** 解析 JSON List。 */
    private List<Object> parse(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (JsonProcessingException exception) {
            throw new SQLException("反序列化 jsonb List 失败", exception);
        }
    }
}
