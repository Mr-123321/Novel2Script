package com.novel2script.domain.handler;

import com.fasterxml.jackson.databind.JavaType;

import java.util.List;

/**
 * Type-safe handler for {@code List<String>} JSON columns.
 * Used by: aliases, personality fields.
 */
public class StringListTypeHandler extends AbstractListTypeHandler<String> {

    @Override
    protected JavaType getListType() {
        return MAPPER.getTypeFactory().constructCollectionType(List.class, String.class);
    }
}
