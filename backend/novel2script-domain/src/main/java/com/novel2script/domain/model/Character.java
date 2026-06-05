package com.novel2script.domain.model;

import com.novel2script.common.enums.CharacterRoleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A character identified in the novel/script.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Character {

    private Long id;
    private Long scriptId;
    private String canonicalName;
    private List<String> aliases;
    private CharacterRoleType roleType;
    private String gender;       // MALE / FEMALE / UNKNOWN
    private String ageRange;
    private String description;
    private List<String> personality;
    private List<Relationship> relationships;
    private int appearanceCount;
    private Long firstAppearance;
    private String embeddingId;
    private boolean resolved;
    private List<Long> mergedFrom;

    private LocalDateTime createdAt;

    /**
     * Inner value object for character relationships.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Relationship {
        private String target;
        private String relation;
    }

    public String getPrimaryAlias() {
        if (aliases != null && !aliases.isEmpty()) {
            return aliases.get(0);
        }
        return canonicalName;
    }

    public boolean isProtagonist() {
        return roleType == CharacterRoleType.PROTAGONIST;
    }

    public boolean isAntagonist() {
        return roleType == CharacterRoleType.ANTAGONIST;
    }
}
