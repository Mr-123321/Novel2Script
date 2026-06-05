package com.novel2script.domain.repository;

import com.novel2script.domain.model.Dialogue;
import java.util.List;

public interface DialogueRepository {
    void saveAll(List<Dialogue> dialogues);
    List<Dialogue> findBySceneIdOrderBySequence(Long sceneId);
    void deleteBySceneId(Long sceneId);
    void deleteByScriptId(Long scriptId);
}
