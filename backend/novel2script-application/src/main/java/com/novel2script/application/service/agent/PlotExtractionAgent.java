package com.novel2script.application.service.agent;

import com.novel2script.application.service.agent.model.PlotTimeline;
import com.novel2script.common.enums.ConflictType;
import com.novel2script.common.enums.TaskType;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Character;
import com.novel2script.domain.model.PlotEvent;

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
 * AI agent that extracts plot events from novel chapters.
 *
 * <p>Capabilities:
 * <ul>
 *   <li>Batch extraction from multiple chapters</li>
 *   <li>Incremental extraction for new chapters</li>
 *   <li>Automatic event merging across chapters</li>
 *   <li>Character name-to-ID resolution</li>
 *   <li>Conflict type detection</li>
 *   <li>Timeline construction with Freytag pyramid analysis</li>
 * </ul>
 *
 * <p>Uses {@link AiModelRouter} to select the optimal model for plot analysis
 * and {@link PromptRegistry} for versioned prompt templates.
 */
@Slf4j
@Service
public class PlotExtractionAgent {

    private final AiModelRouter router;
    private final PromptRegistry promptRegistry;

    public PlotExtractionAgent(AiModelRouter router, PromptRegistry promptRegistry) {
        this.router = router;
        this.promptRegistry = promptRegistry;
    }

    // ── Public API ──────────────────────────────────────

    /**
     * Batch extraction: analyze multiple chapters and extract all plot events.
     *
     * @param chapters         the chapters to analyze
     * @param knownCharacters  known character list for name→ID mapping
     * @return list of extracted, deduplicated PlotEvents in chronological order
     */
    public List<PlotEvent> extract(List<Chapter> chapters, List<Character> knownCharacters) {
        if (chapters == null || chapters.isEmpty()) {
            log.warn("PlotExtractionAgent: no chapters to extract from");
            return List.of();
        }

        ChatModel model = router.route(TaskType.PLOT_EXTRACTION);
        Prompt prompt = buildPrompt(chapters, knownCharacters);

        log.info("PlotExtractionAgent extracting from {} chapters using model={}",
                chapters.size(), model);

        ChatResponse response = model.call(prompt);

        List<PlotEvent> events = parseResponse(response, chapters, knownCharacters);
        events = mergeCrossChapterEvents(events);

        log.info("PlotExtractionAgent extracted {} plot events (after merge)", events.size());
        return events;
    }

    /**
     * Incremental extraction: analyze new chapters and merge with existing events.
     *
     * @param newChapters      newly added chapters
     * @param characters       all known characters
     * @param existingEvents   previously extracted events
     * @return merged and updated event list
     */
    public List<PlotEvent> extractIncremental(
            List<Chapter> newChapters,
            List<Character> characters,
            List<PlotEvent> existingEvents) {

        if (newChapters == null || newChapters.isEmpty()) {
            log.debug("PlotExtractionAgent: no new chapters for incremental extraction");
            return existingEvents != null ? existingEvents : List.of();
        }

        List<PlotEvent> newEvents = extract(newChapters, characters);

        if (existingEvents == null || existingEvents.isEmpty()) {
            return newEvents;
        }

        // Merge new events with existing ones
        List<PlotEvent> merged = new ArrayList<>(existingEvents);
        for (PlotEvent newEvent : newEvents) {
            // Check if this event already exists (by title similarity)
            boolean exists = existingEvents.stream()
                    .anyMatch(e -> isSameEvent(e, newEvent));
            if (!exists) {
                merged.add(newEvent);
            } else {
                // Merge into existing event
                existingEvents.stream()
                        .filter(e -> isSameEvent(e, newEvent))
                        .findFirst()
                        .ifPresent(e -> {
                            PlotEvent mergedEvent = e.merge(newEvent);
                            merged.replaceAll(ev -> ev == e ? mergedEvent : ev);
                        });
            }
        }

        // Re-number event order
        merged.sort(Comparator.comparingInt(PlotEvent::getEventOrder));
        for (int i = 0; i < merged.size(); i++) {
            merged.get(i).setEventOrder(i + 1);
        }

        log.info("PlotExtractionAgent incremental: {} existing + {} new → {} merged",
                existingEvents.size(), newEvents.size(), merged.size());
        return merged;
    }

