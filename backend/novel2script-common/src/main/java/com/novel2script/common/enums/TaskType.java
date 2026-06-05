package com.novel2script.common.enums;

/**
 * AI task types mapped to model tiers for automatic routing.
 *
 * <p>Routing strategy:
 * <ul>
 *   <li><b>Cheap model</b> — simple structured extraction (chapter parsing, composition)</li>
 *   <li><b>Medium model</b> — batch/analytical tasks (scene segmentation, action generation)</li>
 *   <li><b>Strong model</b> — creative tasks (dialogue, character extraction, plot analysis)</li>
 * </ul>
 */
public enum TaskType {

    // ── Primary: Qwen (百炼) ── Fallback: DeepSeek when Qwen unavailable ──
    CHAPTER_PARSE("qwen"),
    SCRIPT_COMPOSE("qwen"),
    YAML_EXPORT("qwen"),

    CHARACTER_EXTRACTION("qwen"),
    CHARACTER_RESOLVE("qwen"),
    PLOT_EXTRACTION("qwen"),
    DIALOGUE_GENERATE("qwen"),
    STORYBOARD_GENERATE("qwen"),

    SCENE_SEGMENT("qwen"),
    ACTION_GENERATE("qwen");

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
