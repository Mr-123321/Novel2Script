package com.novel2script.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novel2script.domain.model.Character;
import com.novel2script.domain.repository.CharacterRepository;
import com.novel2script.infrastructure.persistence.converter.CharacterConverter;
import com.novel2script.infrastructure.persistence.mapper.CharacterMapper;
import com.novel2script.infrastructure.persistence.po.CharacterPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CharacterRepositoryImpl implements CharacterRepository {

    private final CharacterMapper characterMapper;
    private final CharacterConverter characterConverter;

    @Override
    public void saveAll(List<Character> characters) {
        List<CharacterPO> poList = characterConverter.toPoList(characters);
        for (CharacterPO po : poList) {
            characterMapper.insert(po);
        }
    }

    @Override
    public List<Character> findByScriptId(Long scriptId) {
        return characterConverter.toDomainList(characterMapper.findByScriptId(scriptId));
    }

    @Override
    public Optional<Character> findById(Long id) {
        return Optional.ofNullable(characterConverter.toDomain(characterMapper.selectById(id)));
    }

    @Override
    public void deleteByScriptId(Long scriptId) {
        characterMapper.deleteByScriptId(scriptId);
    }

    @Override
    public int countByScriptId(Long scriptId) {
        LambdaQueryWrapper<CharacterPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CharacterPO::getScriptId, scriptId);
        return characterMapper.selectCount(wrapper).intValue();
    }
}
