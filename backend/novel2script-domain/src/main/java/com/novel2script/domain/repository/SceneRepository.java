package com.novel2script.domain.repository;

import com.novel2script.domain.model.Scene;
import java.util.List;
import java.util.Optional;

public interface SceneRepository {
    void saveAll(List<Scene> scenes);
    List<Scene> findByScriptIdOrderBySceneNumber(Long scriptId);
    Optional<Scene> findById(Long id);
    void deleteByScriptId(Long scriptId);
    int countByScriptId(Long scriptId);
}
