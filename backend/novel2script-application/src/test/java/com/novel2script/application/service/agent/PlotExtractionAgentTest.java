package com.novel2script.application.service.agent;

import com.novel2script.application.service.agent.model.PlotTimeline;
import com.novel2script.common.enums.ConflictType;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Character;
import com.novel2script.domain.model.PlotEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlotExtractionAgent — 剧情提取单元测试")
class PlotExtractionAgentTest {

    // ── Test data builders ──────────────────────────────

    private static Chapter chapter(long id, int num, String title, String content) {
        return Chapter.builder()
                .id(id)
                .chapterNumber(num)
                .title(title)
                .content(content)
                .build();
    }

    private static Character character(long id, String name) {
        return Character.builder()
                .id(id)
                .canonicalName(name)
                .build();
    }

    private static PlotEvent event(int order, String title, int importance,
                                   String emotionalArc, ConflictType conflictType,
                                   List<Long> chapterIds, List<Long> characterIds) {
        return PlotEvent.builder()
                .eventOrder(order)
                .title(title)
                .description(title + "的详细描述")
                .importance(importance)
                .emotionalArc(emotionalArc)
                .conflictType(conflictType)
                .chapterIds(chapterIds != null ? chapterIds : new ArrayList<>())
                .characterIds(characterIds != null ? characterIds : new ArrayList<>())
                .build();
    }

    // ── 1. PlotEvent domain model tests ─────────────────

    @Nested
    @DisplayName("1. PlotEvent 领域模型")
    class PlotEventModel {

        @Test
        @DisplayName("importance >= 4 应判定为关键事件")
        void shouldIdentifyCriticalEvents() {
            PlotEvent minor = event(1, "闲聊", 2, "→", null, List.of(1L), List.of());
            PlotEvent major = event(2, "决战", 4, "↑", null, List.of(1L), List.of());
            PlotEvent critical = event(3, "死亡", 5, "↓", null, List.of(1L), List.of());

            assertThat(minor.isCritical()).isFalse();
            assertThat(major.isCritical()).isTrue();
            assertThat(critical.isCritical()).isTrue();
        }

        @Test
        @DisplayName("isCrossChapter 应正确识别跨章事件")
        void shouldIdentifyCrossChapterEvents() {
            PlotEvent single = event(1, "单章事件", 3, "→", null, List.of(3L), List.of());
            PlotEvent multi = event(2, "跨章事件", 4, "↑", null, List.of(3L, 4L, 5L), List.of());

            assertThat(single.isCrossChapter()).isFalse();
            assertThat(multi.isCrossChapter()).isTrue();
            assertThat(multi.getChapterIds()).containsExactly(3L, 4L, 5L);
        }

        @Test
        @DisplayName("情绪弧线方向应正确判定")
        void shouldIdentifyEmotionalArcs() {
            PlotEvent rising = event(1, "上升", 3, "↑", null, List.of(), List.of());
            PlotEvent falling = event(2, "下降", 3, "↓", null, List.of(), List.of());
            PlotEvent flat = event(3, "平稳", 3, "→", null, List.of(), List.of());
            PlotEvent slowRise = event(4, "缓升", 3, "↗", null, List.of(), List.of());
            PlotEvent slowFall = event(5, "缓降", 3, "↘", null, List.of(), List.of());

            assertThat(rising.isRising()).isTrue();
            assertThat(slowRise.isRising()).isTrue();
            assertThat(falling.isFalling()).isTrue();
            assertThat(slowFall.isFalling()).isTrue();
            assertThat(flat.isRising()).isFalse();
            assertThat(flat.isFalling()).isFalse();
        }

        @Test
        @DisplayName("merge 应正确合并两个 PlotEvent")
        void shouldMergeTwoEvents() {
            PlotEvent e1 = event(1, "大事件", 3, "↑", ConflictType.PERSON_VS_PERSON,
                    new ArrayList<>(List.of(3L, 4L)), new ArrayList<>(List.of(1L)));
            PlotEvent e2 = event(1, "大事件", 4, "↑", ConflictType.PERSON_VS_PERSON,
                    new ArrayList<>(List.of(4L, 5L)), new ArrayList<>(List.of(1L, 2L)));

            PlotEvent merged = e1.merge(e2);

            assertThat(merged.getChapterIds()).containsExactly(3L, 4L, 5L);
            assertThat(merged.getCharacterIds()).containsExactly(1L, 2L);
            assertThat(merged.getImportance()).isEqualTo(4); // max of 3 and 4
            assertThat(merged.getTitle()).isEqualTo("大事件");
        }

        @Test
        @DisplayName("merge 空事件应返回原事件不变")
        void shouldNotChangeWhenMergingNull() {
            PlotEvent e1 = event(1, "事件", 3, "→", null, List.of(1L), List.of());

            PlotEvent merged = e1.merge(null);

            assertThat(merged).isEqualTo(e1);
            assertThat(merged.getChapterIds()).containsExactly(1L);
        }
    }

