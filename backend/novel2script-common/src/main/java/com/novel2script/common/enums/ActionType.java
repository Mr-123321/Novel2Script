package com.novel2script.common.enums;

/**
 * Classification of action types in film script stage directions.
 *
 * <h3>Semantics</h3>
 * <ul>
 *   <li><b>ACTION</b> — character-initiated physical movement
 *       (e.g. "林川推开门", "李雪拿起杯子")</li>
 *   <li><b>REACTION</b> — character's response to a stimulus
 *       (e.g. "李雪猛地回头", "林川瞳孔微缩")</li>
 *   <li><b>BEAT</b> — a pause, hesitation, or silence
 *       (e.g. "一阵沉默", "林川停下脚步")</li>
 *   <li><b>BUSINESS</b> — background/atmospheric action by minor characters
 *       (e.g. "服务员擦拭杯子", "窗外树叶飘落")</li>
 * </ul>
 *
 * <p>The {@link #classify(String)} method can auto-classify an action
 * description based on keyword heuristics — useful as a fallback when
 * the AI model doesn't provide a type.
 */
public enum ActionType {

    ACTION("主动动作"),
    REACTION("反应"),
    BEAT("节拍"),
    BUSINESS("背景动作");

    private final String label;

    ActionType(String label) {
        this.label = label;
    }

    /** Human-readable Chinese label. */
    public String getLabel() {
        return label;
    }

    /**
     * Try to parse a string value into an ActionType.
     * Accepts both enum names and Chinese labels.
     *
     * @return the matching type, or {@code null} if unrecognized
     */
    public static ActionType fromString(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim().toUpperCase();

        // Try exact enum name match
        for (ActionType type : values()) {
            if (type.name().equals(trimmed)) return type;
            if (type.label.equals(value.trim())) return type;
        }

        // Fuzzy match for common variations
        return switch (trimmed) {
            case "主动动作", "动作" -> ACTION;
            case "反应动作", "反应" -> REACTION;
            case "节拍", "停顿", "停顿动作" -> BEAT;
            case "背景", "背景动作", "环境" -> BUSINESS;
            default -> null;
        };
    }

    /**
     * Classify an action description based on keyword heuristics.
     * This is a rule-based fallback when the AI doesn't provide a type.
     *
     * <h3>Classification rules (checked in order):</h3>
     * <ol>
     *   <li>BEAT — silence/pause/hesitation keywords</li>
     *   <li>REACTION — perception-response pattern ("看见"/"听见" + reaction)</li>
     *   <li>BUSINESS — minor character / background / atmospheric</li>
     *   <li>ACTION — everything else (default)</li>
     * </ol>
     */
    public static ActionType classify(String description) {
        if (description == null || description.isBlank()) {
            return ACTION;
        }

        String d = description.trim();

        // ── BEAT detection ────────────────────────────
        if (containsAny(d,
                "沉默", "停顿", "犹豫", "停下", "站住", "愣住",
                "僵住", "愣神", "迟疑", "顿了顿", "欲言又止",
                "一时无言", "没有回答", "没说话", "没有说话",
                "半晌", "片刻", "一阵沉默", "一片寂静",
                "漫长的沉默", "安静下来", "静默")) {
            return BEAT;
        }

        // ── REACTION detection ────────────────────────
        if (containsAny(d,
                "猛地回头", "猛地抬头", "猛地转头", "猛地转过身",
                "瞳孔微缩", "瞳孔放大", "脸色一变", "神色一变",
                "神情一变", "表情一变", "微微一愣", "一怔",
                "吃了一惊", "吃了一愣",
                "回头", "抬头", "转头", "转身",
                "望去", "看向", "望过去",
                "倒吸一口凉气", "倒吸凉气", "深吸一口气",
                "后退", "退了一步", "后退半步", "后退一步",
                "后退了两步", "往后一缩", "浑身一震", "身体一震",
                "惊呆", "愣住", "瞪大", "睁大")) {
            return REACTION;
        }

        // ── BUSINESS detection ────────────────────────
        if (containsAny(d,
                "擦拭", "打扫", "整理", "浇花", "扫地", "拖地",
                "窗外", "远处", "背景", "灯光", "音乐",
                "风吹", "树叶", "飘落", "落下", "流水",
                "倒水", "泡茶", "上菜", "端盘", "送餐",
                "路过", "走过", "闲逛", "巡逻", "站岗")) {
            return BUSINESS;
        }

        // ── Default: ACTION ───────────────────────────
        return ACTION;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
