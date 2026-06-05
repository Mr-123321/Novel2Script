package com.novel2script.domain.repository;

import com.novel2script.domain.model.Action;
import java.util.List;

public interface ActionRepository {
    void saveAll(List<Action> actions);
    List<Action> findBySceneIdOrderBySequence(Long sceneId);
    void deleteBySceneId(Long sceneId);
    void deleteByScriptId(Long scriptId);
}
