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

    /** Generation failed with errors */
    FAILED
}
