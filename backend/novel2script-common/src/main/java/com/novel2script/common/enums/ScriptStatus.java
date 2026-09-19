package com.novel2script.common.enums;

/**
 * Status of a script during generation.
 */
public enum ScriptStatus {

    /** Initial draft, not yet generated */
    DRAFT,

    /** AI is actively generating content */
    GENERATING,

    /** Generation completed successfully */
    COMPLETED,

    /**
     * Generation finished, but some content could not be produced by AI
     * (e.g. scenes whose dialogue/action generation failed).
     * Those scenes are left empty and flagged for manual completion —
     * nothing is fabricated to fill the gap.
     */
    PARTIAL,

    /** Generation failed with errors */
    FAILED
}
