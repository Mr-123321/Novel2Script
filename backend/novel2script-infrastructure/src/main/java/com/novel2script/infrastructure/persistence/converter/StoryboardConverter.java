package com.novel2script.infrastructure.persistence.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.domain.model.Storyboard;
import com.novel2script.infrastructure.persistence.po.ShotPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class StoryboardConverter {

    private final ObjectMapper objectMapper;

    public ShotPO toPo(Storyboard storyboard) {
        if (storyboard == null) {
            return null;
        }
        ShotPO po = new ShotPO();
        po.setId(storyboard.getId());
        po.setSceneId(storyboard.getSceneId());
        po.setShotNumber(storyboard.getShotNumber());
        po.setCamera(storyboard.getCamera());
        po.setAngle(storyboard.getAngle());
        po.setDurationSec(storyboard.getDurationSec());
        po.setDescription(storyboard.getDescription());
        po.setMovement(storyboard.getMovement());
        po.setTransition(storyboard.getTransition());
        po.setCreatedAt(storyboard.getCreatedAt());
        return po;
    }

    public Storyboard toDomain(ShotPO po) {
        if (po == null) {
            return null;
        }
        Storyboard storyboard = new Storyboard();
        storyboard.setId(po.getId());
        storyboard.setSceneId(po.getSceneId());
        storyboard.setShotNumber(po.getShotNumber() != null ? po.getShotNumber() : 0);
        storyboard.setCamera(po.getCamera());
        storyboard.setAngle(po.getAngle());
        storyboard.setDurationSec(po.getDurationSec());
        storyboard.setDescription(po.getDescription());
        storyboard.setMovement(po.getMovement());
        storyboard.setTransition(po.getTransition());
        storyboard.setCreatedAt(po.getCreatedAt());
        return storyboard;
    }

    public List<ShotPO> toPoList(List<Storyboard> storyboards) {
        if (storyboards == null) {
            return Collections.emptyList();
        }
        return storyboards.stream().map(this::toPo).collect(Collectors.toList());
    }

    public List<Storyboard> toDomainList(List<ShotPO> pos) {
        if (pos == null) {
            return Collections.emptyList();
        }
        return pos.stream().map(this::toDomain).collect(Collectors.toList());
    }
}