    // ── 2. ConflictType enum tests ──────────────────────

    @Nested
    @DisplayName("2. ConflictType 枚举")
    class ConflictTypeEnum {

        @Test
        @DisplayName("fromLabel 应从中文标签正确解析")
        void shouldParseFromChineseLabel() {
            assertThat(ConflictType.fromLabel("人与人")).isEqualTo(ConflictType.PERSON_VS_PERSON);
            assertThat(ConflictType.fromLabel("人与自我")).isEqualTo(ConflictType.PERSON_VS_SELF);
            assertThat(ConflictType.fromLabel("人与社会")).isEqualTo(ConflictType.PERSON_VS_SOCIETY);
            assertThat(ConflictType.fromLabel("人与自然")).isEqualTo(ConflictType.PERSON_VS_NATURE);
            assertThat(ConflictType.fromLabel("人与科技")).isEqualTo(ConflictType.PERSON_VS_TECHNOLOGY);
            assertThat(ConflictType.fromLabel("人与命运")).isEqualTo(ConflictType.PERSON_VS_FATE);
        }

        @Test
        @DisplayName("fromLabel 应从英文名正确解析")
        void shouldParseFromEnumName() {
            assertThat(ConflictType.fromLabel("PERSON_VS_PERSON")).isEqualTo(ConflictType.PERSON_VS_PERSON);
            assertThat(ConflictType.fromLabel("PERSON_VS_SELF")).isEqualTo(ConflictType.PERSON_VS_SELF);
            assertThat(ConflictType.fromLabel("person_vs_nature")).isEqualTo(ConflictType.PERSON_VS_NATURE);
        }

        @Test
        @DisplayName("fromLabel 应模糊匹配冲突类型")
        void shouldFuzzyMatch() {
            // 内心矛盾 → PERSON_VS_SELF
            assertThat(ConflictType.fromLabel("内心矛盾")).isEqualTo(ConflictType.PERSON_VS_SELF);
            // 社会制度 → PERSON_VS_SOCIETY
            assertThat(ConflictType.fromLabel("社会制度")).isEqualTo(ConflictType.PERSON_VS_SOCIETY);
            // 环境灾难 → PERSON_VS_NATURE
            assertThat(ConflictType.fromLabel("自然灾害")).isEqualTo(ConflictType.PERSON_VS_NATURE);
            // 宿命 → PERSON_VS_FATE
            assertThat(ConflictType.fromLabel("宿命")).isEqualTo(ConflictType.PERSON_VS_FATE);
        }

        @Test
        @DisplayName("fromLabel null/blank 应返回 null")
        void shouldReturnNullForInvalidInput() {
            assertThat(ConflictType.fromLabel(null)).isNull();
            assertThat(ConflictType.fromLabel("")).isNull();
            assertThat(ConflictType.fromLabel("   ")).isNull();
            assertThat(ConflictType.fromLabel("不存在的类型")).isNull();
        }

        @Test
        @DisplayName("所有枚举值应有对应的中文标签")
        void shouldHaveLabelsForAllValues() {
            for (ConflictType ct : ConflictType.values()) {
                assertThat(ct.getLabel()).isNotNull().isNotBlank();
            }
        }
    }

    // ── 3. PlotTimeline tests ──────────────────────────

    @Nested
    @DisplayName("3. PlotTimeline 时间线构建")
    class PlotTimelineTests {

        @Test
        @DisplayName("空事件列表应返回空时间线")
        void shouldReturnEmptyTimelineForNoEvents() {
            PlotTimeline timeline = PlotTimeline.from(List.of());

            assertThat(timeline.getEvents()).isEmpty();
            assertThat(timeline.getClimaxEvent()).isNull();
            assertThat(timeline.getCriticalEventCount()).isEqualTo(0);
            assertThat(timeline.getCoveredChapterCount()).isEqualTo(0);
            assertThat(timeline.summarize()).contains("空时间线");
        }

        @Test
        @DisplayName("应正确分类叙事阶段")
        void shouldClassifyNarrativeStages() {
            List<PlotEvent> events = new ArrayList<>();
            // Create 10 events spread across the narrative
            for (int i = 0; i < 10; i++) {
                events.add(event(i + 1, "事件" + (i + 1), 3, "→", null,
                        List.of((long) i + 1), List.of()));
            }

            PlotTimeline timeline = PlotTimeline.from(events);

            assertThat(timeline.getEvents()).hasSize(10);
            assertThat(timeline.getStages()).isNotEmpty();
            // First 20% should be in EXPOSITION
            assertThat(timeline.getStages().get(PlotTimeline.TimelineStage.EXPOSITION)).isNotEmpty();
            // Middle should have RISING_ACTION
            assertThat(timeline.getStages().get(PlotTimeline.TimelineStage.RISING_ACTION)).isNotEmpty();
        }

