package com.novel2script.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.novel2script.domain.handler.LongListTypeHandler;
import com.novel2script.domain.handler.RelationshipListTypeHandler;
import com.novel2script.domain.handler.StringListTypeHandler;
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
@TableName(value = "characters", autoResultMap = true)
public class Character {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("script_id")
    private Long scriptId;

    @TableField("canonical_name")
    private String canonicalName;

    @TableField(typeHandler = StringListTypeHandler.class)
    @Builder.Default
    private List<String> aliases = new ArrayList<>();

    @TableField("role_type")
    private CharacterRoleType roleType;

    private String gender;       // MALE / FEMALE / UNKNOWN

    @TableField("age_range")
    private String ageRange;

    private String description;

    @TableField(typeHandler = StringListTypeHandler.class)
    @Builder.Default
    private List<String> personality = new ArrayList<>();

    @TableField(typeHandler = RelationshipListTypeHandler.class)
    @Builder.Default
    private List<Relationship> relationships = new ArrayList<>();

    @TableField("appearance_count")
    private int appearanceCount;

    @TableField("first_appearance")
    private Long firstAppearance;

    @TableField("embedding_id")
    private String embeddingId;

    /** Maps to TINYINT(1) is_resolved column */
    @TableField("is_resolved")
    private boolean resolved;

    @TableField(value = "merged_from", typeHandler = LongListTypeHandler.class)
    @Builder.Default
    private List<Long> mergedFrom = new ArrayList<>();

    @TableField("created_at")
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