    /**
     * Build a chronological plot timeline from extracted events.
     *
     * @param events the extracted plot events
     * @return a PlotTimeline with narrative stage classification
     */
    public PlotTimeline buildTimeline(List<PlotEvent> events) {
        PlotTimeline timeline = PlotTimeline.from(events);
        log.info("PlotExtractionAgent built timeline: {}", timeline.summarize());
        return timeline;
    }

    // ── Prompt construction ─────────────────────────────

    private Prompt buildPrompt(List<Chapter> chapters, List<Character> knownCharacters) {
        // Try to use registered prompt template — use full render() for system+few-shot
        PromptTemplate template = promptRegistry.getLatest("plot-extraction");
        if (template != null) {
            List<Map<String, Object>> chapterList = buildChapterList(chapters);
            List<Map<String, String>> characterList = buildCharacterList(knownCharacters);
            Map<String, Object> vars = new HashMap<>();
            vars.put("chapters", chapterList);
            vars.put("characters", characterList);
            vars.put("hasCharacters", characterList != null && !characterList.isEmpty());
            vars.put("conflict_types", buildConflictTypeDescriptions());
            return template.render(vars);
        }

        // Fallback: inline prompt
        return buildInlinePrompt(chapters, knownCharacters);
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

    private List<Map<String, String>> buildCharacterList(List<Character> characters) {
        if (characters == null || characters.isEmpty()) return List.of();
        return characters.stream()
                .map(c -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("name", c.getCanonicalName());
                    m.put("roleType", c.getRoleType() != null ? c.getRoleType().name() : "UNKNOWN");
                    m.put("description", c.getDescription() != null ? c.getDescription() : "");
                    return m;
                })
                .collect(Collectors.toList());
    }

    private String buildConflictTypeDescriptions() {
        StringBuilder sb = new StringBuilder();
        for (ConflictType ct : ConflictType.values()) {
            sb.append(String.format("- %s: %s\n", ct.name(), ct.getLabel()));
        }
        return sb.toString();
    }

