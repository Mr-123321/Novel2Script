package com.novel2script.domain.repository;

import java.util.List;

public interface SceneCharacterRepository {
    void saveAll(Long sceneId, List<Long> characterIds);
    List<Long> findCharacterIdsBySceneId(Long sceneId);
    List<Long> findSceneIdsByCharacterId(Long characterId);
    void deleteBySceneId(Long sceneId);
}
