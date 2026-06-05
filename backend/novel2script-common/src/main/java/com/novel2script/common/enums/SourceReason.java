package com.novel2script.common.enums;

/**
 * Reason why a new scene was created during segmentation.
 *
 * <p>Each value represents a different trigger for scene
 * boundary detection in the narrative-to-script pipeline.
 */
public enum SourceReason {

    /** Location changed (e.g. from "教室" to "操场") */
    LOCATION("地点变化"),

    /** Time changed significantly (> 30 min or explicit jump) */
    TIME("时间变化"),

    /** Major character(s) entered or exited (> 50% turnover) */
    CHARACTER("人物变化"),

    /** Conflict escalated or shifted type */
    CONFLICT("冲突变化"),

    /** Natural chapter boundary */
    CHAPTER_BOUNDARY("章节边界");

    private final String label;

    SourceReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Parse from Chinese label or enum name.
     */
    public static SourceReason fromLabel(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();

        // Exact match: Chinese label
        for (SourceReason sr : values()) {
            if (sr.label.equals(trimmed)) {
                return sr;
            }
        }
        // Exact match: enum name
        for (SourceReason sr : values()) {
            if (sr.name().equalsIgnoreCase(trimmed)) {
                return sr;
            }
        }

        // Fuzzy match
        String lower = trimmed.toLowerCase();
        if (lower.contains("地点") || lower.contains("位置") || lower.contains("location")) {
            return LOCATION;
        }
        if (lower.contains("时间") || lower.contains("time")) {
            return TIME;
        }
        if (lower.contains("人物") || lower.contains("角色") || lower.contains("character")) {
            return CHARACTER;
        }
        if (lower.contains("冲突") || lower.contains("conflict")) {
            return CONFLICT;
        }
        if (lower.contains("章节") || lower.contains("chapter")) {
            return CHAPTER_BOUNDARY;
        }

        return null;
    }
}
