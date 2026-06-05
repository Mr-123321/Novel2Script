package com.novel2script.domain.repository;

import com.novel2script.domain.model.PlotEvent;
import java.util.List;
import java.util.Optional;

public interface PlotEventRepository {
    void saveAll(List<PlotEvent> events);
    List<PlotEvent> findByScriptIdOrderByEventOrder(Long scriptId);
    Optional<PlotEvent> findById(Long id);
    void deleteByScriptId(Long scriptId);
}
