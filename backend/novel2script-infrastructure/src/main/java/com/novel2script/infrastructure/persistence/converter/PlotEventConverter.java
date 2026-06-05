package com.novel2script.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.domain.model.PlotEvent;
import com.novel2script.infrastructure.persistence.po.PlotEventPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PlotEventConverter {

    private final ObjectMapper objectMapper;

    public PlotEventPO toPo(PlotEvent plotEvent) {
        if (plotEvent == null) {
            return null;
        }
        PlotEventPO po = new PlotEventPO();
        po.setId(plotEvent.getId());
        po.setScriptId(plotEvent.getScriptId());
        po.setEventOrder(plotEvent.getEventOrder());
        po.setTitle(plotEvent.getTitle());
        po.setDescription(plotEvent.getDescription());
        po.setLocation(plotEvent.getLocation());
        po.setTimePoint(plotEvent.getTimePoint());
        po.setConflictType(plotEvent.getConflictType());
        po.setChapterIds(toJsonLongList(plotEvent.getChapterIds()));
        po.setCharacterIds(toJsonLongList(plotEvent.getCharacterIds()));
        po.setImportance(plotEvent.getImportance());
        po.setCreatedAt(plotEvent.getCreatedAt());
        return po;
    }

    public PlotEvent toDomain(PlotEventPO po) {
        if (po == null) {
            return null;
        }
        PlotEvent plotEvent = new PlotEvent();
        plotEvent.setId(po.getId());
        plotEvent.setScriptId(po.getScriptId());
        plotEvent.setEventOrder(po.getEventOrder() != null ? po.getEventOrder() : 0);
        plotEvent.setTitle(po.getTitle());
        plotEvent.setDescription(po.getDescription());
        plotEvent.setLocation(po.getLocation());
        plotEvent.setTimePoint(po.getTimePoint());
        plotEvent.setConflictType(po.getConflictType());
        plotEvent.setChapterIds(fromJsonToLongList(po.getChapterIds()));
        plotEvent.setCharacterIds(fromJsonToLongList(po.getCharacterIds()));
        plotEvent.setImportance(po.getImportance() != null ? po.getImportance() : 0);
        plotEvent.setCreatedAt(po.getCreatedAt());
        return plotEvent;
    }

    public List<PlotEventPO> toPoList(List<PlotEvent> plotEvents) {
        if (plotEvents == null) {
            return Collections.emptyList();
        }
        return plotEvents.stream().map(this::toPo).collect(Collectors.toList());
    }

    public List<PlotEvent> toDomainList(List<PlotEventPO> pos) {
        if (pos == null) {
            return Collections.emptyList();
        }
        return pos.stream().map(this::toDomain).collect(Collectors.toList());
    }

    private String toJsonLongList(List<Long> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize list to JSON", e);
        }
    }

    private List<Long> fromJsonToLongList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize list from JSON", e);
        }
    }
}
