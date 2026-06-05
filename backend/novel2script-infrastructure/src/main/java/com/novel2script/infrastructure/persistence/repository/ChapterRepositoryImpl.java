package com.novel2script.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.repository.ChapterRepository;
import com.novel2script.infrastructure.persistence.converter.ChapterConverter;
import com.novel2script.infrastructure.persistence.mapper.ChapterMapper;
import com.novel2script.infrastructure.persistence.po.ChapterPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChapterRepositoryImpl implements ChapterRepository {

    private final ChapterMapper chapterMapper;
    private final ChapterConverter chapterConverter;

    @Override
    public void saveAll(List<Chapter> chapters) {
        List<ChapterPO> poList = chapterConverter.toPoList(chapters);
        for (ChapterPO po : poList) {
            chapterMapper.insert(po);
        }
    }

    @Override
    public List<Chapter> findByNovelId(Long novelId) {
        return chapterConverter.toDomainList(chapterMapper.findByNovelId(novelId));
    }

    @Override
    public List<Chapter> findByNovelIdOrderByChapterNumber(Long novelId) {
        return chapterConverter.toDomainList(chapterMapper.findByNovelIdOrderByChapterNumber(novelId));
    }

    @Override
    public Optional<Chapter> findById(Long id) {
        return Optional.ofNullable(chapterConverter.toDomain(chapterMapper.selectById(id)));
    }

    @Override
    public void deleteByNovelId(Long novelId) {
        chapterMapper.deleteByNovelId(novelId);
    }

    @Override
    public int countByNovelId(Long novelId) {
        LambdaQueryWrapper<ChapterPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChapterPO::getNovelId, novelId);
        return chapterMapper.selectCount(wrapper).intValue();
    }
}
