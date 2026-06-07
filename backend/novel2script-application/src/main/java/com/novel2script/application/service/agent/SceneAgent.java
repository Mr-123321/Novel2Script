package com.novel2script.application.service.agent;

import com.novel2script.common.enums.SourceReason;
import com.novel2script.common.enums.TaskType;
import com.novel2script.common.enums.TimeOfDay;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Character;
import com.novel2script.domain.model.PlotEvent;
import com.novel2script.domain.model.Scene;

import com.novel2script.infrastructure.config.AiModelRouter;
import com.novel2script.infrastructure.prompt.PromptRegistry;
import com.novel2script.infrastructure.prompt.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI agent that segments novel chapters into film scenes.
 *
 * <h3>Segmentation Rules</h3>
 * A new scene is created when any of these conditions change:
 * <ol>
 *   <li><b>LOCATION</b> — location changes (e.g. "教室" → "操场")</li>
 *   <li><b>TIME</b> — significant time jump (gap > 30 min or explicit jump)</li>
 *   <li><b>CHARACTER</b> — major character turnover (> 50% change)</li>
 *   <li><b>CONFLICT</b> — conflict escalates or shifts type</li>
 *   <li><b>CHAPTER_BOUNDARY</b> — natural chapter boundary</li>
 * </ol>
 *
 * <p>Uses {@link AiModelRouter} to select the optimal model for scene
 * segmentation (defaults to Qwen for batch processing).
 */
@Slf4j
@Service
public class SceneAgent {

    private final AiModelRouter router;
    private final PromptRegistry promptRegistry;

    public SceneAgent(AiModelRouter router, PromptRegistry promptRegistry) {
        this.router = router;
        this.promptRegistry = promptRegistry;
    }

    // ── Public API ──────────────────────────────────────

    /**
     * Segment novel chapters into film scenes using plot events and characters as context.
     *
     * @param chapters    the novel chapters to segment
     * @param plotEvents  pre-extracted plot events (for conflict/location context)
     * @param characters  known characters (for character change detection)
     * @return list of scenes in chronological order
     */
    public List<Scene> segment(List<Chapter> chapters,
                               List<PlotEvent> plotEvents,
                               List<Character> characters) {
        if (chapters == null || chapters.isEmpty()) {
            log.warn("SceneAgent: no chapters to segment");
            return List.of();
        }

        // First pass: AI-assisted segmentation
        List<Scene> scenes = aiSegment(chapters, plotEvents, characters);

        // Second pass: rule-based refinement
        scenes = refineScenes(scenes, chapters, plotEvents, characters);

        // Enrich each scene with inferred metadata
        List<Scene> enriched = new ArrayList<>();
        for (Scene scene : scenes) {
            enriched.add(enrichScene(scene, chapters, characters));
        }

        // Re-number after enrichment
        for (int i = 0; i < enriched.size(); i++) {
            enriched.get(i).setSceneNumber(i + 1);
        }

        log.info("SceneAgent produced {} scenes from {} chapters", enriched.size(), chapters.size());
        return enriched;
    }

    // ── AI Segmentation ─────────────────────────────────

    private List<Scene> aiSegment(List<Chapter> chapters,
                                  List<PlotEvent> plotEvents,
                                  List<Character> characters) {
        ChatModel model = router.route(TaskType.SCENE_SEGMENT);
        Prompt prompt = buildPrompt(chapters, plotEvents, characters);

        log.info("SceneAgent segmenting {} chapters using model={}", chapters.size(), model);

        ChatResponse response = model.call(prompt);

        return parseResponse(response);
    }

