package com.novel2script.application.service.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;

/**
 * A relationship extracted from the novel between two characters.
 * Returned as part of {@link CharacterExtractionResult}.
 */
@JsonDeserialize
public record ExtractedRelationship(

        @NotBlank
        @JsonProperty("target")
        String targetName,

        @NotBlank
        @JsonProperty("relation")
        String relationType
) {

    /**
     * Human-readable description of this relationship.
     */
    public String describe() {
        return relationType + " → " + targetName;
    }

    // ── Common relation types ─────────────────────────────

    public boolean isFamily() {
        return relationType.contains("家人") || relationType.contains("父母")
                || relationType.contains("兄弟") || relationType.contains("姐妹")
                || relationType.contains("子女") || relationType.contains("亲戚");
    }

    public boolean isRomantic() {
        return relationType.contains("恋人") || relationType.contains("情侣")
                || relationType.contains("夫妻") || relationType.contains("爱");
    }

    public boolean isHostile() {
        return relationType.contains("敌人") || relationType.contains("对手")
                || relationType.contains("仇");
    }

    public boolean isMentorship() {
        return relationType.contains("师徒") || relationType.contains("师父")
                || relationType.contains("弟子") || relationType.contains("老师");
    }
}
