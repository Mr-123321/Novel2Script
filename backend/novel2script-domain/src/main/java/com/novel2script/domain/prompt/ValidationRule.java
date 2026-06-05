package com.novel2script.domain.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single validation rule applied to the AI response after extraction.
 * Used to verify that the model output conforms to expected structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRule {

    /** Field path in dot notation, e.g. "characters[].name". */
    private String field;

    /** Rule identifier: notBlank, enum_match, minLength, maxLength, pattern, notNull. */
    private String rule;

    /** Optional parameter for the rule (e.g. enum values, min/max). */
    private String param;

    public boolean isNotBlank()         { return "notBlank".equals(rule); }
    public boolean isEnumMatch()        { return "enum_match".equals(rule); }
    public boolean isNotNull()          { return "notNull".equals(rule); }
    public boolean isMinLength()        { return "minLength".equals(rule); }
    public boolean isMaxLength()        { return "maxLength".equals(rule); }
    public boolean isPattern()          { return "pattern".equals(rule); }
}
