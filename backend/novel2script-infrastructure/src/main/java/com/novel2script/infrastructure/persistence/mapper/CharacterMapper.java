package com.novel2script.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel2script.infrastructure.persistence.po.CharacterPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CharacterMapper extends BaseMapper<CharacterPO> {

    @Select("SELECT * FROM characters WHERE script_id = #{scriptId}")
    List<CharacterPO> findByScriptId(@Param("scriptId") Long scriptId);

    @Delete("DELETE FROM characters WHERE script_id = #{scriptId}")
    void deleteByScriptId(@Param("scriptId") Long scriptId);
}
