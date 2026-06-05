package com.novel2script.common.enums;

/**
 * Status of a novel throughout the processing pipeline.
 */
public enum NovelStatus {

    /** Initial state after upload */
    UPLOADED,

    /** Currently being parsed into chapters */
    PARSING,

    /** Chapters have been extracted successfully */
    PARSED,

    /** AI agents are processing the content */
    PROCESSING,

    /** Full pipeline completed successfully */
    COMPLETED,

    /** Pipeline failed with errors */
    FAILED
}
