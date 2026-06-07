package com.novel2script.domain.handler;

import com.fasterxml.jackson.databind.JavaType;

import java.util.List;

/**
 * Type-safe handler for {@code List<Long>} JSON columns.
 * Used by: mergedFrom, chapterIds, characterIds fields.
 */
public class LongListTypeHandler extends AbstractListTypeHandler<Long> {

    @Override
    protected JavaType getListType() {
        return MAPPER.getTypeFactory().constructCollectionType(List.class, Long.class);
    }
}
