package com.novel2script.domain.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base for type-safe JSON list handlers.
 *
 * <p>Unlike {@code JacksonTypeHandler} from MyBatis-Plus which loses generic type
 * info when instantiated via {@code @TableField(typeHandler = ...)}, these concrete
 * handlers know the exact element type, guaranteeing correct deserialization of
 * nested objects (e.g. {@code Character.Relationship}) from JSON columns.
 */
public abstract class AbstractListTypeHandler<T> extends BaseTypeHandler<List<T>> {

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    private final JavaType listType;

    protected AbstractListTypeHandler(Class<T> elementType) {
        this.listType = MAPPER.getTypeFactory().constructCollectionType(List.class, elementType);
    }

    /** No-arg constructor for MyBatis instantiation — subclasses override getListType() */
    protected AbstractListTypeHandler() {
        this.listType = null;
    }

    protected JavaType getListType() {
        return listType;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<T> parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            ps.setString(i, MAPPER.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize list to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public List<T> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJson(rs.getString(columnName));
    }

    @Override
    public List<T> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getString(columnIndex));
    }

    @Override
    public List<T> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getString(columnIndex));
    }

    @SuppressWarnings("unchecked")
    protected List<T> parseJson(String json) throws SQLException {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return Collections.emptyList();
        }
        try {
            JavaType type = getListType();
            if (type != null) {
                return MAPPER.readValue(json, type);
            }
            // Fallback for when subclass doesn't override getListType()
            return (List<T>) MAPPER.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to deserialize JSON list: " + e.getMessage(), e);
        }
    }
}
