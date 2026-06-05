package com.novel2script.domain.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single few-shot example — input/output pair used to guide the AI model.
 * Multiple examples form the few-shot section of a prompt template.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FewShot {

    /** User-side input text (what the model receives). */
    private String input;

    /** Expected assistant-side output text (what the model should produce). */
    private String output;

    /**
     * Format this example as part of a few-shot instruction block.
     */
    public String toConversationBlock() {
        return "User: " + input + "\nAssistant: " + output;
    }
}
