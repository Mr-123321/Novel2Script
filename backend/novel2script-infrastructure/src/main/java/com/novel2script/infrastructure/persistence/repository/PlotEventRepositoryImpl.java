package com.novel2script.infrastructure.persistence.repository;

import com.novel2script.domain.model.PlotEvent;
import com.novel2script.domain.repository.PlotEventRepository;
import com.novel2script.infrastructure.persistence.converter.PlotEventConverter;
import com.novel2script.infrastructure.persistence.mapper.PlotEventMapper;
import com.novel2script.infrastructure.persistence.po.PlotEventPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PlotEventRepositoryImpl implements PlotEventRepository {

    private final PlotEventMapper plotEventMapper;
    private final PlotEventConverter plotEventConverter;

    @Override
    public void saveAll(List<PlotEvent> events) {
        List<PlotEventPO> poList = plotEventConverter.toPoList(events);
        for (PlotEventPO po : poList) {
            plotEventMapper.insert(po);
        }
    }

    @Override
    public List<PlotEvent> findByScriptIdOrderByEventOrder(Long scriptId) {
        return plotEventConverter.toDomainList(plotEventMapper.findByScriptIdOrderByEventOrder(scriptId));
    }

    @Override
    public Optional<PlotEvent> findById(Long id) {
        return Optional.ofNullable(plotEventConverter.toDomain(plotEventMapper.selectById(id)));
    }

    @Override
    public void deleteByScriptId(Long scriptId) {
        plotEventMapper.deleteByScriptId(scriptId);
    }
}
