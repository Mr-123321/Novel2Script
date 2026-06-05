package com.novel2script.domain.repository;

import com.novel2script.domain.model.Storyboard;
import java.util.List;

public interface ShotRepository {
    void saveAll(List<Storyboard> shots);
    List<Storyboard> findBySceneIdOrderByShotNumber(Long sceneId);
    void deleteBySceneId(Long sceneId);
}
