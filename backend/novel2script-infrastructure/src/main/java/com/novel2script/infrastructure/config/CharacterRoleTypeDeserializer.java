package com.novel2script.infrastructure.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.novel2script.common.enums.CharacterRoleType;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Jackson deserializer for {@link CharacterRoleType} that tolerates
 * unknown values from AI model output.
 *
 * <p>When the AI returns an unrecognized role type (e.g. "DEUTERAGONIST",
 * "villain", "sidekick"), this deserializer silently maps it to
 * {@link CharacterRoleType#SUPPORTING} and logs a warning.
 *
 * <p>Registered automatically by {@link JacksonConfig}.
 */
@Slf4j
public class CharacterRoleTypeDeserializer extends JsonDeserializer<CharacterRoleType> {

    @Override
    public CharacterRoleType deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        String value = p.getValueAsString();
        CharacterRoleType result = CharacterRoleType.fromValue(value);

        // Log unknown values for monitoring
        if (value != null && !value.isBlank()) {
            String upper = value.trim().toUpperCase();
            boolean isKnown = false;
            for (CharacterRoleType t : CharacterRoleType.values()) {
                if (t.name().equals(upper)) {
                    isKnown = true;
                    break;
                }
            }
            if (!isKnown) {
                log.warn("Unknown CharacterRoleType '{}' encountered — defaulting to {}",
                        value, result);
            }
        }

        return result;
    }
}
