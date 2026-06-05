package com.novel2script.infrastructure.persistence.converter;

import com.novel2script.domain.model.Chapter;
import com.novel2script.infrastructure.persistence.po.ChapterPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ChapterConverter {

    public ChapterPO toPo(Chapter chapter) {
        if (chapter == null) {
            return null;
        }
        ChapterPO po = new ChapterPO();
        po.setId(chapter.getId());
        po.setNovelId(chapter.getNovelId());
        po.setChapterNumber(chapter.getChapterNumber());
        po.setTitle(chapter.getTitle());
        po.setContent(chapter.getContent());
        po.setCharCount(chapter.getCharCount());
        po.setStartOffset(chapter.getStartOffset());
        po.setEndOffset(chapter.getEndOffset());
        po.setEmbeddingId(chapter.getEmbeddingId());
        po.setStatus(chapter.getStatus());
        po.setCreatedAt(chapter.getCreatedAt());
        return po;
    }

    public Chapter toDomain(ChapterPO po) {
        if (po == null) {
            return null;
        }
        Chapter chapter = new Chapter();
        chapter.setId(po.getId());
        chapter.setNovelId(po.getNovelId());
        chapter.setChapterNumber(po.getChapterNumber() != null ? po.getChapterNumber() : 0);
        chapter.setTitle(po.getTitle());
        chapter.setContent(po.getContent());
        chapter.setCharCount(po.getCharCount() != null ? po.getCharCount() : 0);
        chapter.setStartOffset(po.getStartOffset() != null ? po.getStartOffset() : 0L);
        chapter.setEndOffset(po.getEndOffset() != null ? po.getEndOffset() : 0L);
        chapter.setEmbeddingId(po.getEmbeddingId());
        chapter.setStatus(po.getStatus());
        chapter.setCreatedAt(po.getCreatedAt());
        return chapter;
    }

    public List<ChapterPO> toPoList(List<Chapter> chapters) {
        if (chapters == null) {
            return Collections.emptyList();
        }
        return chapters.stream().map(this::toPo).collect(Collectors.toList());
    }

    public List<Chapter> toDomainList(List<ChapterPO> pos) {
        if (pos == null) {
            return Collections.emptyList();
        }
        return pos.stream().map(this::toDomain).collect(Collectors.toList());
    }
}
