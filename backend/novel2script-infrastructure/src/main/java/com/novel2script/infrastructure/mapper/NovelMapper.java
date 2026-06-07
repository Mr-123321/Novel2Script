package com.novel2script.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel2script.domain.model.Novel;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper interface for Novel entity.
 */
@Mapper
public interface NovelMapper extends BaseMapper<Novel> {
}