    private Prompt buildPrompt(List<Chapter> chapters,
                               List<PlotEvent> plotEvents,
                               List<Character> characters) {
        // Try registered prompt template first — use full render() to include
        // system prompt, few-shot examples, and JSON format instructions
        PromptTemplate template = promptRegistry.getLatest("scene-segmentation");
        if (template != null) {
            List<Map<String, Object>> chapterList = buildChapterList(chapters);
            List<Map<String, String>> characterList = buildCharacterList(characters);
            List<Map<String, Object>> eventList = buildEventList(plotEvents);
            Map<String, Object> vars = new HashMap<>();
            vars.put("chapters", chapterList);
            vars.put("characters", characterList);
            vars.put("hasCharacters", characterList != null && !characterList.isEmpty());
            vars.put("events", eventList);
            vars.put("hasEvents", eventList != null && !eventList.isEmpty());
            return template.render(vars);
        }
        // Fallback: inline prompt with system message for JSON format
        return buildInlinePrompt(chapters, plotEvents, characters);
    }

    private List<Map<String, Object>> buildChapterList(List<Chapter> chapters) {
        return chapters.stream()
                .map(ch -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("chapterNumber", ch.getChapterNumber());
                    m.put("title", ch.getTitle() != null ? ch.getTitle() : "");
                    m.put("content", ch.getContent());
                    return m;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildEventList(List<PlotEvent> events) {
        if (events == null || events.isEmpty()) return List.of();
        return events.stream()
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("title", e.getTitle());
                    m.put("description", e.getDescription());
                    m.put("location", e.getLocation() != null ? e.getLocation() : "未知");
                    m.put("conflictType", e.getConflictType() != null ? e.getConflictType().getLabel() : "无");
                    return m;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, String>> buildCharacterList(List<Character> characters) {
        if (characters == null || characters.isEmpty()) return List.of();
        return characters.stream()
                .map(c -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("name", c.getCanonicalName());
                    m.put("roleType", c.getRoleType() != null ? c.getRoleType().name() : "UNKNOWN");
                    return m;
                })
                .collect(Collectors.toList());
    }

    /**
     * Inline prompt builder for fallback when no YAML template is registered.
     * Returns a full Prompt with system message for JSON format enforcement.
     */
    private Prompt buildInlinePrompt(List<Chapter> chapters,
                                     List<PlotEvent> plotEvents,
                                     List<Character> characters) {
        StringBuilder systemSb = new StringBuilder();
        systemSb.append("""
                你是一位专业的影视编剧专家。请将以下小说内容切分为影视场景。

                ## 核心原则

                一个"场景"是影视剧本的基本单位，必须具备：
                - **统一的地点**：同一个场景内地点不变
                - **连续的时间**：同一个场景内时间是连续的
                - **存在对白或动作**：至少有角色在做某事或说话

                ## 切分规则（以下任一条件变化即切分新场景）

                1. **地点变化**: 角色从一处移动到另一处（如"走出教室来到操场"→切分）
                2. **时间跳跃**: 明确的时间推进（如"第二天"、"三天后"、"深夜"、"转眼间"）
                3. **人物进出**: 重要角色登场或退场，导致场景焦点转移
                4. **冲突转变**: 对话氛围明显变化（如平静交谈→激烈争吵）
                5. **章节边界**: 自然章节结束

                ## 每个场景必须包含

                - sceneNumber: 场景序号（从1开始递增）
                - title: 场景标题（简洁有力，8字以内，概括核心事件）
                - location: 具体地点（如"教室"、"街道"、"卧室"，不要泛泛写"某处"）
                - timeOfDay: 时间段（DAWN/MORNING/AFTERNOON/EVENING/NIGHT/LATE_NIGHT/UNKNOWN）
                - interior: 室内外（true=室内INT/false=室外EXT）
                - summary: 场景摘要（30-80字，描述这个场景里发生了什么，不要重复整段原文）
                - mood: 场景氛围（如"紧张"、"温馨"、"悲伤"、"悬疑"、"凝重"）
                - characterNames: 出场角色名列表（只列出实际在此场景中说话或行动的角色）
                - sourceReason: 切分原因（LOCATION/TIME/CHARACTER/CONFLICT/CHAPTER_BOUNDARY）
                - chapterIds: 来源章节编号列表

                ## 重要提示

                - 场景数量不要太多，一般每章2-5个场景即可
                - 不要把每一段对话都切成一个场景——合并同一地点同一时间的连续内容
                - summary 要概括场景的核心事件，不要大段抄原文
                - characterNames 必须使用以上"已知角色"列表中的准确名字
                - 确保 location 是具体地点名词，不是抽象描述

                """);

        StringBuilder userSb = new StringBuilder();

        // Known characters
        if (characters != null && !characters.isEmpty()) {
            userSb.append("## 已知角色\n\n");
            for (Character c : characters) {
                userSb.append(String.format("- %s (%s): %s\n",
                        c.getCanonicalName(),
                        c.getRoleType() != null ? c.getRoleType().name() : "未知",
                        c.getDescription() != null ? c.getDescription() : ""));
            }
            userSb.append("\n");
        }

        // Chapter content
        userSb.append("## 小说内容\n\n");
        for (Chapter ch : chapters) {
            userSb.append(String.format("### 第%d章 %s\n%s\n\n",
                    ch.getChapterNumber(),
                    ch.getTitle() != null ? ch.getTitle() : "",
                    ch.getContent() != null ? ch.getContent() : ""));
        }

        userSb.append("""
                ## 输出格式

                以 JSON 数组格式输出：

                ```json
                [{
                  "sceneNumber": 1,
                  "title": "场景标题",
                  "location": "地点",
                  "timeOfDay": "MORNING",
                  "interior": true,
                  "summary": "场景摘要描述",
                  "mood": "紧张",
                  "characterNames": ["角色A", "角色B"],
                  "sourceReason": "LOCATION",
                  "chapterIds": [1]
                }]
                ```

                请确保输出是有效的 JSON 数组。
                """);

        return new Prompt(
                new org.springframework.ai.chat.messages.SystemMessage(systemSb.toString()),
                new org.springframework.ai.chat.messages.UserMessage(userSb.toString()));
    }

    // ── AI Response Parsing ─────────────────────────────

    private List<Scene> parseResponse(ChatResponse response) {
        String content = response.getResult().getOutput().getText();
        List<Scene> scenes = new ArrayList<>();

        String jsonArray = extractJsonArray(content);
        if (jsonArray == null) {
            log.warn("SceneAgent: no JSON array found in AI response");
            return scenes;
        }

        // Use string-aware bracket parsing (handles nested objects/arrays)
        List<String> blocks = extractJsonObjects(jsonArray);
        if (blocks.isEmpty()) {
            log.warn("SceneAgent: no JSON objects found in array (first 200 chars): {}",
                    jsonArray.length() > 200 ? jsonArray.substring(0, 200) + "..." : jsonArray);
            return scenes;
        }

        for (String block : blocks) {
            try {
                Scene scene = parseSceneBlock(block);
                if (scene != null) {
                    scenes.add(scene);
                }
            } catch (Exception e) {
                log.debug("Failed to parse scene block: {}", e.getMessage());
            }
        }

        if (scenes.isEmpty() && !blocks.isEmpty()) {
            log.warn("SceneAgent: parsed 0 scenes from {} JSON blocks — first block: {}",
                    blocks.size(),
                    blocks.get(0).length() > 200 ? blocks.get(0).substring(0, 200) + "..." : blocks.get(0));
        }

        scenes.sort(Comparator.comparingInt(Scene::getSceneNumber));
        return scenes;
    }

    /**
     * Extract individual JSON objects from a JSON array string.
     * Uses string-aware bracket-depth tracking to handle nested braces.
     */
    private List<String> extractJsonObjects(String jsonArray) {
        List<String> objects = new ArrayList<>();
        int i = 0;
        while (i < jsonArray.length()) {
            if (jsonArray.charAt(i) == '{') {
                int depth = 0;
                boolean inString = false;
                boolean escaped = false;
                int start = i;
                while (i < jsonArray.length()) {
                    char c = jsonArray.charAt(i);
                    if (inString) {
                        if (escaped) escaped = false;
                        else if (c == '\\') escaped = true;
                        else if (c == '"') inString = false;
                    } else {
                        if (c == '"') inString = true;
                        else if (c == '{') depth++;
                        else if (c == '}') {
                            depth--;
                            if (depth == 0) {
                                objects.add(jsonArray.substring(start, i + 1));
                                i++;
                                break;
                            }
                        }
                    }
                    i++;
                }
            } else {
                i++;
            }
        }
        return objects;
    }

    private String extractJsonArray(String text) {
        // Strip markdown code fences first
        String cleaned = text
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        // Try to find the outermost JSON array
        int start = cleaned.indexOf('[');
        if (start == -1) {
            log.warn("SceneAgent: no '[' found in AI response (first 200 chars): {}",
                    text.length() > 200 ? text.substring(0, 200) + "..." : text);
            return null;
        }

        // Find matching closing bracket
        int depth = 0;
        int end = -1;
        for (int i = start; i < cleaned.length(); i++) {
            char ch = cleaned.charAt(i);
            if (ch == '[') depth++;
            else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }

        if (end == -1) {
            log.warn("SceneAgent: no matching ']' found for array starting at {}", start);
            return null;
        }

        return cleaned.substring(start, end + 1);
    }

    private Scene parseSceneBlock(String block) {
        String title = extractJsonField(block, "title");
        if (title == null || title.isBlank()) {
            return null;
        }

        String location = extractJsonField(block, "location");
        String timeOfDayStr = extractJsonField(block, "timeOfDay");
        String summary = extractJsonField(block, "summary");
        String mood = extractJsonField(block, "mood");
        String sourceReasonStr = extractJsonField(block, "sourceReason");
        int sceneNumber = extractIntField(block, "sceneNumber", 0);

        boolean interior = extractBooleanField(block, "interior", false);

        TimeOfDay timeOfDay = TimeOfDay.fromLabel(timeOfDayStr);
        SourceReason sourceReason = SourceReason.fromLabel(sourceReasonStr);

        List<String> characterNames = extractStringList(block, "characterNames");
        List<Long> chapterIds = extractLongList(block, "chapterIds");

        return Scene.builder()
                .sceneNumber(sceneNumber)
                .title(title)
                .location(location != null ? location : "未知")
                .timeOfDay(timeOfDay)
                .interior(interior)
                .summary(summary != null ? summary : "")
                .mood(mood)
                .sourceReason(sourceReason)
                .chapterIds(chapterIds)
                .characterIds(new ArrayList<>()) // populated during enrich
                .build();
    }

    // ── Rule-based Refinement ───────────────────────────

    /**
     * Apply rule-based refinement to AI-generated scenes.
     */
    private List<Scene> refineScenes(List<Scene> scenes,
                                     List<Chapter> chapters,
                                     List<PlotEvent> plotEvents,
                                     List<Character> characters) {
        if (scenes.isEmpty()) return scenes;

        List<Scene> refined = new ArrayList<>();

        for (Scene scene : scenes) {
            // Infer time of day from chapter content if unknown
            if (scene.getTimeOfDay() == null || scene.getTimeOfDay() == TimeOfDay.UNKNOWN) {
                scene.setTimeOfDay(inferTimeOfDay(scene, chapters));
            }

            // Infer interior/exterior from location and content
            if (!scene.isInterior()) {
                boolean inferred = inferInterior(scene, chapters);
                scene.setInterior(inferred);
            }

            // Detect split reason if not provided
            if (scene.getSourceReason() == null && !refined.isEmpty()) {
                Scene previous = refined.get(refined.size() - 1);
                SourceReason reason = detectSplitReason(previous, scene, plotEvents);
                scene.setSourceReason(reason);
            }

            refined.add(scene);
        }

        return refined;
    }

    // ── Scene Enrichment ────────────────────────────────

    /**
     * Enrich a scene with additional metadata inferred from source chapters.
     */
    private Scene enrichScene(Scene scene, List<Chapter> chapters, List<Character> characters) {
        // Generate scene heading
        scene.setSceneHeading(scene.getSceneHeader());

        // Map character names to IDs
        if (characters != null && scene.getCharacterIds() != null
                && scene.getCharacterIds().isEmpty() && chapters != null) {
            List<Long> ids = resolveCharacterIds(scene, chapters, characters);
            scene.setCharacterIds(ids);
        }

        // Ensure chapter IDs are populated
        if ((scene.getChapterIds() == null || scene.getChapterIds().isEmpty()) && chapters != null) {
            scene.setChapterIds(chapters.stream()
                    .map(Chapter::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }

        return scene;
    }

    // ── Split Point Detection ───────────────────────────

    /**
     * Detect split points by analyzing chapter content and plot events.
     *
     * @return list of character positions (indices) where new scenes should start
     */
    private List<Integer> detectSplitPoints(List<Chapter> chapters, List<PlotEvent> events) {
        List<Integer> splitPoints = new ArrayList<>();
        if (chapters == null || chapters.isEmpty()) return splitPoints;

        // Chapter boundaries are always split points
        splitPoints.add(0); // first scene always starts at 0

        for (int i = 1; i < chapters.size(); i++) {
            Chapter prev = chapters.get(i - 1);
            Chapter curr = chapters.get(i);

            // Detect location change
            String prevLoc = extractLocation(prev.getContent());
            String currLoc = extractLocation(curr.getContent());
            if (!prevLoc.equals(currLoc)) {
                splitPoints.add(i);
                continue;
            }

            // Detect explicit time jump
            if (hasTimeJump(prev.getContent(), curr.getContent())) {
                splitPoints.add(i);
                continue;
            }

            // Chapter boundary is a natural split
            splitPoints.add(i);
        }

        return splitPoints;
    }

    /**
     * Detect what triggered the scene change between two consecutive scenes.
     */
    private SourceReason detectSplitReason(Scene prev, Scene curr, List<PlotEvent> events) {
        // Location change
        if (prev.getLocation() != null && curr.getLocation() != null
                && !prev.getLocation().equals(curr.getLocation())) {
            return SourceReason.LOCATION;
        }

        // Time change
        if (prev.getTimeOfDay() != curr.getTimeOfDay()) {
            return SourceReason.TIME;
        }

        // Character change (> 50% turnover)
        if (prev.getCharacterIds() != null && curr.getCharacterIds() != null) {
            int overlap = 0;
            for (Long id : prev.getCharacterIds()) {
                if (curr.getCharacterIds().contains(id)) overlap++;
            }
            int total = Math.max(prev.getCharacterIds().size(), curr.getCharacterIds().size());
            if (total > 0 && (double) overlap / total < 0.5) {
                return SourceReason.CHARACTER;
            }
        }

        // Conflict change (check if any plot event crosses this boundary)
        if (events != null) {
            for (PlotEvent event : events) {
                if (event.getConflictType() != null && event.isCrossChapter()) {
                    return SourceReason.CONFLICT;
                }
            }
        }

        return SourceReason.CHAPTER_BOUNDARY;
    }

    // ── Inference Helpers ───────────────────────────────

    /**
     * Infer the time of day from scene and chapter content.
     */
    private TimeOfDay inferTimeOfDay(Scene scene, List<Chapter> chapters) {
        // Try to find matching chapter content
        if (chapters != null && scene.getChapterIds() != null) {
            for (Chapter ch : chapters) {
                if (scene.getChapterIds().contains(ch.getId()) && ch.getContent() != null) {
                    TimeOfDay inferred = TimeOfDay.inferFromContent(ch.getContent());
                    if (inferred != TimeOfDay.UNKNOWN) {
                        return inferred;
                    }
                }
            }
        }

        // Fallback: check scene summary
        if (scene.getSummary() != null) {
            TimeOfDay inferred = TimeOfDay.inferFromContent(scene.getSummary());
            if (inferred != TimeOfDay.UNKNOWN) {
                return inferred;
            }
        }

        return TimeOfDay.UNKNOWN;
    }

    /**
     * Infer whether a scene takes place indoors or outdoors.
     */
    private boolean inferInterior(Scene scene, List<Chapter> chapters) {
        String textToCheck = "";

        if (chapters != null && scene.getChapterIds() != null) {
            for (Chapter ch : chapters) {
                if (scene.getChapterIds().contains(ch.getId()) && ch.getContent() != null) {
                    textToCheck += ch.getContent();
                }
            }
        }

        // Also check scene summary and location
        if (scene.getSummary() != null) {
            textToCheck += " " + scene.getSummary();
        }
        if (scene.getLocation() != null) {
            textToCheck += " " + scene.getLocation();
        }

        // Interior indicators
        String[] interiorKeywords = {
                "室内", "房间", "屋内", "客厅", "卧室", "厨房", "教室", "办公室",
                "走廊", "地下室", "阁楼", "宫殿", "牢房", "医院", "商店", "酒吧",
                "屋", "室", "房", "厅", "堂", "楼内"
        };

        // Exterior indicators
        String[] exteriorKeywords = {
                "室外", "户外", "街上", "广场", "操场", "公园", "森林", "山上",
                "海边", "沙漠", "田野", "天空", "花园", "庭院", "河边", "湖",
                "街", "路", "场", "外", "野", "山", "海", "林"
        };

        int interiorScore = 0;
        int exteriorScore = 0;

        for (String kw : interiorKeywords) {
            if (textToCheck.contains(kw)) interiorScore++;
        }
        for (String kw : exteriorKeywords) {
            if (textToCheck.contains(kw)) exteriorScore++;
        }

        // Check location name specifically
        if (scene.getLocation() != null) {
            String loc = scene.getLocation();
            for (String kw : interiorKeywords) {
                if (loc.contains(kw)) interiorScore += 2;
            }
            for (String kw : exteriorKeywords) {
                if (loc.contains(kw)) exteriorScore += 2;
            }
        }

        return interiorScore >= exteriorScore;
    }

    /**
     * Map character names found in scene content to known character IDs.
     */
    private List<Long> resolveCharacterIds(Scene scene, List<Chapter> chapters,
                                           List<Character> characters) {
        if (characters == null || characters.isEmpty()) {
            return List.of();
        }

        // Collect relevant chapter content
        StringBuilder content = new StringBuilder();
        if (chapters != null && scene.getChapterIds() != null) {
            for (Chapter ch : chapters) {
                if (scene.getChapterIds().contains(ch.getId()) && ch.getContent() != null) {
                    content.append(ch.getContent()).append(" ");
                }
            }
        }

        Set<Long> ids = new LinkedHashSet<>();
        for (Character c : characters) {
            if (content.toString().contains(c.getCanonicalName())) {
                ids.add(c.getId());
                continue;
            }
            if (c.getAliases() != null) {
                for (String alias : c.getAliases()) {
                    if (content.toString().contains(alias)) {
                        ids.add(c.getId());
                        break;
                    }
                }
            }
        }

        return new ArrayList<>(ids);
    }

    // ── Text Analysis Helpers ───────────────────────────

    private String extractLocation(String content) {
        if (content == null || content.isBlank()) return "";

        // Simple heuristic: look for location change markers
        // In production this would use NER or the AI model
        Pattern locPattern = Pattern.compile("(?:走进|来到|到达|回到|前往)([^，。；]{2,10})");
        Matcher m = locPattern.matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }
        return content.substring(0, Math.min(50, content.length()));
    }

    private boolean hasTimeJump(String prevContent, String currContent) {
        if (currContent == null) return false;

        // Explicit time jump markers
        String[] timeJumpPatterns = {
                "第二天", "次日", "三天后", "几天后", "一周后", "一个月后",
                "第二天早上", "第二天下午", "第二天晚上", "翌日",
                "半小时后", "一小时后", "几个小时后",
                "深夜", "凌晨", "清晨", "傍晚",
                "过了", "转眼", "时光飞逝", "眨眼间"
        };

        String firstPart = currContent.length() > 100
                ? currContent.substring(0, 100)
                : currContent;

        for (String pattern : timeJumpPatterns) {
            if (firstPart.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    // ── JSON Parsing Helpers ────────────────────────────

    private String extractJsonField(String block, String fieldName) {
        // First try with regex for quoted strings
        Pattern p = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(block);
        if (m.find()) {
            return m.group(1).trim();
        }

        // Fallback 1: string-aware extraction for escaped quotes
        String keyPattern = "\"" + fieldName + "\"";
        int keyIdx = block.indexOf(keyPattern);
        if (keyIdx != -1) {
            int colonIdx = block.indexOf(':', keyIdx + keyPattern.length());
            if (colonIdx != -1) {
                int valStart = colonIdx + 1;
                while (valStart < block.length() && java.lang.Character.isWhitespace(block.charAt(valStart))) {
                    valStart++;
                }
                if (valStart < block.length()) {
                    char firstChar = block.charAt(valStart);
                    if (firstChar == '"') {
                        // String-aware quoted value extraction
                        StringBuilder sb = new StringBuilder();
                        boolean escaped = false;
                        for (int i = valStart + 1; i < block.length(); i++) {
                            char c = block.charAt(i);
                            if (escaped) {
                                sb.append(c);
                                escaped = false;
                            } else if (c == '\\') {
                                escaped = true;
                            } else if (c == '"') {
                                return sb.toString();
                            } else {
                                sb.append(c);
                            }
                        }
                        return sb.toString();
                    } else if (firstChar != '{' && firstChar != '[') {
                        // Unquoted value
                        StringBuilder sb = new StringBuilder();
                        for (int i = valStart; i < block.length(); i++) {
                            char c = block.charAt(i);
                            if (c == ',' || c == '}' || c == '\n' || c == '\r') break;
                            sb.append(c);
                        }
                        String result = sb.toString().trim();
                        if (result.startsWith("\"") && result.endsWith("\"")) {
                            result = result.substring(1, result.length() - 1);
                        }
                        return result;
                    }
                }
            }
        }

        // Fallback 2: try with flexible pattern
        p = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*([^,\\n}]+)");
        m = p.matcher(block);
        if (m.find()) {
            String val = m.group(1).trim();
            if (val.startsWith("\"") && val.endsWith("\"")) {
                val = val.substring(1, val.length() - 1);
            }
            return val;
        }
        return null;
    }

    private int extractIntField(String block, String fieldName, int defaultValue) {
        Pattern p = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(block);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean extractBooleanField(String block, String fieldName, boolean defaultValue) {
        Pattern p = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*(true|false)");
        Matcher m = p.matcher(block);
        if (m.find()) {
            return Boolean.parseBoolean(m.group(1));
        }
        return defaultValue;
    }

    private List<String> extractStringList(String block, String fieldName) {
        List<String> result = new ArrayList<>();
        Pattern p = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
        Matcher m = p.matcher(block);
        if (!m.find()) return result;

        String arrayContent = m.group(1);
        Pattern namePattern = Pattern.compile("\"([^\"]+)\"");
        Matcher nameMatcher = namePattern.matcher(arrayContent);
        while (nameMatcher.find()) {
            result.add(nameMatcher.group(1).trim());
        }
        return result;
    }

    private List<Long> extractLongList(String block, String fieldName) {
        List<Long> result = new ArrayList<>();
        Pattern p = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
        Matcher m = p.matcher(block);
        if (!m.find()) return result;

        String arrayContent = m.group(1);
        Pattern numPattern = Pattern.compile("(\\d+)");
        Matcher numMatcher = numPattern.matcher(arrayContent);
        while (numMatcher.find()) {
            try {
                result.add(Long.parseLong(numMatcher.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }
}
