package com.novel2script.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel2script.infrastructure.persistence.po.NovelPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NovelMapper extends BaseMapper<NovelPO> {

    @Select("SELECT * FROM novels WHERE status = #{status} ORDER BY created_at DESC")
    List<NovelPO> findByStatus(@Param("status") String status);
}
