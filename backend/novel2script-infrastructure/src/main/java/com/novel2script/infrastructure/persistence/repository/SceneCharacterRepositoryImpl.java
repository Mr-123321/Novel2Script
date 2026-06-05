package com.novel2script.infrastructure.persistence.repository;

import com.novel2script.domain.repository.SceneCharacterRepository;
import com.novel2script.infrastructure.persistence.mapper.SceneCharacterMapper;
import com.novel2script.infrastructure.persistence.po.SceneCharacterPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SceneCharacterRepositoryImpl implements SceneCharacterRepository {

    private final SceneCharacterMapper sceneCharacterMapper;

    @Override
    public void saveAll(Long sceneId, List<Long> characterIds) {
        for (Long characterId : characterIds) {
            SceneCharacterPO po = new SceneCharacterPO();
            po.setSceneId(sceneId);
            po.setCharacterId(characterId);
            sceneCharacterMapper.insert(po);
        }
    }

    @Override
    public List<Long> findCharacterIdsBySceneId(Long sceneId) {
        return sceneCharacterMapper.findCharacterIdsBySceneId(sceneId);
    }

    @Override
    public List<Long> findSceneIdsByCharacterId(Long characterId) {
        return sceneCharacterMapper.findSceneIdsByCharacterId(characterId);
    }

    @Override
    public void deleteBySceneId(Long sceneId) {
        sceneCharacterMapper.deleteBySceneId(sceneId);
    }
}
