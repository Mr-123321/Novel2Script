package com.novel2script.infrastructure.persistence.repository;

import com.novel2script.domain.model.Storyboard;
import com.novel2script.domain.repository.ShotRepository;
import com.novel2script.infrastructure.persistence.converter.StoryboardConverter;
import com.novel2script.infrastructure.persistence.mapper.ShotMapper;
import com.novel2script.infrastructure.persistence.po.ShotPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ShotRepositoryImpl implements ShotRepository {

    private final ShotMapper shotMapper;
    private final StoryboardConverter storyboardConverter;

    @Override
    public void saveAll(List<Storyboard> shots) {
        List<ShotPO> poList = storyboardConverter.toPoList(shots);
        for (ShotPO po : poList) {
            shotMapper.insert(po);
        }
    }

    @Override
    public List<Storyboard> findBySceneIdOrderByShotNumber(Long sceneId) {
        return storyboardConverter.toDomainList(shotMapper.findBySceneIdOrderByShotNumber(sceneId));
    }

    @Override
    public void deleteBySceneId(Long sceneId) {
        shotMapper.deleteBySceneId(sceneId);
    }
}