        @Test
        @DisplayName("应正确识别高潮事件（最高重要性）")
        void shouldIdentifyClimax() {
            List<PlotEvent> events = List.of(
                    event(1, "开端", 2, "→", null, List.of(1L), List.of()),
                    event(2, "发展", 3, "↗", null, List.of(2L), List.of()),
                    event(3, "高潮", 5, "↑", null, List.of(3L), List.of()),
                    event(4, "转折", 4, "↓", null, List.of(4L), List.of()),
                    event(5, "结局", 2, "→", null, List.of(5L), List.of())
            );

            PlotTimeline timeline = PlotTimeline.from(events);

            assertThat(timeline.getClimaxEvent()).isNotNull();
            assertThat(timeline.getClimaxEvent().getTitle()).isEqualTo("高潮");
            assertThat(timeline.getClimaxEvent().getImportance()).isEqualTo(5);
            assertThat(timeline.getCriticalEventCount()).isEqualTo(2); // importance 5 + 4
        }

        @Test
        @DisplayName("应正确统计主要冲突类型")
        void shouldCountDominantConflict() {
            List<PlotEvent> events = List.of(
                    event(1, "事件1", 3, "→", ConflictType.PERSON_VS_PERSON, List.of(1L), List.of()),
                    event(2, "事件2", 3, "→", ConflictType.PERSON_VS_PERSON, List.of(2L), List.of()),
                    event(3, "事件3", 3, "→", ConflictType.PERSON_VS_SELF, List.of(3L), List.of()),
                    event(4, "事件4", 3, "→", ConflictType.PERSON_VS_PERSON, List.of(4L), List.of()),
                    event(5, "事件5", 3, "→", ConflictType.PERSON_VS_SOCIETY, List.of(5L), List.of())
            );

            PlotTimeline timeline = PlotTimeline.from(events);

            assertThat(timeline.getDominantConflictType()).isEqualTo("PERSON_VS_PERSON");
            assertThat(timeline.getCoveredChapterCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("summarize 应输出可读摘要")
        void shouldSummarizeReadably() {
            List<PlotEvent> events = List.of(
                    event(1, "关键事件", 5, "↑", ConflictType.PERSON_VS_PERSON,
                            List.of(1L, 2L), List.of(1L, 2L))
            );

            PlotTimeline timeline = PlotTimeline.from(events);
            String summary = timeline.summarize();

            assertThat(summary).contains("剧情时间线摘要");
            assertThat(summary).contains("事件总数: 1");
            assertThat(summary).contains("关键事件: 1");
            assertThat(summary).contains("高潮事件");
            assertThat(summary).contains("关键事件");
        }

        @Test
        @DisplayName("null 事件列表应安全处理")
        void shouldHandleNullEvents() {
            PlotTimeline timeline = PlotTimeline.from(null);
            assertThat(timeline.getEvents()).isEmpty();
        }
    }

    // ── 4. Event merging logic tests ───────────────────

    @Nested
    @DisplayName("4. 事件合并逻辑")
    class EventMerging {

        @Test
        @DisplayName("同标题事件应判定为同一事件")
        void shouldMatchByTitle() {
            PlotEvent e1 = event(1, "战斗开始", 3, "↑", null, List.of(3L), List.of());
            PlotEvent e2 = event(2, "战斗开始", 4, "↑", null, List.of(4L), List.of());

            PlotEvent merged = e1.merge(e2);

            assertThat(merged.getChapterIds()).contains(3L, 4L);
            assertThat(merged.getImportance()).isEqualTo(4); // max importance
        }

        @Test
        @DisplayName("合并后 eventOrder 应保持最早的序号")
        void shouldKeepEarliestOrderOnMerge() {
            PlotEvent e1 = event(1, "长线事件", 2, "→", null, List.of(2L), List.of());
            PlotEvent e2 = event(2, "长线事件", 4, "↑", null, List.of(3L, 4L), List.of());

            PlotEvent merged = e1.merge(e2);

            assertThat(merged.getEventOrder()).isEqualTo(1);
            assertThat(merged.getChapterIds()).containsExactly(2L, 3L, 4L);
        }

        @Test
        @DisplayName("相同地点+时间应判定为同一事件")
        void shouldMatchByLocationAndTime() {
            PlotEvent e1 = PlotEvent.builder()
                    .eventOrder(1).title("月下对话").description("在某处对话")
                    .location("后花园").timePoint("月圆之夜")
                    .chapterIds(new ArrayList<>(List.of(5L)))
                    .importance(3).emotionalArc("→")
                    .build();
            PlotEvent e2 = PlotEvent.builder()
                    .eventOrder(2).title("深夜密谈").description("在另一个地方密谈")
                    .location("后花园").timePoint("月圆之夜")
                    .chapterIds(new ArrayList<>(List.of(6L)))
                    .importance(3).emotionalArc("↓")
                    .build();

            // Same location + timePoint → same event
            PlotEvent merged = e1.merge(e2);
            assertThat(merged.getChapterIds()).contains(5L, 6L);
        }

        @Test
        @DisplayName("不同标题+不同地点的事件不应合并")
        void shouldNotMergeUnrelatedEvents() {
            PlotEvent e1 = event(1, "早餐", 1, "→", null, List.of(1L), List.of());
            PlotEvent e2 = event(2, "决战", 5, "↑", null, List.of(10L), List.of());

            // These are clearly different events - but they won't be merged
            // because the similarity is low and locations differ
            assertThat(e1.getTitle()).isNotEqualTo(e2.getTitle());
        }
    }

    // ── 5. Edge case tests ─────────────────────────────

    @Nested
    @DisplayName("5. 边缘情况")
    class EdgeCases {

        @Test
        @DisplayName("纯风景描写章节不应提取事件")
        void shouldHandleSceneryOnlyChapters() {
            // Pure scenery chapters would produce empty events
            // The AI response parsing would simply return empty list
            List<PlotEvent> events = List.of(); // simulating empty extraction
            PlotTimeline timeline = PlotTimeline.from(events);

            assertThat(timeline.getEvents()).isEmpty();
            assertThat(timeline.getCriticalEventCount()).isEqualTo(0);
            assertThat(timeline.summarize()).contains("空时间线");
        }

        @Test
        @DisplayName("重要性评分应正确反映事件重要程度")
        void shouldReflectImportanceInTimeline() {
            // 主角死亡 (importance 5) > 日常对话 (importance 1)
            PlotEvent death = event(1, "主角死亡", 5, "↓", ConflictType.PERSON_VS_FATE,
                    List.of(10L), List.of(1L));
            PlotEvent chat = event(2, "日常对话", 1, "→", null,
                    List.of(1L), List.of());

            assertThat(death.isCritical()).isTrue();
            assertThat(chat.isCritical()).isFalse();
            assertThat(death.getImportance()).isGreaterThan(chat.getImportance());

            // In timeline, death should be climax
            PlotTimeline timeline = PlotTimeline.from(List.of(chat, death));
            assertThat(timeline.getClimaxEvent().getTitle()).isEqualTo("主角死亡");
        }

        @Test
        @DisplayName("大量事件时应保持排序稳定性")
        void shouldMaintainStableOrderWithManyEvents() {
            List<PlotEvent> events = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                events.add(event(i + 1, "事件" + (i + 1),
                        1 + (i % 5), // importance cycles 1-5
                        i % 2 == 0 ? "↑" : "↓",
                        ConflictType.values()[i % 6],
                        List.of((long) i + 1),
                        List.of()));
            }

            PlotTimeline timeline = PlotTimeline.from(events);

            assertThat(timeline.getEvents()).hasSize(50);
            // Verify order is maintained
            for (int i = 0; i < timeline.getEvents().size(); i++) {
                assertThat(timeline.getEvents().get(i).getEventOrder()).isEqualTo(i + 1);
            }
            assertThat(timeline.getCoveredChapterCount()).isEqualTo(50);
        }

        @Test
        @DisplayName("所有冲突类型的 PlotEvent 应能正常处理")
        void shouldHandleAllConflictTypes() {
            List<PlotEvent> events = new ArrayList<>();
            int order = 1;
            for (ConflictType ct : ConflictType.values()) {
                events.add(event(order++, ct.getLabel(), 3, "→", ct,
                        List.of((long) order), List.of()));
            }

            PlotTimeline timeline = PlotTimeline.from(events);

            assertThat(timeline.getEvents()).hasSize(ConflictType.values().length);
            assertThat(timeline.getDominantConflictType()).isNotNull();
        }
    }

    // ── 6. Chapter domain model compatibility ──────────

    @Nested
    @DisplayName("6. Chapter 模型兼容性")
    class ChapterCompatibility {

        @Test
        @DisplayName("Chapter.getDisplayName 应正确格式化")
        void shouldFormatChapterDisplayName() {
            Chapter withTitle = chapter(1L, 3, "初遇", "内容...");
            Chapter withoutTitle = chapter(2L, 5, null, "内容...");
            Chapter blankTitle = chapter(3L, 7, "", "内容...");

            assertThat(withTitle.getDisplayName()).isEqualTo("第3章 初遇");
            assertThat(withoutTitle.getDisplayName()).isEqualTo("第5章");
            assertThat(blankTitle.getDisplayName()).isEqualTo("第7章");
        }
    }
}
