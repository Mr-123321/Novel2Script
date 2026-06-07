package com.novel2script.domain.handler;

import com.fasterxml.jackson.databind.JavaType;
import com.novel2script.domain.model.Character;

import java.util.List;

/**
 * Type-safe handler for {@code List<Character.Relationship>} JSON column.
 *
 * <p>This is the critical handler — without explicit element type info,
 * Jackson would deserialize relationships as {@code List<LinkedHashMap>},
 * causing {@code ClassCastException} when code calls {@code r.getTarget()}.
 */
public class RelationshipListTypeHandler extends AbstractListTypeHandler<Character.Relationship> {

    @Override
    protected JavaType getListType() {
        return MAPPER.getTypeFactory().constructCollectionType(
                List.class, Character.Relationship.class);
    }
}
