package com.novel2script.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.common.enums.NovelStatus;
import com.novel2script.domain.model.Novel;
import com.novel2script.infrastructure.persistence.po.NovelPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class NovelConverter {

    private final ObjectMapper objectMapper;

    public NovelPO toPo(Novel novel) {
        if (novel == null) {
            return null;
        }
        NovelPO po = new NovelPO();
        po.setId(novel.getId());
        po.setTitle(novel.getTitle());
        po.setAuthor(novel.getAuthor());
        po.setFileName(novel.getFileName());
        po.setFileSize(novel.getFileSize());
        po.setTotalChars(novel.getTotalChars());
        po.setChapterCount(novel.getChapterCount());
        po.setStatus(novel.getStatus() != null ? novel.getStatus().name() : null);
        po.setMetadata(toJsonMap(novel.getMetadata()));
        po.setCreatedAt(novel.getCreatedAt());
        po.setUpdatedAt(novel.getUpdatedAt());
        return po;
    }

    public Novel toDomain(NovelPO po) {
        if (po == null) {
            return null;
        }
        Novel novel = new Novel();
        novel.setId(po.getId());
        novel.setTitle(po.getTitle());
        novel.setAuthor(po.getAuthor());
        novel.setFileName(po.getFileName());
        novel.setFileSize(po.getFileSize() != null ? po.getFileSize() : 0L);
        novel.setTotalChars(po.getTotalChars() != null ? po.getTotalChars() : 0);
        novel.setChapterCount(po.getChapterCount() != null ? po.getChapterCount() : 0);
        novel.setStatus(po.getStatus() != null ? NovelStatus.valueOf(po.getStatus()) : null);
        novel.setMetadata(fromJsonToMap(po.getMetadata()));
        novel.setCreatedAt(po.getCreatedAt());
        novel.setUpdatedAt(po.getUpdatedAt());
        return novel;
    }

    public List<NovelPO> toPoList(List<Novel> novels) {
        if (novels == null) {
            return Collections.emptyList();
        }
        return novels.stream().map(this::toPo).collect(Collectors.toList());
    }

    public List<Novel> toDomainList(List<NovelPO> pos) {
        if (pos == null) {
            return Collections.emptyList();
        }
        return pos.stream().map(this::toDomain).collect(Collectors.toList());
    }

    private String toJsonMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize metadata to JSON", e);
        }
    }

    private Map<String, Object> fromJsonToMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize metadata from JSON", e);
        }
    }
}
