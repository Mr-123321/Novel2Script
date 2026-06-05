package com.novel2script.application.service.agent.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Records a consistency violation found in generated dialogue.
 *
 * <p>Used by {@code DialogueConsistencyChecker} to flag
 * dialogues that don't match character personality, setting, or
 * basic screenplay rules.
 */
@Getter
@Builder
public class ConsistencyIssue {

    /** Severity level of the issue. */
    private final Severity severity;

    /** Common issue type for categorization. */
    private final IssueType type;

    /** The dialogue line that has the issue (sequence number). */
    private final int dialogueSequence;

    /** The speaker name. */
    private final String speaker;

    /** Brief description of what's wrong. */
    private final String description;

    /** Suggested fix or alternative phrasing. */
    private final String suggestion;

    /** The problematic text fragment (if applicable). */
    private final String fragment;

    // ── Inner types ──────────────────────────────────

    public enum Severity {
        /** Critical: dialogue fundamentally contradicts character. Must fix. */
        ERROR,
        /** Warning: dialogue feels off but may be acceptable in context. */
        WARNING,
        /** Info: minor style suggestion for improvement. */
        INFO
    }

    public enum IssueType {
        /** Character personality mismatch (e.g. calm character shouting). */
        PERSONALITY_MISMATCH,
        /** Gender-inappropriate language (e.g. female saying "老子"). */
        GENDER_MISMATCH,
        /** Content too long for spoken dialogue (>50 chars). */
        LINE_TOO_LONG,
        /** Repetition of already stated information. */
        REPETITION,
        /** Exposition dump — explaining background through dialogue. */
        EXPOSITION_DUMP,
        /** Anachronistic or setting-inappropriate language. */
        SETTING_MISMATCH,
        /** Emotion inconsistent with character state. */
        EMOTION_INCONSISTENT,
        /** Missing logical connection to previous dialogue. */
        MISSING_CONNECTION,
        /** Other miscellaneous issue. */
        OTHER
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (S%03d %s): %s → %s",
                severity, type, dialogueSequence, speaker, description, suggestion);
    }
}
