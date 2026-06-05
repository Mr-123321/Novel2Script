package com.novel2script.infrastructure.persistence.repository;

import com.novel2script.domain.model.Action;
import com.novel2script.domain.repository.ActionRepository;
import com.novel2script.infrastructure.persistence.converter.ActionConverter;
import com.novel2script.infrastructure.persistence.mapper.ActionMapper;
import com.novel2script.infrastructure.persistence.po.ActionPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ActionRepositoryImpl implements ActionRepository {

    private final ActionMapper actionMapper;
    private final ActionConverter actionConverter;

    @Override
    public void saveAll(List<Action> actions) {
        List<ActionPO> poList = actionConverter.toPoList(actions);
        for (ActionPO po : poList) {
            actionMapper.insert(po);
        }
    }

    @Override
    public List<Action> findBySceneIdOrderBySequence(Long sceneId) {
        return actionConverter.toDomainList(actionMapper.findBySceneIdOrderBySequence(sceneId));
    }

    @Override
    public void deleteBySceneId(Long sceneId) {
        actionMapper.deleteBySceneId(sceneId);
    }

    @Override
    public void deleteByScriptId(Long scriptId) {
        actionMapper.deleteByScriptId(scriptId);
    }
}
