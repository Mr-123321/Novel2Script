package com.novel2script.common.enums;

/**
 * Provenance of a piece of generated content (dialogue / action).
 *
 * <p>The pipeline can produce content through three distinct paths, and they
 * must never be conflated:
 * <ul>
 *   <li>{@link #AI} — produced by the language model;</li>
 *   <li>{@link #REGEX} — deterministically extracted from the source novel
 *       (rule/regex based, no model involved);</li>
 *   <li>{@link #MANUAL} — written or edited by a human.</li>
 * </ul>
 *
 * <p>Recorded on every row so downstream consumers — experiment scripts that
 * must exclude regex-extracted samples, or a UI that flags content awaiting
 * human review — can tell them apart.
 */
public enum ContentSource {

    /** Produced by the AI model. */
    AI,

    /** Deterministically extracted from the source text (no model involved). */
    REGEX,

    /** Written or edited by a human. */
    MANUAL
}
