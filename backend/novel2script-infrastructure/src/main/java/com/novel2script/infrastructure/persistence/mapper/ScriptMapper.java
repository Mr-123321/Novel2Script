package com.novel2script.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel2script.infrastructure.persistence.po.ScriptPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ScriptMapper extends BaseMapper<ScriptPO> {

    @Select("SELECT * FROM scripts WHERE novel_id = #{novelId}")
    ScriptPO findByNovelId(@Param("novelId") Long novelId);

    @Select("SELECT * FROM scripts WHERE status = #{status} ORDER BY created_at DESC")
    List<ScriptPO> findByStatus(@Param("status") String status);

    @Update("UPDATE scripts SET progress = #{progress} WHERE id = #{id}")
    void updateProgress(@Param("id") Long id, @Param("progress") double progress);

    @Update("UPDATE scripts SET status = #{status} WHERE id = #{id}")
    void updateStatus(@Param("id") Long id, @Param("status") String status);
}