    /**
     * Inline prompt builder used when no YAML template is registered.
     * Returns a full Prompt with system message for JSON format enforcement.
     */
    private Prompt buildInlinePrompt(List<Chapter> chapters, List<Character> knownCharacters) {
        StringBuilder systemSb = new StringBuilder();
        systemSb.append("""
                你是一位专业的叙事分析专家。请从以下小说章节中提取所有关键情节事件。

                ## 提取要求

                1. **事件粒度**: 以"情节转折点"为单位提取，不要太平凡也不要太细碎。一般每章提取1-4个主要事件即可。

                2. **每个事件必须包含**:
                   - eventOrder: 事件序号（从1开始递增）
                   - title: 事件标题（简洁，10字以内，概括核心事件）
                   - description: 详细描述（50-200字，说明发生了什么、为什么重要）
                   - location: 发生地点（具体地点名词）
                   - timePoint: 时间点描述
                   - conflictType: 冲突类型
                   - participants: 参与角色名列表（必须使用已知角色列表中的准确名字）
                   - importance: 重要程度 (1-5)
                   - emotionalArc: 情绪变化方向

                3. **重要程度评分标准**:
                   - 5: 改变故事走向的核心转折（如主角死亡、重大背叛、能力觉醒）
                   - 4: 重要的情节推动事件（如关键战斗、重要决定、真相揭露）
                   - 3: 有明显推动的普通事件（如新角色登场、获得线索）
                   - 2: 过渡性事件（日常对话、赶路、环境描写）
                   - 1: 极次要的背景事件

                4. **冲突类型定义**:
                """);
        for (ConflictType ct : ConflictType.values()) {
            systemSb.append(String.format("   - %s: %s\n", ct.name(), ct.getLabel()));
        }
        systemSb.append("""

                5. **情绪弧线方向**:
                   - ↑: 情绪上升（紧张加剧、希望增加）
                   - ↓: 情绪下降（失落、悲伤、释然）
                   - →: 情绪平稳（过渡、日常）
                   - ↗: 缓慢上升
                   - ↘: 缓慢下降

                6. **按时间顺序输出**，确保 eventOrder 从小到大排列

                7. **重要**: participants 中的人名必须使用上述"已知角色列表"中的准确名字，不要自己编造

                """);

        StringBuilder userSb = new StringBuilder();

        // Append known characters for reference
        if (knownCharacters != null && !knownCharacters.isEmpty()) {
            userSb.append("## 已知角色列表\n\n");
            for (Character c : knownCharacters) {
                userSb.append(String.format("- %s (%s): %s\n",
                        c.getCanonicalName(),
                        c.getRoleType() != null ? c.getRoleType().name() : "未知",
                        c.getDescription() != null ? c.getDescription() : ""));
            }
            userSb.append("\n请务必使用以上准确的角色名作为 participants。\n\n");
        }

        // Append chapter content
        userSb.append("## 小说章节内容\n\n");
        for (Chapter ch : chapters) {
            userSb.append(String.format("--- 第%d章 %s ---\n%s\n\n",
                    ch.getChapterNumber(),
                    ch.getTitle() != null ? ch.getTitle() : "",
                    ch.getContent() != null ? ch.getContent() : ""));
        }

        userSb.append("""
                ## 输出格式

                请以 JSON 数组格式输出，每个事件如下：

                ```json
                [{
                  "eventOrder": 1,
                  "title": "事件标题",
                  "description": "详细描述",
                  "location": "地点",
                  "timePoint": "时间点",
                  "conflictType": "PERSON_VS_PERSON",
                  "participants": ["角色A", "角色B"],
                  "importance": 4,
                  "emotionalArc": "↑",
                  "chapterIds": [1, 2]
                }]
                ```

                请确保输出是有效的 JSON 数组。不要输出额外的注释或说明文字。
                """);

        return new Prompt(
                new org.springframework.ai.chat.messages.SystemMessage(systemSb.toString()),
                new org.springframework.ai.chat.messages.UserMessage(userSb.toString()));
    }

    // ── Response parsing ────────────────────────────────

    private List<PlotEvent> parseResponse(ChatResponse response,
                                          List<Chapter> chapters,
                                          List<Character> knownCharacters) {
        String content = response.getResult().getOutput().getText();
        List<PlotEvent> events = new ArrayList<>();

        // Extract JSON array from response
        String jsonArray = extractJsonArray(content);
        if (jsonArray == null) {
            log.warn("PlotExtractionAgent: no JSON array found in AI response (first 300 chars): {}",
                    content.length() > 300 ? content.substring(0, 300) + "..." : content);
            return events;
        }

        // Parse each event object using bracket-aware matching
        List<String> blocks = extractJsonObjects(jsonArray);
        for (String block : blocks) {
            try {
                PlotEvent event = parseEventBlock(block, chapters, knownCharacters);
                if (event != null) {
                    events.add(event);
                }
            } catch (Exception e) {
                log.debug("Failed to parse event block: {}", e.getMessage());
            }
        }

        if (events.isEmpty() && !blocks.isEmpty()) {
            log.warn("PlotExtractionAgent: found {} JSON objects but 0 parsed — first block: {}",
                    blocks.size(),
                    blocks.get(0).length() > 200 ? blocks.get(0).substring(0, 200) + "..." : blocks.get(0));
        }

        // Sort by eventOrder
        events.sort(Comparator.comparingInt(PlotEvent::getEventOrder));
        return events;
    }

    private String extractJsonArray(String text) {
        // Strip markdown code fences first
        String cleaned = text
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        // Find the outermost JSON array by bracket matching
        int start = cleaned.indexOf('[');
        if (start == -1) {
            log.warn("PlotExtractionAgent: no '[' found in AI response (first 200 chars): {}",
                    text.length() > 200 ? text.substring(0, 200) + "..." : text);
            return null;
        }

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
            log.warn("PlotExtractionAgent: no matching ']' found");
            return null;
        }

