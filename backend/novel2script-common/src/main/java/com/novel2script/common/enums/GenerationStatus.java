package com.novel2script.common.enums;

/**
 * Per-scene generation outcome for one content type (dialogue or action).
 *
 * <p>Distinct from {@link ContentSource}: a scene may have successfully
 * generated dialogue lines, or it may have failed — in which case it is left
 * empty on purpose (nothing is fabricated) and flagged {@link #FAILED} so the
 * UI can surface it as 待补全.
 */
public enum GenerationStatus {

    /** Every line for this scene was produced successfully. */
    COMPLETED,

    /**
     * Generation failed for this scene. The content is intentionally left
     * empty — never padded with fabricated lines — and awaits manual completion.
     */
    FAILED
}
