package com.novel2script.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel2script.infrastructure.persistence.po.ShotPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ShotMapper extends BaseMapper<ShotPO> {

    @Select("SELECT * FROM shots WHERE scene_id = #{sceneId} ORDER BY shot_number")
    List<ShotPO> findBySceneIdOrderByShotNumber(@Param("sceneId") Long sceneId);

    @Delete("DELETE FROM shots WHERE scene_id = #{sceneId}")
    void deleteBySceneId(@Param("sceneId") Long sceneId);
}
