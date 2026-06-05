package com.novel2script.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.common.enums.SourceReason;
import com.novel2script.common.enums.TimeOfDay;
import com.novel2script.domain.model.Scene;
import com.novel2script.infrastructure.persistence.po.ScenePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SceneConverter {

    private final ObjectMapper objectMapper;

    public ScenePO toPo(Scene scene) {
        if (scene == null) {
            return null;
        }
        ScenePO po = new ScenePO();
        po.setId(scene.getId());
        po.setScriptId(scene.getScriptId());
        po.setSceneNumber(scene.getSceneNumber());
        po.setTitle(scene.getTitle());
        po.setLocation(scene.getLocation());
        po.setTimeOfDay(scene.getTimeOfDay() != null ? scene.getTimeOfDay().name() : null);
        po.setIsInterior(scene.isInterior() ? 1 : 0);
        po.setSummary(scene.getSummary());
        po.setMood(scene.getMood());
        po.setChapterIds(toJson(scene.getChapterIds()));
        po.setSourceReason(scene.getSourceReason() != null ? scene.getSourceReason().name() : null);
        po.setCreatedAt(scene.getCreatedAt());
        return po;
    }

    public Scene toDomain(ScenePO po) {
        if (po == null) {
            return null;
        }
        Scene scene = new Scene();
        scene.setId(po.getId());
        scene.setScriptId(po.getScriptId());
        scene.setSceneNumber(po.getSceneNumber());
        scene.setTitle(po.getTitle());
        scene.setLocation(po.getLocation());
        scene.setTimeOfDay(TimeOfDay.fromLabel(po.getTimeOfDay()));
        scene.setInterior(po.getIsInterior() != null && po.getIsInterior() == 1);
        scene.setSummary(po.getSummary());
        scene.setMood(po.getMood());
        scene.setChapterIds(fromJson(po.getChapterIds()));
        scene.setSourceReason(SourceReason.fromLabel(po.getSourceReason()));
        scene.setCreatedAt(po.getCreatedAt());
        return scene;
    }

    public List<ScenePO> toPoList(List<Scene> scenes) {
        if (scenes == null) {
            return Collections.emptyList();
        }
        return scenes.stream().map(this::toPo).collect(Collectors.toList());
    }

    public List<Scene> toDomainList(List<ScenePO> pos) {
        if (pos == null) {
            return Collections.emptyList();
        }
        return pos.stream().map(this::toDomain).collect(Collectors.toList());
    }

    private String toJson(List<Long> chapterIds) {
        if (chapterIds == null || chapterIds.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(chapterIds);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize chapterIds to JSON", e);
        }
    }

    private List<Long> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize chapterIds from JSON", e);
        }
    }
}
