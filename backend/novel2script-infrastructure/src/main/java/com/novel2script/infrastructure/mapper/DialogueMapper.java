package com.novel2script.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel2script.domain.model.Dialogue;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DialogueMapper extends BaseMapper<Dialogue> {
}
