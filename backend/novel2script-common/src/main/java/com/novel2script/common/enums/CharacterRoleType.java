package com.novel2script.common.enums;

/**
 * Role type classification for characters in a script.
 *
 * <p>Includes {@link #fromValue(String)} for tolerant deserialization —
 * unknown values silently default to {@link #SUPPORTING}.
 * For Jackson integration, register
 * {@code com.novel2script.infrastructure.config.CharacterRoleTypeDeserializer}.
 */
public enum CharacterRoleType {

    /** Main character driving the story */
    PROTAGONIST,

    /** Second protagonist / co-lead (e.g. Watson to Holmes) */
    DEUTERAGONIST,

    /** Character opposing the protagonist */
    ANTAGONIST,

    /** Supporting character with significant presence */
    SUPPORTING,

    /** Minor character with limited appearance */
    MINOR;

    /**
     * Resolve a role type string case-insensitively.
     * Unknown values silently default to {@link #SUPPORTING}.
     *
     * @param value the raw string (e.g. "PROTAGONIST", "deuteragonist", "villain")
     * @return the matching enum constant, or SUPPORTING if unrecognized
     */
    public static CharacterRoleType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return SUPPORTING;
        }
        String trimmed = value.trim().toUpperCase();
        for (CharacterRoleType type : values()) {
            if (type.name().equals(trimmed)) {
                return type;
            }
        }
        return SUPPORTING;
    }
}
