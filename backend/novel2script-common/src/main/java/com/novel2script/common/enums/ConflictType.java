package com.novel2script.common.enums;

/**
 * Conflict type classification for plot events.
 *
 * <p>Based on the classic literary conflict taxonomy. Each type
 * represents a distinct dramatic tension in narrative structure.
 */
public enum ConflictType {

    /** Character against another character (e.g. argument, battle, rivalry) */
    PERSON_VS_PERSON("人与人"),

    /** Character against their own inner demons, doubts, or dilemmas */
    PERSON_VS_SELF("人与自我"),

    /** Character against societal norms, institutions, or groups */
    PERSON_VS_SOCIETY("人与社会"),

    /** Character against natural forces (storms, disease, wilderness) */
    PERSON_VS_NATURE("人与自然"),

    /** Character against technological or artificial threats */
    PERSON_VS_TECHNOLOGY("人与科技"),

    /** Character against destiny, fate, or supernatural forces */
    PERSON_VS_FATE("人与命运");

    private final String label;

    ConflictType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Parse from Chinese label or enum name.
     */
    public static ConflictType fromLabel(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (ConflictType ct : values()) {
            if (ct.label.equals(text.trim()) || ct.name().equalsIgnoreCase(text.trim())) {
                return ct;
            }
        }
        // Fuzzy match for AI-generated text
        String lower = text.trim();
        if (lower.contains("人") && lower.contains("人") && !lower.contains("自我") && !lower.contains("社会")
                && !lower.contains("自然") && !lower.contains("科技") && !lower.contains("命运")) {
            return PERSON_VS_PERSON;
        }
        if (lower.contains("自我") || lower.contains("内心") || lower.contains("矛盾")) {
            return PERSON_VS_SELF;
        }
        if (lower.contains("社会") || lower.contains("制度") || lower.contains("群体")) {
            return PERSON_VS_SOCIETY;
        }
        if (lower.contains("自然") || lower.contains("环境") || lower.contains("灾难")) {
            return PERSON_VS_NATURE;
        }
        if (lower.contains("科技") || lower.contains("机器") || lower.contains("技术")) {
            return PERSON_VS_TECHNOLOGY;
        }
        if (lower.contains("命运") || lower.contains("宿命") || lower.contains("天意")) {
            return PERSON_VS_FATE;
        }
        return null;
    }
}
