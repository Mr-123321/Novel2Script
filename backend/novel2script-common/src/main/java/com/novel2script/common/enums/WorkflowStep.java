package com.novel2script.common.enums;

/**
 * All steps in the script generation workflow pipeline.
 */
public enum WorkflowStep {

    CHAPTER_PARSE       ("ChapterParserAgent",        1,  false),
    CHARACTER_EXTRACT   ("CharacterAgent",            2,  true),
    CHARACTER_RESOLVE   ("CharacterResolverAgent",    3,  true),
    PLOT_EXTRACT        ("PlotExtractionAgent",       4,  true),
    SCENE_SEGMENT       ("SceneAgent",                5,  true),
    DIALOGUE_GENERATE   ("DialogueAgent",             6,  true),
    ACTION_GENERATE     ("ActionAgent",               7,  true),
    SCRIPT_COMPOSE      ("ScriptComposerAgent",       8,  false),
    YAML_EXPORT         ("YamlExporter",              9,  false),
    STORYBOARD_GENERATE ("StoryboardAgent",          10,  true);

    private final String agentName;
    private final int order;
    private final boolean retryable;

    WorkflowStep(String agentName, int order, boolean retryable) {
        this.agentName = agentName;
        this.order = order;
        this.retryable = retryable;
    }

    public String getAgentName() {
        return agentName;
    }

    public int getOrder() {
        return order;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public static WorkflowStep next(WorkflowStep current) {
        int nextOrder = current.order + 1;
        for (WorkflowStep step : values()) {
            if (step.order == nextOrder) {
                return step;
            }
        }
        return null;
    }

    public static WorkflowStep first() {
        return CHAPTER_PARSE;
    }

    public boolean isLast() {
        return this == STORYBOARD_GENERATE;
    }
}
