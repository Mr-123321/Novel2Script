package com.novel2script.domain.repository;

import com.novel2script.domain.model.Script;
import java.util.List;
import java.util.Optional;

public interface ScriptRepository {
    Script save(Script script);
    Optional<Script> findById(Long id);
    Optional<Script> findByNovelId(Long novelId);
    List<Script> findAll();
    List<Script> findByStatus(String status);
    void deleteById(Long id);
    void updateProgress(Long id, double progress);
    void updateStatus(Long id, String status);
    boolean existsByNovelId(Long novelId);
}
