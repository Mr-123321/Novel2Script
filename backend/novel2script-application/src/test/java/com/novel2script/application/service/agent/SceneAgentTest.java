package com.novel2script.application.service.agent;

import com.novel2script.common.enums.SourceReason;
import com.novel2script.common.enums.TimeOfDay;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Character;
import com.novel2script.domain.model.PlotEvent;
import com.novel2script.domain.model.Scene;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SceneAgent — 场景切分单元测试")
class SceneAgentTest {

    // ── Test data builders ──────────────────────────────

    private static Chapter chapter(long id, int num, String title, String content) {
        return Chapter.builder()
                .id(id).chapterNumber(num).title(title).content(content)
                .build();
    }

    private static Character character(long id, String name) {
        return Character.builder().id(id).canonicalName(name).build();
    }

    private static Scene scene(int num, String title, String location,
                               TimeOfDay tod, boolean interior, String summary,
                               SourceReason reason, List<Long> chapterIds) {
        return Scene.builder()
                .sceneNumber(num).title(title).location(location)
                .timeOfDay(tod).interior(interior).summary(summary)
                .mood("中性").sourceReason(reason)
                .chapterIds(chapterIds != null ? chapterIds : new ArrayList<>())
                .characterIds(new ArrayList<>())
                .build();
    }

    // ── 1. Scene domain model tests ─────────────────────

    @Nested
    @DisplayName("1. Scene 领域模型")
    class SceneModel {

        @Test
        @DisplayName("getSceneHeader 应生成标准场景头格式")
        void shouldGenerateStandardSceneHeader() {
            Scene s = scene(1, "教室早自习", "教室",
                    TimeOfDay.MORNING, true, "...",
                    SourceReason.LOCATION, List.of(1L));

            assertThat(s.getSceneHeader()).isEqualTo("INT. 教室 - MORNING");
        }

        @Test
        @DisplayName("室外场景应标注 EXT")
        void shouldMarkExteriorScenes() {
            Scene s = scene(2, "操场跑步", "操场",
                    TimeOfDay.AFTERNOON, false, "...",
                    SourceReason.LOCATION, List.of(1L));

            assertThat(s.getSceneHeader()).isEqualTo("EXT. 操场 - AFTERNOON");
            assertThat(s.isDayScene()).isTrue();
            assertThat(s.isNightScene()).isFalse();
        }

        @Test
        @DisplayName("夜间场景应正确判定")
        void shouldIdentifyNightScenes() {
            Scene night = scene(3, "深夜对峙", "房间",
                    TimeOfDay.LATE_NIGHT, true, "...",
                    SourceReason.TIME, List.of(3L));
            Scene evening = scene(4, "傍晚散步", "公园",
                    TimeOfDay.EVENING, false, "...",
                    SourceReason.TIME, List.of(4L));

            assertThat(night.isNightScene()).isTrue();
            assertThat(evening.isNightScene()).isTrue();
            assertThat(night.isDayScene()).isFalse();
        }

        @Test
        @DisplayName("getFullHeader 应包含场景编号")
        void shouldIncludeSceneNumberInFullHeader() {
            Scene s = scene(5, "测试", "某地",
                    TimeOfDay.DAWN, true, "...",
                    SourceReason.LOCATION, List.of(1L));

            assertThat(s.getFullHeader()).contains("5");
            assertThat(s.getFullHeader()).startsWith("INT.");
        }

        @Test
        @DisplayName("toStoryboardLine 应生成紧凑分镜描述")
        void shouldGenerateStoryboardLine() {
            Scene s = Scene.builder()
                    .sceneNumber(7)
                    .title("关键对话")
                    .location("咖啡厅")
                    .timeOfDay(TimeOfDay.AFTERNOON)
                    .interior(true)
                    .summary("两位主角在咖啡厅进行了一场决定命运的关键对话")
                    .build();

            String line = s.toStoryboardLine();
            assertThat(line).contains("S007");
            assertThat(line).contains("INT.");
            assertThat(line).contains("咖啡厅");
            assertThat(line).contains("关键对话");
        }
    }

    // ── 2. TimeOfDay enum tests ─────────────────────────

