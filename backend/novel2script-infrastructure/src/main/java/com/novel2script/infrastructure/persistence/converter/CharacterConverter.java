package com.novel2script.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.common.enums.CharacterRoleType;
import com.novel2script.domain.model.Character;
import com.novel2script.infrastructure.persistence.po.CharacterPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CharacterConverter {

    private final ObjectMapper objectMapper;

    public CharacterPO toPo(Character character) {
        if (character == null) {
            return null;
        }
        CharacterPO po = new CharacterPO();
        po.setId(character.getId());
        po.setScriptId(character.getScriptId());
        po.setCanonicalName(character.getCanonicalName());
        po.setAliases(toJsonStringList(character.getAliases()));
        po.setRoleType(character.getRoleType() != null ? character.getRoleType().name() : null);
        po.setGender(character.getGender());
        po.setAgeRange(character.getAgeRange());
        po.setDescription(character.getDescription());
        po.setPersonality(toJsonStringList(character.getPersonality()));
        po.setRelationships(toJsonRelationships(character.getRelationships()));
        po.setAppearanceCount(character.getAppearanceCount());
        po.setFirstAppearance(character.getFirstAppearance());
        po.setEmbeddingId(character.getEmbeddingId());
        po.setIsResolved(character.isResolved() ? 1 : 0);
        po.setMergedFrom(toJsonLongList(character.getMergedFrom()));
        po.setCreatedAt(character.getCreatedAt());
        return po;
    }

    public Character toDomain(CharacterPO po) {
        if (po == null) {
            return null;
        }
        Character character = new Character();
        character.setId(po.getId());
        character.setScriptId(po.getScriptId());
        character.setCanonicalName(po.getCanonicalName());
        character.setAliases(fromJsonToStringList(po.getAliases()));
        character.setRoleType(po.getRoleType() != null ? CharacterRoleType.valueOf(po.getRoleType()) : null);
        character.setGender(po.getGender());
        character.setAgeRange(po.getAgeRange());
        character.setDescription(po.getDescription());
        character.setPersonality(fromJsonToStringList(po.getPersonality()));
        character.setRelationships(fromJsonToRelationships(po.getRelationships()));
        character.setAppearanceCount(po.getAppearanceCount() != null ? po.getAppearanceCount() : 0);
        character.setFirstAppearance(po.getFirstAppearance());
        character.setEmbeddingId(po.getEmbeddingId());
        character.setResolved(po.getIsResolved() != null && po.getIsResolved() == 1);
        character.setMergedFrom(fromJsonToLongList(po.getMergedFrom()));
        character.setCreatedAt(po.getCreatedAt());
        return character;
    }

    public List<CharacterPO> toPoList(List<Character> characters) {
        if (characters == null) {
            return Collections.emptyList();
        }
        return characters.stream().map(this::toPo).collect(Collectors.toList());
    }

    public List<Character> toDomainList(List<CharacterPO> pos) {
        if (pos == null) {
            return Collections.emptyList();
        }
        return pos.stream().map(this::toDomain).collect(Collectors.toList());
    }

    private String toJsonStringList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize list to JSON", e);
        }
    }

    private List<String> fromJsonToStringList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize list from JSON", e);
        }
    }

    private String toJsonLongList(List<Long> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize list to JSON", e);
        }
    }

    private List<Long> fromJsonToLongList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize list from JSON", e);
        }
    }

    private String toJsonRelationships(List<Character.Relationship> relationships) {
        if (relationships == null || relationships.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(relationships);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize relationships to JSON", e);
        }
    }

    private List<Character.Relationship> fromJsonToRelationships(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, String>> rawList = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, String>>>() {});
            return rawList.stream()
                    .map(map -> Character.Relationship.builder()
                            .target(map.get("target"))
                            .relation(map.get("relation"))
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize relationships from JSON", e);
        }
    }
}
