package com.novel2script.domain.repository;

import com.novel2script.domain.model.Novel;
import java.util.List;
import java.util.Optional;

public interface NovelRepository {
    Novel save(Novel novel);
    Optional<Novel> findById(Long id);
    List<Novel> findAll();
    List<Novel> findByStatus(String status);
    void deleteById(Long id);
    boolean existsById(Long id);
}
