package com.mychat.mapper.typehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.vo.MessagePartVO;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * PostgreSQL {@code jsonb} ↔ {@code List<MessagePartVO>}。
 * <p>
 * MyBatis-Plus 默认 {@code JacksonTypeHandler} 按 {@code varchar} 绑定，
 * PG 会报「字段 parts 类型为 jsonb，但表达式类型为 character varying」。
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class MessagePartsJsonbTypeHandler extends BaseTypeHandler<List<MessagePartVO>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<MessagePartVO>> TYPE =
            new TypeReference<List<MessagePartVO>>() {
            };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i,
                                    List<MessagePartVO> parameter,
                                    JdbcType jdbcType) throws SQLException {
        PGobject json = new PGobject();
        json.setType("jsonb");
        try {
            json.setValue(MAPPER.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            throw new SQLException("序列化 parts 为 jsonb 失败", e);
        }
        ps.setObject(i, json);
    }

    @Override
    public List<MessagePartVO> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<MessagePartVO> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<MessagePartVO> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private static List<MessagePartVO> parse(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<MessagePartVO> list = MAPPER.readValue(json, TYPE);
            return list != null ? list : Collections.emptyList();
        } catch (JsonProcessingException e) {
            throw new SQLException("反序列化 parts jsonb 失败", e);
        }
    }
}
