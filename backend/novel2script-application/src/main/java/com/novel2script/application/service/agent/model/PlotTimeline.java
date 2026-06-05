package com.novel2script.application.service.agent.model;

import com.novel2script.domain.model.PlotEvent;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A chronological timeline of plot events extracted from a novel.
 *
 * <p>Provides structural analysis of the narrative arc including
 * the classic Freytag pyramid stages: exposition, rising action,
 * climax, falling action, and resolution.
 */
@Getter
@Builder
public class PlotTimeline {

    /** All events in chronological order. */
    private final List<PlotEvent> events;

    /** Events grouped by narrative stage. */
    private final Map<TimelineStage, List<PlotEvent>> stages;

    /** Peak emotional intensity event (climax candidate). */
    private final PlotEvent climaxEvent;

    /** Total number of critical events (importance >= 4). */
    private final int criticalEventCount;

    /** Most common conflict type across all events. */
    private final String dominantConflictType;

    /** Number of distinct chapters covered by these events. */
    private final int coveredChapterCount;

    /**
     * Narrative stages following Freytag's pyramid.
     */
    public enum TimelineStage {
        EXPOSITION,       // 开端 — setting the scene
        RISING_ACTION,    // 上升 — building tension
        CLIMAX,           // 高潮 — peak conflict
        FALLING_ACTION,   // 下降 — consequences unfold
        RESOLUTION        // 结局 — resolution
    }

    /**
     * Build a PlotTimeline from a list of events, automatically
     * classifying them into narrative stages.
     */
    public static PlotTimeline from(List<PlotEvent> events) {
        if (events == null || events.isEmpty()) {
            return PlotTimeline.builder()
                    .events(List.of())
                    .stages(Map.of())
                    .climaxEvent(null)
                    .criticalEventCount(0)
                    .dominantConflictType(null)
                    .coveredChapterCount(0)
                    .build();
        }

        // Sort by eventOrder
        List<PlotEvent> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparingInt(PlotEvent::getEventOrder));

        // Classify into stages based on emotional arc and position
        Map<TimelineStage, List<PlotEvent>> stages = classifyStages(sorted);

        // Find climax — highest importance with peak emotional intensity
        PlotEvent climax = findClimax(sorted);

        // Count critical events
        int criticalCount = (int) sorted.stream().filter(PlotEvent::isCritical).count();

        // Dominant conflict type
        String dominantConflict = sorted.stream()
                .filter(e -> e.getConflictType() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getConflictType().name(),
                        Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        // Count distinct chapters
        long chapterCount = sorted.stream()
                .flatMap(e -> e.getChapterIds() != null ? e.getChapterIds().stream() : java.util.stream.Stream.empty())
                .distinct()
                .count();

        return PlotTimeline.builder()
                .events(sorted)
                .stages(stages)
                .climaxEvent(climax)
                .criticalEventCount(criticalCount)
                .dominantConflictType(dominantConflict)
                .coveredChapterCount((int) chapterCount)
                .build();
    }

    private static Map<TimelineStage, List<PlotEvent>> classifyStages(List<PlotEvent> events) {
        int n = events.size();
        if (n == 0) return Map.of();

        // Use emotional arc + position to classify
        // Simple heuristic: first 20% → exposition, next 40% → rising,
        // middle 10% → climax, next 20% → falling, last 10% → resolution
        Map<TimelineStage, List<PlotEvent>> stages = new java.util.LinkedHashMap<>();
        for (TimelineStage stage : TimelineStage.values()) {
            stages.put(stage, new ArrayList<>());
        }

        for (int i = 0; i < n; i++) {
            double position = (double) i / n;
            PlotEvent event = events.get(i);

            TimelineStage stage;
            if (position < 0.20) {
                stage = TimelineStage.EXPOSITION;
            } else if (position < 0.60) {
                stage = TimelineStage.RISING_ACTION;
            } else if (position < 0.70) {
                stage = TimelineStage.CLIMAX;
            } else if (position < 0.85) {
                stage = TimelineStage.FALLING_ACTION;
            } else {
                stage = TimelineStage.RESOLUTION;
            }

            // Override based on emotional arc if explicit
            if (event.isRising() && stage.ordinal() < TimelineStage.CLIMAX.ordinal()) {
                stage = TimelineStage.RISING_ACTION;
            } else if (event.isFalling() && stage.ordinal() > TimelineStage.CLIMAX.ordinal()) {
                stage = TimelineStage.FALLING_ACTION;
            }

            stages.get(stage).add(event);
        }

        return stages;
    }

    private static PlotEvent findClimax(List<PlotEvent> events) {
        return events.stream()
                .max(Comparator.comparingInt(PlotEvent::getImportance)
                        .thenComparing(e -> e.isRising() ? 1 : 0))
                .orElse(null);
    }

    /**
     * Returns a human-readable summary of the timeline.
     */
    public String summarize() {
        if (events.isEmpty()) {
            return "空时间线 — 无剧情事件";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("剧情时间线摘要：\n");
        sb.append(String.format("  事件总数: %d\n", events.size()));
        sb.append(String.format("  关键事件: %d\n", criticalEventCount));
        sb.append(String.format("  覆盖章节: %d\n", coveredChapterCount));
        if (climaxEvent != null) {
            sb.append(String.format("  高潮事件: \"%s\" (重要度 %d)\n",
                    climaxEvent.getTitle(), climaxEvent.getImportance()));
        }
        if (dominantConflictType != null) {
            sb.append(String.format("  主要冲突: %s\n", dominantConflictType));
        }
        stages.forEach((stage, evts) -> {
            if (!evts.isEmpty()) {
                sb.append(String.format("  %s: %d 事件\n", stage.name(), evts.size()));
            }
        });
        return sb.toString();
    }
}
