package com.novel2script.domain.vo;

/**
 * Value object for character relationships.
 */
public record Relationship(
        String targetName,
        String relationType
) {}