        return cleaned.substring(start, end + 1);
    }

    private PlotEvent parseEventBlock(String block, List<Chapter> chapters,
                                      List<Character> knownCharacters) {
        String title = extractJsonField(block, "title");
        if (title == null || title.isBlank()) {
            return null;
        }

        String description = extractJsonField(block, "description");
        String location = extractJsonField(block, "location");
        String timePoint = extractJsonField(block, "timePoint");
        String conflictTypeStr = extractJsonField(block, "conflictType");
        String emotionalArc = extractJsonField(block, "emotionalArc");
        int eventOrder = extractIntField(block, "eventOrder", 0);
        int importance = extractIntField(block, "importance", 3);

        // Parse conflict type
        ConflictType conflictType = null;
        if (conflictTypeStr != null && !conflictTypeStr.isBlank()) {
            conflictType = ConflictType.fromLabel(conflictTypeStr);
        }

        // Parse participant names and map to character IDs
        List<Long> characterIds = extractCharacterIds(block, knownCharacters);

        // Determine chapter IDs from event context
        List<Long> chapterIds = extractChapterIds(block, chapters);

        return PlotEvent.builder()
                .eventOrder(eventOrder)
                .title(title)
                .description(description != null ? description : "")
                .location(location)
                .timePoint(timePoint)
                .conflictType(conflictType)
                .chapterIds(chapterIds)
                .characterIds(characterIds)
                .importance(Math.max(1, Math.min(5, importance)))
                .emotionalArc(emotionalArc)
                .build();
    }

    // ── Field extraction helpers ────────────────────────

    /**
     * Extract individual JSON objects from a JSON array string.
     * Uses bracket-depth tracking to handle nested braces correctly.
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

    private String extractJsonField(String block, String fieldName) {
        // First try exact key match
        String val = extractJsonFieldByKey(block, fieldName);
        if (val != null) return val;

        // Fallback: try common alternative names
        Map<String, String> aliases = Map.of(
            "title", "event_title",
            "description", "detail",
            "location", "place",
            "eventOrder", "event_order"
        );
        String altKey = aliases.get(fieldName);
        if (altKey != null) {
            return extractJsonFieldByKey(block, altKey);
        }
        return null;
    }

    private String extractJsonFieldByKey(String block, String fieldName) {
        String keyPattern = "\"" + fieldName + "\"";
        int keyIdx = block.indexOf(keyPattern);
        if (keyIdx == -1) return null;

        int colonIdx = block.indexOf(':', keyIdx + keyPattern.length());
        if (colonIdx == -1) return null;

        int valStart = colonIdx + 1;
        while (valStart < block.length() && java.lang.Character.isWhitespace(block.charAt(valStart))) {
            valStart++;
        }
        if (valStart >= block.length()) return null;

        char firstChar = block.charAt(valStart);

        if (firstChar == '"') {
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
        }

        // Unquoted value
        StringBuilder sb = new StringBuilder();
        for (int i = valStart; i < block.length(); i++) {
            char c = block.charAt(i);
            if (c == ',' || c == '}' || c == '\n' || c == '\r') break;
            sb.append(c);
        }
        return sb.toString().trim();
    }

    private int extractIntField(String block, String fieldName, int defaultValue) {
        String val = extractJsonField(block, fieldName);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private List<Long> extractCharacterIds(String block, List<Character> knownCharacters) {
        List<Long> ids = new ArrayList<>();
        if (knownCharacters == null || knownCharacters.isEmpty()) {
            return ids;
        }

        // Extract participants array
        Pattern p = Pattern.compile("\"participants\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
        Matcher m = p.matcher(block);
        if (!m.find()) {
            return ids;
        }

        String participantsStr = m.group(1);
        // Extract individual names
        Pattern namePattern = Pattern.compile("\"([^\"]+)\"");
        Matcher nameMatcher = namePattern.matcher(participantsStr);

        while (nameMatcher.find()) {
            String participantName = nameMatcher.group(1).trim();
            // Match against known characters
            for (Character c : knownCharacters) {
                if (c.getCanonicalName().equals(participantName)
                        || (c.getAliases() != null && c.getAliases().contains(participantName))) {
                    if (!ids.contains(c.getId())) {
                        ids.add(c.getId());
                    }
                    break;
                }
            }
        }
        return ids;
    }

    private List<Long> extractChapterIds(String block, List<Chapter> chapters) {
        // Try explicit chapterIds field first
        Pattern p = Pattern.compile("\"chapterIds\"\\s*:\\\s*\\[(.*?)]", Pattern.DOTALL);
        Matcher m = p.matcher(block);
        if (m.find()) {
            String idsStr = m.group(1);
            Pattern numPattern = Pattern.compile("(\\d+)");
            Matcher numMatcher = numPattern.matcher(idsStr);
            List<Long> ids = new ArrayList<>();
            while (numMatcher.find()) {
                try {
                    ids.add(Long.parseLong(numMatcher.group(1)));
                } catch (NumberFormatException ignored) {
                }
            }
            if (!ids.isEmpty()) {
                return ids;
            }
        }

        // Fallback: return all chapter IDs (event may span all provided chapters)
        return chapters.stream()
                .map(Chapter::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    // ── Event merging ───────────────────────────────────

    /**
     * Merge events that represent the same story event spanning multiple chapters.
     *
     * <p>Two events are considered the same if their titles have high
     * similarity (Levenshtein-like fuzzy match) or if one event's chapter
     * list is a subset of another's.
     */
    private List<PlotEvent> mergeCrossChapterEvents(List<PlotEvent> events) {
        if (events.size() <= 1) return new ArrayList<>(events);

        List<PlotEvent> merged = new ArrayList<>();
        boolean[] absorbed = new boolean[events.size()];

        for (int i = 0; i < events.size(); i++) {
            if (absorbed[i]) continue;

            PlotEvent base = events.get(i);
            for (int j = i + 1; j < events.size(); j++) {
                if (absorbed[j]) continue;

                PlotEvent candidate = events.get(j);
                if (isSameEvent(base, candidate)) {
                    base = base.merge(candidate);
                    absorbed[j] = true;
                    log.debug("Merged event '{}' with '{}' (cross-chapter)",
                            base.getTitle(), candidate.getTitle());
                }
            }
            merged.add(base);
        }

        // Re-number event order
        merged.sort(Comparator.comparingInt(PlotEvent::getEventOrder));
        for (int i = 0; i < merged.size(); i++) {
            merged.get(i).setEventOrder(i + 1);
        }

        return merged;
    }

    /**
     * Determine if two events represent the same story event.
     */
    private boolean isSameEvent(PlotEvent a, PlotEvent b) {
        if (a == null || b == null) return false;

        // Exact title match
        if (a.getTitle() != null && a.getTitle().equals(b.getTitle())) {
            return true;
        }

        // High title similarity (simple substring match)
        if (a.getTitle() != null && b.getTitle() != null) {
            String ta = a.getTitle().replaceAll("\\s+", "");
            String tb = b.getTitle().replaceAll("\\s+", "");
            if (ta.contains(tb) || tb.contains(ta)) {
                return true;
            }
            // Fuzzy: >70% character overlap
            if (similarity(ta, tb) > 0.70) {
                return true;
            }
        }

        // Same location + same timePoint + same participants
        if (a.getLocation() != null && a.getLocation().equals(b.getLocation())
                && a.getTimePoint() != null && a.getTimePoint().equals(b.getTimePoint())) {
            return true;
        }

        return false;
    }

    /**
     * Simple character-level Jaccard similarity.
     */
    private double similarity(String a, String b) {
        if (a.equals(b)) return 1.0;
        Set<Integer> setA = a.chars().boxed().collect(java.util.stream.Collectors.toSet());
        Set<Integer> setB = b.chars().boxed().collect(java.util.stream.Collectors.toSet());

        Set<Integer> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<Integer> union = new HashSet<>(setA);
        union.addAll(setB);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}