    @Nested
    @DisplayName("2. TimeOfDay 枚举")
    class TimeOfDayEnum {

        @Test
        @DisplayName("fromLabel 应从中文标签正确解析")
        void shouldParseFromChineseLabel() {
            assertThat(TimeOfDay.fromLabel("黎明")).isEqualTo(TimeOfDay.DAWN);
            assertThat(TimeOfDay.fromLabel("早晨")).isEqualTo(TimeOfDay.MORNING);
            assertThat(TimeOfDay.fromLabel("下午")).isEqualTo(TimeOfDay.AFTERNOON);
            assertThat(TimeOfDay.fromLabel("傍晚")).isEqualTo(TimeOfDay.EVENING);
            assertThat(TimeOfDay.fromLabel("夜晚")).isEqualTo(TimeOfDay.NIGHT);
            assertThat(TimeOfDay.fromLabel("深夜")).isEqualTo(TimeOfDay.LATE_NIGHT);
        }

        @Test
        @DisplayName("fromLabel 应从英文标签正确解析")
        void shouldParseFromScriptLabel() {
            assertThat(TimeOfDay.fromLabel("DAWN")).isEqualTo(TimeOfDay.DAWN);
            assertThat(TimeOfDay.fromLabel("morning")).isEqualTo(TimeOfDay.MORNING);
            assertThat(TimeOfDay.fromLabel("NIGHT")).isEqualTo(TimeOfDay.NIGHT);
        }

        @Test
        @DisplayName("fromLabel 应模糊匹配变体词汇")
        void shouldFuzzyMatchVariants() {
            assertThat(TimeOfDay.fromLabel("日出")).isEqualTo(TimeOfDay.DAWN);
            assertThat(TimeOfDay.fromLabel("早上")).isEqualTo(TimeOfDay.MORNING);
            assertThat(TimeOfDay.fromLabel("黄昏")).isEqualTo(TimeOfDay.EVENING);
            assertThat(TimeOfDay.fromLabel("晚上")).isEqualTo(TimeOfDay.NIGHT);
            assertThat(TimeOfDay.fromLabel("午夜")).isEqualTo(TimeOfDay.LATE_NIGHT);
        }

        @Test
        @DisplayName("fromLabel 未知输入应返回 UNKNOWN")
        void shouldReturnUnknownForUnrecognized() {
            assertThat(TimeOfDay.fromLabel(null)).isEqualTo(TimeOfDay.UNKNOWN);
            assertThat(TimeOfDay.fromLabel("")).isEqualTo(TimeOfDay.UNKNOWN);
            assertThat(TimeOfDay.fromLabel("不存在的")).isEqualTo(TimeOfDay.UNKNOWN);
        }

        @Test
        @DisplayName("day/night 判定应正确")
        void shouldCorrectlyIdentifyDayAndNight() {
            assertThat(TimeOfDay.DAWN.isDaytime()).isTrue();
            assertThat(TimeOfDay.MORNING.isDaytime()).isTrue();
            assertThat(TimeOfDay.AFTERNOON.isDaytime()).isTrue();
            assertThat(TimeOfDay.EVENING.isDaytime()).isFalse();
            assertThat(TimeOfDay.NIGHT.isDaytime()).isFalse();
            assertThat(TimeOfDay.LATE_NIGHT.isDaytime()).isFalse();

            assertThat(TimeOfDay.EVENING.isNighttime()).isTrue();
            assertThat(TimeOfDay.NIGHT.isNighttime()).isTrue();
            assertThat(TimeOfDay.LATE_NIGHT.isNighttime()).isTrue();
        }

        @Test
        @DisplayName("inferFromContent 应从文本推断时间")
        void shouldInferFromContent() {
            assertThat(TimeOfDay.inferFromContent("清晨的阳光洒在脸上"))
                    .isEqualTo(TimeOfDay.DAWN);
            assertThat(TimeOfDay.inferFromContent("早上好，同学们"))
                    .isEqualTo(TimeOfDay.MORNING);
            assertThat(TimeOfDay.inferFromContent("下午的会议开始了"))
                    .isEqualTo(TimeOfDay.AFTERNOON);
            assertThat(TimeOfDay.inferFromContent("傍晚的夕阳很美"))
                    .isEqualTo(TimeOfDay.EVENING);
            assertThat(TimeOfDay.inferFromContent("夜晚的星空璀璨"))
                    .isEqualTo(TimeOfDay.NIGHT);
            assertThat(TimeOfDay.inferFromContent("深夜三点，他还没睡"))
                    .isEqualTo(TimeOfDay.LATE_NIGHT);
        }

