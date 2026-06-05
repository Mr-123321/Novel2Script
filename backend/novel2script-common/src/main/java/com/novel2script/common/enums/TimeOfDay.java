package com.novel2script.common.enums;

/**
 * Time of day classification for film scenes.
 *
 * <p>Standard script formatting uses these periods to
 * describe lighting, mood, and temporal context.
 */
public enum TimeOfDay {

    /** Early morning, sunrise (4:00-6:00) */
    DAWN("黎明", "DAWN"),

    /** Morning (6:00-12:00) */
    MORNING("早晨", "MORNING"),

    /** Afternoon (12:00-17:00) */
    AFTERNOON("下午", "AFTERNOON"),

    /** Evening / dusk (17:00-19:00) */
    EVENING("傍晚", "EVENING"),

    /** Night (19:00-23:00) */
    NIGHT("夜晚", "NIGHT"),

    /** Late night / midnight (23:00-4:00) */
    LATE_NIGHT("深夜", "LATE_NIGHT"),

    /** Unknown or unspecified time */
    UNKNOWN("未知", "UNKNOWN");

    private final String chineseLabel;
    private final String scriptLabel;

    TimeOfDay(String chineseLabel, String scriptLabel) {
        this.chineseLabel = chineseLabel;
        this.scriptLabel = scriptLabel;
    }

    public String getChineseLabel() {
        return chineseLabel;
    }

    /** Standard script abbreviation (DAWN, MORNING, etc.) */
    public String getScriptLabel() {
        return scriptLabel;
    }

    public boolean isDaytime() {
        return this == DAWN || this == MORNING || this == AFTERNOON;
    }

    public boolean isNighttime() {
        return this == EVENING || this == NIGHT || this == LATE_NIGHT;
    }

    /**
     * Parse from Chinese label, script label, or enum name.
     * Returns UNKNOWN for unrecognized input.
     */
    public static TimeOfDay fromLabel(String text) {
        if (text == null || text.isBlank()) {
            return UNKNOWN;
        }
        String trimmed = text.trim();

        // Exact match: Chinese label
        for (TimeOfDay tod : values()) {
            if (tod.chineseLabel.equals(trimmed)) {
                return tod;
            }
        }
        // Exact match: script label
        for (TimeOfDay tod : values()) {
            if (tod.scriptLabel.equalsIgnoreCase(trimmed)) {
                return tod;
            }
        }
        // Exact match: enum name
        for (TimeOfDay tod : values()) {
            if (tod.name().equalsIgnoreCase(trimmed)) {
                return tod;
            }
        }

        // Fuzzy match
        String lower = trimmed.toLowerCase();
        if (lower.contains("黎明") || lower.contains("日出") || lower.contains("清晨") || lower.contains("dawn") || lower.contains("sunrise")) {
            return DAWN;
        }
        if (lower.contains("早上") || lower.contains("上午") || lower.contains("早晨") || lower.contains("morning")) {
            return MORNING;
        }
        if (lower.contains("午后") || lower.contains("下午") || lower.contains("中午") || lower.contains("afternoon") || lower.contains("noon")) {
            return AFTERNOON;
        }
        if (lower.contains("傍晚") || lower.contains("黄昏") || lower.contains("日落") || lower.contains("evening") || lower.contains("dusk") || lower.contains("sunset")) {
            return EVENING;
        }
        if (lower.contains("深夜") || lower.contains("半夜") || lower.contains("午夜") || lower.contains("late_night") || lower.contains("midnight")) {
            return LATE_NIGHT;
        }
        if (lower.contains("夜晚") || lower.contains("晚上") || lower.contains("夜间") || lower.contains("night")) {
            return NIGHT;
        }
        if (lower.contains("白天") || lower.contains("日间") || lower.contains("day")) {
            return MORNING; // default daytime fallback
        }

        return UNKNOWN;
    }

    /**
     * Infer time of day from text content using keyword analysis.
     */
    public static TimeOfDay inferFromContent(String content) {
        if (content == null || content.isBlank()) {
            return UNKNOWN;
        }

        // Score each time period based on keyword presence
        int[] scores = new int[values().length];

        // Dawn keywords
        if (containsAny(content, "黎明", "日出", "破晓", "晨曦", "拂晓", "清晨", "蒙蒙亮")) {
            scores[DAWN.ordinal()] += 3;
        }
        // Morning keywords
        if (containsAny(content, "早晨", "早上", "上午", "早餐", "早自习", "晨", "朝阳")) {
            scores[MORNING.ordinal()] += 3;
        }
        // Afternoon keywords
        if (containsAny(content, "下午", "午后", "中午", "午餐", "午睡", "午", "烈日")) {
            scores[AFTERNOON.ordinal()] += 3;
        }
        // Evening keywords
        if (containsAny(content, "傍晚", "黄昏", "日落", "晚餐", "夕阳", "晚霞", "天黑前")) {
            scores[EVENING.ordinal()] += 3;
        }
        // Night keywords
        if (containsAny(content, "夜晚", "晚上", "天黑", "月亮", "星空", "晚饭后", "夜间")) {
            scores[NIGHT.ordinal()] += 3;
        }
        // Late night keywords
        if (containsAny(content, "深夜", "半夜", "午夜", "凌晨", "三更", "夜深")) {
            scores[LATE_NIGHT.ordinal()] += 3;
        }

        // Find the highest scoring time
        int maxScore = 0;
        TimeOfDay best = UNKNOWN;
        for (TimeOfDay tod : values()) {
            if (scores[tod.ordinal()] > maxScore) {
                maxScore = scores[tod.ordinal()];
                best = tod;
            }
        }

        return best;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
