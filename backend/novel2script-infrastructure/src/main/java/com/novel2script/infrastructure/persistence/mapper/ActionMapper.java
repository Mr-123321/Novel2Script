package com.novel2script.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel2script.infrastructure.persistence.po.ActionPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ActionMapper extends BaseMapper<ActionPO> {

    @Select("SELECT * FROM actions WHERE scene_id = #{sceneId} ORDER BY sequence")
    List<ActionPO> findBySceneIdOrderBySequence(@Param("sceneId") Long sceneId);

    @Delete("DELETE FROM actions WHERE scene_id = #{sceneId}")
    void deleteBySceneId(@Param("sceneId") Long sceneId);

    @Delete("DELETE a FROM actions a INNER JOIN scenes s ON a.scene_id = s.id WHERE s.script_id = #{scriptId}")
    void deleteByScriptId(@Param("scriptId") Long scriptId);
}
