package com.novel2script.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novel2script.domain.model.Scene;
import com.novel2script.domain.repository.SceneRepository;
import com.novel2script.infrastructure.persistence.converter.SceneConverter;
import com.novel2script.infrastructure.persistence.mapper.SceneMapper;
import com.novel2script.infrastructure.persistence.po.ScenePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SceneRepositoryImpl implements SceneRepository {

    private final SceneMapper sceneMapper;
    private final SceneConverter sceneConverter;

    @Override
    public void saveAll(List<Scene> scenes) {
        List<ScenePO> poList = sceneConverter.toPoList(scenes);
        for (ScenePO po : poList) {
            sceneMapper.insert(po);
        }
    }

    @Override
    public List<Scene> findByScriptIdOrderBySceneNumber(Long scriptId) {
        return sceneConverter.toDomainList(sceneMapper.findByScriptIdOrderBySceneNumber(scriptId));
    }

    @Override
    public Optional<Scene> findById(Long id) {
        return Optional.ofNullable(sceneConverter.toDomain(sceneMapper.selectById(id)));
    }

    @Override
    public void deleteByScriptId(Long scriptId) {
        sceneMapper.deleteByScriptId(scriptId);
    }

    @Override
    public int countByScriptId(Long scriptId) {
        LambdaQueryWrapper<ScenePO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScenePO::getScriptId, scriptId);
        return sceneMapper.selectCount(wrapper).intValue();
    }
}
