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
     * 生成完成，但有部分场景的对白/动作生成失败，需人工补全。
     * <p>Those scenes are left empty and flagged for manual completion —
     * nothing is fabricated to fill the gap. The script is still exportable.
     */
    COMPLETED_WITH_WARNINGS,

    /** Generation failed with errors */
    FAILED
}
