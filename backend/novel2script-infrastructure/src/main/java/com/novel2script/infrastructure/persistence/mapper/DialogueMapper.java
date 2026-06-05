package com.novel2script.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel2script.infrastructure.persistence.po.DialoguePO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DialogueMapper extends BaseMapper<DialoguePO> {

    @Select("SELECT * FROM dialogues WHERE scene_id = #{sceneId} ORDER BY sequence")
    List<DialoguePO> findBySceneIdOrderBySequence(@Param("sceneId") Long sceneId);

    @Delete("DELETE FROM dialogues WHERE scene_id = #{sceneId}")
    void deleteBySceneId(@Param("sceneId") Long sceneId);

    @Delete("DELETE d FROM dialogues d INNER JOIN scenes s ON d.scene_id = s.id WHERE s.script_id = #{scriptId}")
    void deleteByScriptId(@Param("scriptId") Long scriptId);
}
