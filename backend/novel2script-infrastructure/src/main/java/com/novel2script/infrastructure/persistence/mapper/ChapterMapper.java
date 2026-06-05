package com.novel2script.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel2script.infrastructure.persistence.po.ChapterPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChapterMapper extends BaseMapper<ChapterPO> {

    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId} ORDER BY chapter_number")
    List<ChapterPO> findByNovelIdOrderByChapterNumber(@Param("novelId") Long novelId);

    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId}")
    List<ChapterPO> findByNovelId(@Param("novelId") Long novelId);

    @Delete("DELETE FROM chapters WHERE novel_id = #{novelId}")
    void deleteByNovelId(@Param("novelId") Long novelId);
}
