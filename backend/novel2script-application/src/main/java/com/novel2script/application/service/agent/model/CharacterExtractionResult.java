package com.novel2script.application.service.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.novel2script.common.enums.CharacterRoleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Result of extracting a single character from novel chapters.
 * Produced by {@link com.novel2script.application.service.agent.CharacterAgent}.
 *
 * <p>This is the AI-extraction DTO — distinct from the domain
 * {@link com.novel2script.domain.model.Character} entity which lives in the
 * persistence layer. Use {@link #toDomainCharacter()} to convert.
 */
@JsonDeserialize
public record CharacterExtractionResult(

        @NotBlank
        @JsonProperty("name")
        String name,

        @JsonProperty("aliases")
        List<String> aliases,

        @NotNull
        @JsonProperty("role_type")
        CharacterRoleType roleType,

        @JsonProperty("gender")
        String gender,

        @JsonProperty("age_range")
        String ageRange,

        @NotBlank
        @JsonProperty("description")
        String description,

        @JsonProperty("personality")
        List<String> personality,

        @JsonProperty("relationships")
        List<ExtractedRelationship> relationships,

        @JsonProperty("first_appearance_chapter")
        String firstAppearanceChapter,

        @JsonProperty("appearance_count")
        int appearanceCount
) {

    /**
     * Returns an immutable empty list for nullable fields.
     */
    public List<String> aliases() {
        return aliases != null ? aliases : Collections.emptyList();
    }

    public List<String> personality() {
        return personality != null ? personality : Collections.emptyList();
    }

    public List<ExtractedRelationship> relationships() {
        return relationships != null ? relationships : Collections.emptyList();
    }

    /**
     * Whether this character is the protagonist.
     */
    public boolean isProtagonist() {
        return roleType == CharacterRoleType.PROTAGONIST;
    }

    /**
     * Whether this character is the antagonist.
     */
    public boolean isAntagonist() {
        return roleType == CharacterRoleType.ANTAGONIST;
    }

    /**
     * Convert this extraction result to a domain {@code Character} entity
     * suitable for persistence. Note: the domain Character class uses
     * {@code Character.Relationship} as an inner class.
     *
     * @return a new domain Character (not yet persisted)
     */
    public com.novel2script.domain.model.Character toDomainCharacter() {
        List<com.novel2script.domain.model.Character.Relationship> domainRelationships =
                relationships().stream()
                        .map(r -> com.novel2script.domain.model.Character.Relationship.builder()
                                .target(r.targetName())
                                .relation(r.relationType())
                                .build())
                        .toList();

        return com.novel2script.domain.model.Character.builder()
                .canonicalName(name)
                .aliases(aliases())
                .roleType(roleType)
                .gender(gender)
                .ageRange(ageRange)
                .description(description)
                .personality(personality())
                .relationships(domainRelationships)
                .appearanceCount(appearanceCount)
                .build();
    }

    @Override
    public String toString() {
        return String.format("CharacterExtractionResult{name='%s', role=%s, aliases=%d, relationships=%d}",
                name, roleType, aliases().size(), relationships().size());
    }
}
