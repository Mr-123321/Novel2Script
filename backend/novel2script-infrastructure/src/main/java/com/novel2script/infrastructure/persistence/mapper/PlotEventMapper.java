package com.novel2script.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel2script.infrastructure.persistence.po.PlotEventPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PlotEventMapper extends BaseMapper<PlotEventPO> {

    @Select("SELECT * FROM plot_events WHERE script_id = #{scriptId} ORDER BY event_order")
    List<PlotEventPO> findByScriptIdOrderByEventOrder(@Param("scriptId") Long scriptId);

    @Delete("DELETE FROM plot_events WHERE script_id = #{scriptId}")
    void deleteByScriptId(@Param("scriptId") Long scriptId);
}
