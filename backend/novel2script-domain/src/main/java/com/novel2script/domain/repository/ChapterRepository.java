package com.novel2script.domain.repository;

import com.novel2script.domain.model.Chapter;
import java.util.List;
import java.util.Optional;

public interface ChapterRepository {
    void saveAll(List<Chapter> chapters);
    List<Chapter> findByNovelId(Long novelId);
    List<Chapter> findByNovelIdOrderByChapterNumber(Long novelId);
    Optional<Chapter> findById(Long id);
    void deleteByNovelId(Long novelId);
    int countByNovelId(Long novelId);
}
