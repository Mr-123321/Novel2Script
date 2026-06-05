package com.novel2script.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel2script.infrastructure.persistence.po.SceneCharacterPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SceneCharacterMapper extends BaseMapper<SceneCharacterPO> {

    @Select("SELECT character_id FROM scene_characters WHERE scene_id = #{sceneId}")
    List<Long> findCharacterIdsBySceneId(@Param("sceneId") Long sceneId);

    @Select("SELECT scene_id FROM scene_characters WHERE character_id = #{characterId}")
    List<Long> findSceneIdsByCharacterId(@Param("characterId") Long characterId);

    @Delete("DELETE FROM scene_characters WHERE scene_id = #{sceneId}")
    void deleteBySceneId(@Param("sceneId") Long sceneId);
}
