package com.novel2script.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.common.enums.ScriptStatus;
import com.novel2script.domain.model.Script;
import com.novel2script.infrastructure.persistence.po.ScriptPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ScriptConverter {

    private final ObjectMapper objectMapper;

    public ScriptPO toPo(Script script) {
        if (script == null) {
            return null;
        }
        ScriptPO po = new ScriptPO();
        po.setId(script.getId());
        po.setNovelId(script.getNovelId());
        po.setTitle(script.getTitle());
        po.setVersion(script.getVersion());
        po.setSceneCount(script.getSceneCount());
        po.setCharacterCount(script.getCharacterCount());
        po.setDialogueCount(script.getDialogueCount());
        po.setYamlContent(script.getYamlContent());
        po.setStatus(script.getStatus() != null ? script.getStatus().name() : null);
        po.setProgress(BigDecimal.valueOf(script.getProgress()));
        po.setWorkflowState(toJsonMap(script.getWorkflowState()));
        po.setCreatedAt(script.getCreatedAt());
        po.setUpdatedAt(script.getUpdatedAt());
        return po;
    }

    public Script toDomain(ScriptPO po) {
        if (po == null) {
            return null;
        }
        Script script = new Script();
        script.setId(po.getId());
        script.setNovelId(po.getNovelId());
        script.setTitle(po.getTitle());
        script.setVersion(po.getVersion() != null ? po.getVersion() : 0);
        script.setSceneCount(po.getSceneCount() != null ? po.getSceneCount() : 0);
        script.setCharacterCount(po.getCharacterCount() != null ? po.getCharacterCount() : 0);
        script.setDialogueCount(po.getDialogueCount() != null ? po.getDialogueCount() : 0);
        script.setYamlContent(po.getYamlContent());
        script.setStatus(po.getStatus() != null ? ScriptStatus.valueOf(po.getStatus()) : null);
        script.setProgress(po.getProgress() != null ? po.getProgress().doubleValue() : 0.0);
        script.setWorkflowState(fromJsonToMap(po.getWorkflowState()));
        script.setCreatedAt(po.getCreatedAt());
        script.setUpdatedAt(po.getUpdatedAt());
        return script;
    }

    public List<ScriptPO> toPoList(List<Script> scripts) {
        if (scripts == null) {
            return Collections.emptyList();
        }
        return scripts.stream().map(this::toPo).collect(Collectors.toList());
    }

    public List<Script> toDomainList(List<ScriptPO> pos) {
        if (pos == null) {
            return Collections.emptyList();
        }
        return pos.stream().map(this::toDomain).collect(Collectors.toList());
    }

    private String toJsonMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize workflowState to JSON", e);
        }
    }

    private Map<String, Object> fromJsonToMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize workflowState from JSON", e);
        }
    }
}
