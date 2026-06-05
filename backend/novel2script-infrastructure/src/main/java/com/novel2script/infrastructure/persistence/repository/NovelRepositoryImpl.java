package com.novel2script.infrastructure.persistence.repository;

import com.novel2script.domain.model.Novel;
import com.novel2script.domain.repository.NovelRepository;
import com.novel2script.infrastructure.persistence.converter.NovelConverter;
import com.novel2script.infrastructure.persistence.mapper.NovelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NovelRepositoryImpl implements NovelRepository {

    private final NovelMapper novelMapper;
    private final NovelConverter novelConverter;

    @Override
    public Novel save(Novel novel) {
        if (novel.getId() == null) {
            novelMapper.insert(novelConverter.toPo(novel));
        } else {
            novelMapper.updateById(novelConverter.toPo(novel));
        }
        return novel;
    }

    @Override
    public Optional<Novel> findById(Long id) {
        return Optional.ofNullable(novelConverter.toDomain(novelMapper.selectById(id)));
    }

    @Override
    public List<Novel> findAll() {
        return novelConverter.toDomainList(novelMapper.selectList(null));
    }

    @Override
    public List<Novel> findByStatus(String status) {
        return novelConverter.toDomainList(novelMapper.findByStatus(status));
    }

    @Override
    public void deleteById(Long id) {
        novelMapper.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return novelMapper.selectById(id) != null;
    }
}
