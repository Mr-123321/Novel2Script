package com.novel2script.domain.repository;

import com.novel2script.domain.model.Character;
import java.util.List;
import java.util.Optional;

public interface CharacterRepository {
    void saveAll(List<Character> characters);
    List<Character> findByScriptId(Long scriptId);
    Optional<Character> findById(Long id);
    void deleteByScriptId(Long scriptId);
    int countByScriptId(Long scriptId);
}
