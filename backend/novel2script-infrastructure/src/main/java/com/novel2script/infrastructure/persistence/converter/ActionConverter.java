package com.novel2script.infrastructure.persistence.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.domain.model.Action;
import com.novel2script.infrastructure.persistence.po.ActionPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ActionConverter {

    private final ObjectMapper objectMapper;

    public ActionPO toPo(Action action) {
        if (action == null) {
            return null;
        }
        ActionPO po = new ActionPO();
        po.setId(action.getId());
        po.setSceneId(action.getSceneId());
        po.setCharacterId(action.getCharacterId());
        po.setSequence(action.getSequence());
        po.setActionType(action.getActionType());
        po.setDescription(action.getDescription());
        po.setDurationMs(action.getDurationMs());
        po.setCreatedAt(action.getCreatedAt());
        return po;
    }

    public Action toDomain(ActionPO po) {
        if (po == null) {
            return null;
        }
        Action action = new Action();
        action.setId(po.getId());
        action.setSceneId(po.getSceneId());
        action.setCharacterId(po.getCharacterId());
        action.setSequence(po.getSequence() != null ? po.getSequence() : 0);
        action.setActionType(po.getActionType());
        action.setDescription(po.getDescription());
        action.setDurationMs(po.getDurationMs());
        action.setCreatedAt(po.getCreatedAt());
        return action;
    }

    public List<ActionPO> toPoList(List<Action> actions) {
        if (actions == null) {
            return Collections.emptyList();
        }
        return actions.stream().map(this::toPo).collect(Collectors.toList());
    }

    public List<Action> toDomainList(List<ActionPO> pos) {
        if (pos == null) {
            return Collections.emptyList();
        }
        return pos.stream().map(this::toDomain).collect(Collectors.toList());
    }
}
