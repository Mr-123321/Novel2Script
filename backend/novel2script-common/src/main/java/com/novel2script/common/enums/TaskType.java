package com.novel2script.common.enums;

/**
 * AI task types mapped to model tiers for automatic routing.
 *
 * <p>Routing strategy (Alibaba Qwen multi-tier):
 * <ul>
 *   <li><b>qwen-turbo</b> — fast/cheap: character extraction, scene segmentation,
 *       action generation, chapter parsing, YAML export</li>
 *   <li><b>qwen-plus</b> — balanced: dialogue generation, character resolution,
 *       plot extraction, script composition, storyboard generation</li>
 *   <li><b>qwen-max</b> — most powerful (available for manual override on any task)</li>
 * </ul>
 *
 * <p>Fallback order: task's default provider → global default provider
 * → <i>any available provider</i>.
 */
public enum TaskType {

    // ── qwen-turbo: 极速廉价 — 简单结构化提取、场景切分、动作生成 ──
    CHAPTER_PARSE("qwen-turbo"),
    YAML_EXPORT("qwen-turbo"),
    CHARACTER_EXTRACTION("qwen-turbo"),
    SCENE_SEGMENT("qwen-turbo"),
    ACTION_GENERATE("qwen-turbo"),

    // ── qwen-plus: 均衡性价比 — 对白生成、角色消歧、情节分析、剧本合成 ──
    SCRIPT_COMPOSE("qwen-plus"),
    CHARACTER_RESOLVE("qwen-plus"),
    PLOT_EXTRACTION("qwen-plus"),
    DIALOGUE_GENERATE("qwen-plus"),
    STORYBOARD_GENERATE("qwen-plus");

    private final String defaultProvider;

    TaskType(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    /** The default AI provider recommended for this task type. */
    public String getDefaultProvider() {
        return defaultProvider;
    }

    /**
     * Find the matching TaskType for a given WorkflowStep.
     * Returns null if no match exists.
     */
    public static TaskType fromWorkflowStep(WorkflowStep step) {
        return switch (step) {
            case CHAPTER_PARSE -> CHAPTER_PARSE;
            case CHARACTER_EXTRACT -> CHARACTER_EXTRACTION;
            case CHARACTER_RESOLVE -> CHARACTER_RESOLVE;
            case PLOT_EXTRACT -> PLOT_EXTRACTION;
            case SCENE_SEGMENT -> SCENE_SEGMENT;
            case DIALOGUE_GENERATE -> DIALOGUE_GENERATE;
            case ACTION_GENERATE -> ACTION_GENERATE;
            case SCRIPT_COMPOSE -> SCRIPT_COMPOSE;
            case YAML_EXPORT -> YAML_EXPORT;
            case STORYBOARD_GENERATE -> STORYBOARD_GENERATE;
        };
    }
}
