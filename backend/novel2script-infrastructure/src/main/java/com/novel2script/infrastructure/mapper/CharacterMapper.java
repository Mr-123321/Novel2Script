package com.novel2script.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel2script.domain.model.Character;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper interface for Character entity.
 * Uses MyBatis-Plus BaseMapper — all queries go through LambdaQueryWrapper
 * to ensure autoResultMap (with JSON type handlers) is applied.
 */
@Mapper
public interface CharacterMapper extends BaseMapper<Character> {
}
