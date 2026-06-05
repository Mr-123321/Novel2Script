package com.novel2script.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novel2script.domain.model.Script;
import com.novel2script.domain.repository.ScriptRepository;
import com.novel2script.infrastructure.persistence.converter.ScriptConverter;
import com.novel2script.infrastructure.persistence.mapper.ScriptMapper;
import com.novel2script.infrastructure.persistence.po.ScriptPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ScriptRepositoryImpl implements ScriptRepository {

    private final ScriptMapper scriptMapper;
    private final ScriptConverter scriptConverter;

    @Override
    public Script save(Script script) {
        if (script.getId() == null) {
            scriptMapper.insert(scriptConverter.toPo(script));
        } else {
            scriptMapper.updateById(scriptConverter.toPo(script));
        }
        return script;
    }

    @Override
    public Optional<Script> findById(Long id) {
        return Optional.ofNullable(scriptConverter.toDomain(scriptMapper.selectById(id)));
    }

    @Override
    public Optional<Script> findByNovelId(Long novelId) {
        return Optional.ofNullable(scriptConverter.toDomain(scriptMapper.findByNovelId(novelId)));
    }

    @Override
    public List<Script> findAll() {
        return scriptConverter.toDomainList(scriptMapper.selectList(null));
    }

    @Override
    public List<Script> findByStatus(String status) {
        return scriptConverter.toDomainList(scriptMapper.findByStatus(status));
    }

    @Override
    public void deleteById(Long id) {
        scriptMapper.deleteById(id);
    }

    @Override
    public void updateProgress(Long id, double progress) {
        scriptMapper.updateProgress(id, progress);
    }

    @Override
    public void updateStatus(Long id, String status) {
        scriptMapper.updateStatus(id, status);
    }

    @Override
    public boolean existsByNovelId(Long novelId) {
        LambdaQueryWrapper<ScriptPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScriptPO::getNovelId, novelId);
        return scriptMapper.selectCount(wrapper) > 0;
    }
}
