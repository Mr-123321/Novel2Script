package com.novel2script.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel2script.infrastructure.persistence.po.ScenePO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SceneMapper extends BaseMapper<ScenePO> {

    @Select("SELECT * FROM scenes WHERE script_id = #{scriptId} ORDER BY scene_number")
    List<ScenePO> findByScriptIdOrderBySceneNumber(@Param("scriptId") Long scriptId);

    @Delete("DELETE FROM scenes WHERE script_id = #{scriptId}")
    void deleteByScriptId(@Param("scriptId") Long scriptId);
}
