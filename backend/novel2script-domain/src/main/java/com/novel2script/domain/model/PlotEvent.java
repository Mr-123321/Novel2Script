package com.novel2script.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.novel2script.domain.handler.LongListTypeHandler;
import com.novel2script.common.enums.ConflictType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A significant plot event extracted from the novel.
 *
 * <p>Plot events represent dramatic turning points, conflicts,
 * and key narrative moments that drive the story forward.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "plot_events", autoResultMap = true)
public class PlotEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("script_id")
    private Long scriptId;

    @TableField("event_order")
    private int eventOrder;

    private String title;
    private String description;
    private String location;

    @TableField("time_point")
    private String timePoint;

    @TableField("conflict_type")
    private ConflictType conflictType;

    @TableField(value = "chapter_ids", typeHandler = LongListTypeHandler.class)
    @Builder.Default
    private List<Long> chapterIds = new ArrayList<>();

    @TableField(value = "character_ids", typeHandler = LongListTypeHandler.class)
    @Builder.Default
    private List<Long> characterIds = new ArrayList<>();

    /** 1-5 */
    private int importance;

    /** ↑(上升) / ↓(下降) / →(平) / ↗(缓升) / ↘(缓降) */
    @TableField("emotional_arc")
    private String emotionalArc;

    @TableField("created_at")
    private LocalDateTime createdAt;

    // ── Domain logic ────────────────────────────────────

    public boolean isCritical() {
        return importance >= 4;
    }

    public boolean isMinor() {
        return importance <= 2;
    }

    /**
     * Returns true if this event spans multiple chapters.
     */
    public boolean isCrossChapter() {
        return chapterIds != null && chapterIds.size() > 1;
    }

    /**
     * Returns true if the emotional arc is trending upward (rising action).
     */
    public boolean isRising() {
        return emotionalArc != null && (emotionalArc.contains("↑") || emotionalArc.contains("↗"));
    }

    /**
     * Returns true if the emotional arc is trending downward (falling action).
     */
    public boolean isFalling() {
        return emotionalArc != null && (emotionalArc.contains("↓") || emotionalArc.contains("↘"));
    }

    /**
     * Merge another event into this one (e.g. same event spanning multiple chapters).
     */
    public PlotEvent merge(PlotEvent other) {
        if (other == null) return this;

        List<Long> mergedChapterIds = new ArrayList<>(this.chapterIds != null ? this.chapterIds : new ArrayList<>());
        if (other.chapterIds != null) {
            for (Long cid : other.chapterIds) {
                if (!mergedChapterIds.contains(cid)) {
                    mergedChapterIds.add(cid);
                }
            }
        }
        mergedChapterIds.sort(Long::compareTo);

        List<Long> mergedCharacterIds = new ArrayList<>(this.characterIds != null ? this.characterIds : new ArrayList<>());
        if (other.characterIds != null) {
            for (Long cid : other.characterIds) {
                if (!mergedCharacterIds.contains(cid)) {
                    mergedCharacterIds.add(cid);
                }
            }
        }

        // Take the higher importance
        int mergedImportance = Math.max(this.importance, other.importance);

        // Combine descriptions if complementary
        String mergedDesc = this.description;
        if (other.description != null && !other.description.equals(this.description)) {
            mergedDesc = this.description + " " + other.description;
        }

        return PlotEvent.builder()
                .id(this.id)
                .scriptId(this.scriptId)
                .eventOrder(this.eventOrder)
                .title(this.title)
                .description(mergedDesc)
                .location(this.location != null ? this.location : other.location)
                .timePoint(this.timePoint != null ? this.timePoint : other.timePoint)
                .conflictType(this.conflictType != null ? this.conflictType : other.conflictType)
                .chapterIds(mergedChapterIds)
                .characterIds(mergedCharacterIds)
                .importance(mergedImportance)
                .emotionalArc(this.emotionalArc != null ? this.emotionalArc : other.emotionalArc)
                .createdAt(this.createdAt)
                .build();
    }
}