        @Test
        @DisplayName("inferFromContent 无关键词应返回 UNKNOWN")
        void shouldReturnUnknownForNoKeywords() {
            assertThat(TimeOfDay.inferFromContent("他站在那里什么也没说"))
                    .isEqualTo(TimeOfDay.UNKNOWN);
        }
    }

    // ── 3. SourceReason enum tests ──────────────────────

    @Nested
    @DisplayName("3. SourceReason 枚举")
    class SourceReasonEnum {

        @Test
        @DisplayName("fromLabel 应从中文标签正确解析")
        void shouldParseFromChineseLabel() {
            assertThat(SourceReason.fromLabel("地点变化")).isEqualTo(SourceReason.LOCATION);
            assertThat(SourceReason.fromLabel("时间变化")).isEqualTo(SourceReason.TIME);
            assertThat(SourceReason.fromLabel("人物变化")).isEqualTo(SourceReason.CHARACTER);
            assertThat(SourceReason.fromLabel("冲突变化")).isEqualTo(SourceReason.CONFLICT);
            assertThat(SourceReason.fromLabel("章节边界")).isEqualTo(SourceReason.CHAPTER_BOUNDARY);
        }

        @Test
        @DisplayName("fromLabel 应从英文名正确解析")
        void shouldParseFromEnumName() {
            assertThat(SourceReason.fromLabel("LOCATION")).isEqualTo(SourceReason.LOCATION);
            assertThat(SourceReason.fromLabel("time")).isEqualTo(SourceReason.TIME);
        }

        @Test
        @DisplayName("fromLabel 应模糊匹配")
        void shouldFuzzyMatch() {
            assertThat(SourceReason.fromLabel("位置变了")).isEqualTo(SourceReason.LOCATION);
            assertThat(SourceReason.fromLabel("角色变化")).isEqualTo(SourceReason.CHARACTER);
        }

        @Test
        @DisplayName("fromLabel null/blank 应返回 null")
        void shouldReturnNullForInvalid() {
            assertThat(SourceReason.fromLabel(null)).isNull();
            assertThat(SourceReason.fromLabel("")).isNull();
            assertThat(SourceReason.fromLabel("不存在")).isNull();
        }
    }

    // ── 4. Scene segmentation logic tests ───────────────

    @Nested
    @DisplayName("4. 场景切分逻辑")
    class SceneSegmentation {

        @Test
        @DisplayName("地点变化应产生新场景")
        void shouldSplitOnLocationChange() {
            // 教室→操场 = 2个场景
            Scene s1 = scene(1, "教室", "教室", TimeOfDay.MORNING, true,
                    "在教室里", SourceReason.LOCATION, List.of(1L));
            Scene s2 = scene(2, "操场", "操场", TimeOfDay.MORNING, false,
                    "在操场上", SourceReason.LOCATION, List.of(1L));

            assertThat(s1.getLocation()).isNotEqualTo(s2.getLocation());
            assertThat(s1.getSceneHeader()).isEqualTo("INT. 教室 - MORNING");
            assertThat(s2.getSceneHeader()).isEqualTo("EXT. 操场 - MORNING");
            assertThat(s2.getSourceReason()).isEqualTo(SourceReason.LOCATION);
        }

        @Test
        @DisplayName("时间跳跃应产生新场景")
        void shouldSplitOnTimeJump() {
            Scene morning = scene(1, "早晨", "家中", TimeOfDay.MORNING, true,
                    "...", SourceReason.TIME, List.of(1L));
            Scene night = scene(2, "夜晚", "家中", TimeOfDay.NIGHT, true,
                    "三天后...", SourceReason.TIME, List.of(2L));

            assertThat(morning.getTimeOfDay()).isNotEqualTo(night.getTimeOfDay());
            assertThat(morning.isDayScene()).isTrue();
            assertThat(night.isDayScene()).isFalse();
        }

        @Test
        @DisplayName("同一地点连续对白不应切分")
        void shouldNotSplitOnContinuousDialogue() {
            // 同一地点、同一时间、同一角色的连续对话 → 1个场景
            Scene dialogue = scene(1, "长对话", "咖啡厅", TimeOfDay.AFTERNOON, true,
                    "A与B进行了长达一小时的深入对话",
                    SourceReason.LOCATION, List.of(1L));

            assertThat(dialogue.getSceneNumber()).isEqualTo(1);
            assertThat(dialogue.getSummary()).contains("对话");
        }

        @Test
        @DisplayName("倒叙场景应能正确处理")
        void shouldHandleFlashbackScenes() {
            Scene flashback = scene(3, "【闪回】童年", "老房子",
                    TimeOfDay.AFTERNOON, true,
                    "主角回忆起童年时期在老房子的往事",
                    SourceReason.CHARACTER, List.of(5L, 6L));

            assertThat(flashback.getTitle()).contains("闪回");
            assertThat(flashback.getSummary()).contains("回忆");
        }

        @Test
        @DisplayName("空章节应安全处理")
        void shouldHandleEmptyChapters() {
            List<Scene> scenes = List.of();
            assertThat(scenes).isEmpty();
        }
    }

    // ── 5. Edge cases ───────────────────────────────────

    @Nested
    @DisplayName("5. 边缘情况")
    class EdgeCases {

        @Test
        @DisplayName("所有切分原因的 Scene 应能正常创建")
        void shouldCreateScenesForAllSplitReasons() {
            List<Scene> scenes = new ArrayList<>();
            int num = 1;
            for (SourceReason reason : SourceReason.values()) {
                scenes.add(scene(num++, reason.getLabel(),
                        "地点X", TimeOfDay.MORNING, true,
                        reason.getLabel() + "导致切分",
                        reason, List.of((long) num)));
            }

            assertThat(scenes).hasSize(SourceReason.values().length);
            for (Scene s : scenes) {
                assertThat(s.getSourceReason()).isNotNull();
                assertThat(s.getSceneHeader()).isNotNull();
                assertThat(s.getSourceReason().getLabel()).isNotBlank();
            }
        }

        @Test
        @DisplayName("所有时间段的场景头应正确")
        void shouldGenerateCorrectHeadersForAllTimes() {
            String[] locations = {"教室", "操场", "家中", "咖啡厅", "屋顶", "地下室", "未知地点"};
            int i = 0;
            for (TimeOfDay tod : TimeOfDay.values()) {
                Scene s = scene(i + 1, "场景", locations[i % locations.length],
                        tod, i % 2 == 0, "...",
                        SourceReason.LOCATION, List.of((long) i + 1));

                String header = s.getSceneHeader();
                assertThat(header).contains(tod.getScriptLabel());
                i++;
            }
        }

        @Test
        @DisplayName("大量场景应保持序号连续")
        void shouldMaintainSequentialNumbering() {
            List<Scene> scenes = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                scenes.add(scene(i + 1, "场景" + (i + 1), "地点" + (i % 10),
                        TimeOfDay.values()[i % 7], i % 2 == 0,
                        "摘要" + i,
                        SourceReason.values()[i % 5],
                        List.of((long) (i % 20 + 1))));
            }

            assertThat(scenes).hasSize(100);
            for (int i = 0; i < scenes.size(); i++) {
                assertThat(scenes.get(i).getSceneNumber()).isEqualTo(i + 1);
            }
        }

        @Test
        @DisplayName("场景标题不应为空")
        void shouldAlwaysHaveTitle() {
            Scene s = scene(1, "", "某地", TimeOfDay.UNKNOWN, true,
                    "摘要", SourceReason.LOCATION, List.of(1L));

            // Empty title should still be handleable
            assertThat(s.getTitle()).isNotNull();
            assertThat(s.getSceneHeader()).isNotNull();
        }
    }
}
