package com.novel2script.application.service.agent;

import com.novel2script.application.service.agent.checker.DialogueConsistencyChecker;
import com.novel2script.application.service.agent.model.ConsistencyIssue;
import com.novel2script.common.enums.Emotion;
import com.novel2script.common.enums.TaskType;
import com.novel2script.domain.model.Character;
import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.model.PlotEvent;
import com.novel2script.domain.model.Scene;
import com.novel2script.infrastructure.annotation.AiMonitored;
import com.novel2script.infrastructure.config.AiModelRouter;
import com.novel2script.infrastructure.prompt.PromptRegistry;
import com.novel2script.infrastructure.prompt.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Core AI agent for generating film-quality dialogue.
 *
 * <p>This is the heart of Novel2Script. It generates character-consistent
 * dialogue based on:
 * <ul>
 *   <li>Character personality profiles (identity, traits, relationships)</li>
 *   <li>Scene context (location, time, mood, events)</li>
 *   <li>Narrative flow (previous scene, plot progression)</li>
 *   <li>Screenplay conventions (show-don't-tell, subtext, rhythm)</li>
 * </ul>
 *
 * <h3>Key Principles</h3>
 * <ol>
 *   <li><b>SHOW, DON'T TELL</b> — reveal conflict through words, not narration</li>
 *   <li><b>VOICE MATTERS</b> — each character has a distinct speech style</li>
 *   <li><b>SUBTEXT</b> — the best dialogue is about what's NOT being said</li>
 *   <li><b>RHYTHM</b> — vary sentence length, alternate fast and slow</li>
 *   <li><b>NO EXPOSITION DUMP</b> — never explain background through dialogue</li>
 * </ol>
 */
@Slf4j
@Service
public class DialogueAgent {

    private final AiModelRouter router;
    private final PromptRegistry promptRegistry;
    private final DialogueConsistencyChecker consistencyChecker;

    public DialogueAgent(AiModelRouter router,
                         PromptRegistry promptRegistry,
                         DialogueConsistencyChecker consistencyChecker) {
        this.router = router;
        this.promptRegistry = promptRegistry;
        this.consistencyChecker = consistencyChecker;
    }

    // ── Public API ──────────────────────────────────────

    /**
     * Generate all dialogue lines for a scene.
     *
     * @param scene               the target scene
     * @param presentCharacters   characters present in this scene
     * @param sceneEvents         plot events occurring in this scene
     * @param previousScene       the previous scene (for narrative continuity), nullable
     * @param characterEmotions   character_id → current emotional state override
     * @return list of generated dialogues in sequence order
     */
    @AiMonitored(value = "dialogue-generation", version = "1.0")
    public List<Dialogue> generate(Scene scene,
                                   List<Character> presentCharacters,
                                   List<PlotEvent> sceneEvents,
                                   Scene previousScene,
                                   Map<Long, String> characterEmotions) {
        if (scene == null || presentCharacters == null || presentCharacters.isEmpty()) {
            log.warn("DialogueAgent: scene or characters missing");
            return List.of();
        }

        ChatModel model = router.route(TaskType.DIALOGUE_GENERATE);

        log.info("DialogueAgent generating for scene '{}' with {} characters using model={}",
                scene.getTitle(), presentCharacters.size(), model);

        org.springframework.ai.chat.prompt.Prompt fullPrompt = buildPrompt(
                scene, presentCharacters, sceneEvents, previousScene, characterEmotions);
        ChatResponse response = model.call(fullPrompt);

        // Log token usage
        logTokenUsage(response, fullPrompt.getContents().toString(), "dialogue-generation");

        // ── Log raw response for diagnosis (first 500 chars) ──
        String rawText = response.getResult().getOutput().getText();
        log.debug("DialogueAgent raw response for '{}' ({} chars, first 500):\n{}",
                scene.getTitle(), rawText.length(),
                rawText.length() > 500 ? rawText.substring(0, 500) + "…" : rawText);

        List<Dialogue> dialogues = parseResponse(response, scene, presentCharacters);

        // Run consistency check
        List<ConsistencyIssue> issues = consistencyChecker.check(dialogues, presentCharacters);
        if (!issues.isEmpty()) {
            log.info("Consistency check found {} issues for scene '{}'", issues.size(), scene.getTitle());
        }

        // Assign sequence numbers
        for (int i = 0; i < dialogues.size(); i++) {
            dialogues.get(i).setSequence(i + 1);
        }

        log.info("DialogueAgent generated {} lines for scene '{}'", dialogues.size(), scene.getTitle());
        return dialogues;
    }

    /**
     * Suggest the next line of dialogue — useful for interactive editors.
     *
     * @param scene              the current scene
     * @param existingDialogues  already generated dialogue lines
     * @param characters         all available characters
     * @return a single suggested Dialogue line, or null
     */
    @AiMonitored(value = "dialogue-suggest", version = "1.0")
    public Dialogue suggestNext(Scene scene,
                                List<Dialogue> existingDialogues,
                                List<Character> characters) {
        if (scene == null || characters == null || characters.isEmpty()) {
            return null;
        }

        ChatModel model = router.route(TaskType.DIALOGUE_GENERATE);
        String prompt = buildSuggestPrompt(scene, existingDialogues, characters);

        ChatResponse response = model.call(
                new org.springframework.ai.chat.prompt.Prompt(
                        new org.springframework.ai.chat.messages.UserMessage(prompt)));

        List<Dialogue> results = parseResponse(response, scene, characters);
        if (!results.isEmpty()) {
            Dialogue next = results.get(0);
            next.setSequence(existingDialogues != null ? existingDialogues.size() + 1 : 1);
            return next;
        }
        return null;
    }

    /**
     * Batch-generate dialogues for multiple scenes in parallel.
     * Uses a thread pool for concurrent AI calls — dramatically reduces wall-clock time
     * when processing many scenes.
     *
     * @param scenes       all scenes to generate dialogue for
     * @param characters   all characters in the script
     * @param sceneEvents  scene_id → list of plot events occurring in that scene
     * @return scene_id → list of generated dialogues
     */
    public Map<Long, List<Dialogue>> generateBatch(List<Scene> scenes,
                                                    List<Character> characters,
                                                    Map<Long, List<PlotEvent>> sceneEvents) {
        if (scenes == null || scenes.isEmpty()) {
            return Map.of();
        }

        int parallelism = Math.min(scenes.size(), Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(2, parallelism));

        try {
            Map<Long, List<Dialogue>> results = new LinkedHashMap<>();
            List<CompletableFuture<Map.Entry<Long, List<Dialogue>>>> futures = new ArrayList<>();

            for (int i = 0; i < scenes.size(); i++) {
                final Scene scene = scenes.get(i);
                final int sceneIndex = i;

                CompletableFuture<Map.Entry<Long, List<Dialogue>>> future =
                        CompletableFuture.supplyAsync(() -> {
                    List<Character> presentCharacters = filterPresentCharacters(scene, characters);
                    List<PlotEvent> events = sceneEvents != null
                            ? sceneEvents.getOrDefault(scene.getId(), List.of())
                            : List.of();

                    // Determine previous scene for narrative continuity
                    Scene prevScene = sceneIndex > 0 ? scenes.get(sceneIndex - 1) : null;

                    List<Dialogue> dialogues = generate(scene, presentCharacters, events, prevScene, null);
                    assignCharacterIds(dialogues, characters);
                    return Map.entry(scene.getId(), dialogues);
                }, executor);

                futures.add(future);
            }

            // Collect results in order
            for (CompletableFuture<Map.Entry<Long, List<Dialogue>>> future : futures) {
                try {
                    Map.Entry<Long, List<Dialogue>> entry = future.get(60, TimeUnit.SECONDS);
                    results.put(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    log.error("DialogueAgent batch: scene generation timed out or failed: {}", e.getMessage());
                    // Add empty result for failed scene
                    results.put(scenes.get(results.size()).getId(), List.of());
                }
            }

            log.info("DialogueAgent parallel batch: generated dialogues for {} scenes (parallelism={})",
                    scenes.size(), parallelism);
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    // ── Prompt Construction ─────────────────────────────

    private org.springframework.ai.chat.prompt.Prompt buildPrompt(Scene scene,
                               List<Character> presentCharacters,
                               List<PlotEvent> sceneEvents,
                               Scene previousScene,
                               Map<Long, String> characterEmotions) {
        // Try registered template first — use render() to include system
        // message, few-shot examples, and output schema instructions
        PromptTemplate template = promptRegistry.getLatest("dialogue-generation");
        if (template != null) {
            Map<String, Object> vars = buildTemplateVariables(
                    scene, presentCharacters, sceneEvents, previousScene, characterEmotions);
            return template.render(vars);
        }
        // Fallback: inline prompt without template
        String userContent = buildInlinePrompt(
                scene, presentCharacters, sceneEvents, previousScene, characterEmotions);
        return new org.springframework.ai.chat.prompt.Prompt(
                new org.springframework.ai.chat.messages.UserMessage(userContent));
    }

    private Map<String, Object> buildTemplateVariables(Scene scene,
                                                        List<Character> presentCharacters,
                                                        List<PlotEvent> sceneEvents,
                                                        Scene previousScene,
                                                        Map<Long, String> characterEmotions) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("sceneTitle", scene.getTitle());
        vars.put("location", scene.getLocation() != null ? scene.getLocation() : "未知");
        vars.put("isInterior", scene.isInterior() ? "内" : "外");
        vars.put("timeOfDay", scene.getTimeOfDay() != null ? scene.getTimeOfDay().getScriptLabel() : "UNKNOWN");
        vars.put("mood", scene.getMood() != null ? scene.getMood() : "中性");
        vars.put("summary", scene.getSummary() != null ? scene.getSummary() : "");

        // Character profiles
        List<Map<String, String>> profiles = new ArrayList<>();
        for (Character c : presentCharacters) {
            Map<String, String> profile = new HashMap<>();
            profile.put("name", c.getCanonicalName());
            profile.put("description", c.getDescription() != null ? c.getDescription() : "");
            profile.put("personality", c.getPersonality() != null
                    ? String.join("、", c.getPersonality())
                    : "未知");
            profile.put("speechStyle", Emotion.inferSpeechStyle(c.getPersonality()));
            profile.put("gender", c.getGender() != null ? c.getGender() : "UNKNOWN");
            profile.put("roleType", c.getRoleType() != null ? c.getRoleType().name() : "UNKNOWN");

            // Emotion override or default
            if (characterEmotions != null && c.getId() != null && characterEmotions.containsKey(c.getId())) {
                profile.put("currentEmotion", characterEmotions.get(c.getId()));
            } else {
                profile.put("currentEmotion", "自然");
            }

            // Relationships
            if (c.getRelationships() != null && !c.getRelationships().isEmpty()) {
                List<String> rels = new ArrayList<>();
                for (var rel : c.getRelationships()) {
                    rels.add(rel.getTarget() + ": " + rel.getRelation());
                }
                profile.put("relationships", String.join("; ", rels));
            } else {
                profile.put("relationships", "无明显关系");
            }

            profiles.add(profile);
        }
        vars.put("characterProfiles", profiles);

        // Scene events
        if (sceneEvents != null && !sceneEvents.isEmpty()) {
            List<Map<String, String>> events = new ArrayList<>();
            for (PlotEvent e : sceneEvents) {
                Map<String, String> evt = new HashMap<>();
                evt.put("description", e.getDescription());
                evt.put("conflictType", e.getConflictType() != null ? e.getConflictType().getLabel() : "无");
                events.add(evt);
            }
            vars.put("sceneEvents", events);
            vars.put("hasSceneEvents", true);
        } else {
            vars.put("hasSceneEvents", false);
        }

        // Previous scene summary
        if (previousScene != null) {
            vars.put("previousSceneSummary", previousScene.getSummary() != null
                    ? previousScene.getSummary() : "无");
            vars.put("hasPreviousScene", true);
        } else {
            vars.put("previousSceneSummary", "这是第一个场景");
            vars.put("hasPreviousScene", false);
        }

        return vars;
    }

    private String buildInlinePrompt(Scene scene,
                                     List<Character> presentCharacters,
                                     List<PlotEvent> sceneEvents,
                                     Scene previousScene,
                                     Map<Long, String> characterEmotions) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位专业影视编剧，请为以下场景生成高质量的角色对话。\n\n");

        // ── Writing principles ────────────────────────
        sb.append("""
                ## 创作原则

                1. **SHOW, DON'T TELL** — 通过对话展现冲突和情感，不要直接说明
                2. **VOICE MATTERS** — 每个角色有独特的说话方式，符合其性格和身份
                3. **SUBTEXT** — 最好的对白往往在说"别的"，潜台词比字面更重要
                4. **RHYTHM** — 长短句交替，注意对话节奏
                5. **NO EXPOSITION DUMP** — 不要通过对话大量解释背景信息
                6. **每句不超过50字** — 口语化表达，避免书面长句

                """);

        // ── Scene context ─────────────────────────────
        sb.append(buildSceneContext(scene, previousScene, sceneEvents));

        // ── Character context ─────────────────────────
        sb.append(buildCharacterContexts(presentCharacters, characterEmotions));

        // ── Output format ─────────────────────────────
        sb.append("""
                ## 输出格式

                请以 JSON 数组格式输出所有角色的对白，按对话顺序排列。每个对话对象包含：
                - speaker: 说话人姓名
                - content: 对白内容（每句≤50字）
                - emotion: 情绪标签（中文，如"平静"、"愤怒"、"悲伤"等）
                - parenthetical: 括号说明（如"(低声)"、"(冷笑)"、"(犹豫)"，无则填null）
                - replyTo: 回复的对白序号（从0开始，首句为0）

                ⚠️ 重要提示：
                - 请基于场景氛围和角色性格**主动创作**合理的对话，不要只返回空数组
                - 即使原文没有逐字对话，也可以根据场景冲突和角色关系**推断**他们会说什么
                - 每个场景至少生成 2-4 句对话（如果有2个或以上角色在场）
                - 只有场景中只有**一个角色且无说话对象**时，才返回空数组

                ```json
                [{
                  "speaker": "角色A",
                  "content": "这就是你的选择？",
                  "emotion": "失望",
                  "parenthetical": "盯着对方",
                  "replyTo": 0
                }]
                ```

                请确保输出是有效的 JSON 数组。
                """);

        return sb.toString();
    }

    private String buildSceneContext(Scene scene, Scene previousScene, List<PlotEvent> sceneEvents) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 场景信息\n\n");

        sb.append(String.format("- **地点**: %s (%s)\n",
                scene.getLocation() != null ? scene.getLocation() : "未知",
                scene.isInterior() ? "内景" : "外景"));
        sb.append(String.format("- **时间**: %s\n",
                scene.getTimeOfDay() != null ? scene.getTimeOfDay().getChineseLabel() : "未知"));
        sb.append(String.format("- **氛围**: %s\n",
                scene.getMood() != null ? scene.getMood() : "中性"));
        sb.append(String.format("- **剧情概要**: %s\n\n",
                scene.getSummary() != null ? scene.getSummary() : "无"));

        if (sceneEvents != null && !sceneEvents.isEmpty()) {
            sb.append("### 场景中的事件\n\n");
            for (PlotEvent event : sceneEvents) {
                sb.append(String.format("- %s (冲突: %s)\n",
                        event.getDescription(),
                        event.getConflictType() != null ? event.getConflictType().getLabel() : "无"));
            }
            sb.append("\n");
        }

        if (previousScene != null) {
            sb.append(String.format("### 前一个场景\n%s\n\n",
                    previousScene.getSummary() != null ? previousScene.getSummary() : "无"));
        }

        return sb.toString();
    }

    private String buildCharacterContexts(List<Character> characters, Map<Long, String> emotions) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 角色设定\n\n");

        for (Character c : characters) {
            sb.append(String.format("### 【%s】\n", c.getCanonicalName()));
            sb.append(String.format("- 身份: %s\n", c.getDescription() != null ? c.getDescription() : "未知"));

            if (c.getGender() != null) {
                sb.append(String.format("- 性别: %s\n", c.getGender()));
            }

            if (c.getPersonality() != null && !c.getPersonality().isEmpty()) {
                sb.append(String.format("- 性格: %s\n", String.join("、", c.getPersonality())));
                sb.append(String.format("- 说话风格: %s\n", Emotion.inferSpeechStyle(c.getPersonality())));
            }

            if (c.getRoleType() != null) {
                sb.append(String.format("- 角色类型: %s\n", c.getRoleType().name()));
            }

            // Current emotion
            if (emotions != null && c.getId() != null && emotions.containsKey(c.getId())) {
                sb.append(String.format("- 当前情绪: %s\n", emotions.get(c.getId())));
            }

            // Relationships with other present characters
            if (c.getRelationships() != null && !c.getRelationships().isEmpty()) {
                sb.append("- 关系: ");
                for (var rel : c.getRelationships()) {
                    sb.append(String.format("%s=%s ", rel.getTarget(), rel.getRelation()));
                }
                sb.append("\n");
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    private String buildSuggestPrompt(Scene scene, List<Dialogue> existingDialogues, List<Character> characters) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildInlinePrompt(scene, characters, null, null, null));
        sb.append("\n## 已有对话\n\n");

        if (existingDialogues != null && !existingDialogues.isEmpty()) {
            for (Dialogue d : existingDialogues) {
                sb.append(String.format("%s: \"%s\"%s\n",
                        d.getSpeaker(),
                        d.getContent(),
                        d.getParenthetical() != null ? " " + d.getParenthetical() : ""));
            }
        }

        sb.append("\n请生成下一个角色的对白。只输出一句，JSON 对象格式（非数组）。\n");
        return sb.toString();
    }

    // ── Response Parsing ────────────────────────────────

    private List<Dialogue> parseResponse(ChatResponse response, Scene scene, List<Character> characters) {
        String content = response.getResult().getOutput().getText();
        List<Dialogue> dialogues = new ArrayList<>();

        // ── Try bare array first: [...] ──
        String jsonArray = extractJsonArray(content);
        if (jsonArray != null) {
            if (jsonArray.trim().equals("[]")) {
                log.info("DialogueAgent: empty array for scene '{}'", scene.getTitle());
            } else {
                dialogues = parseDialogueList(jsonArray, scene, characters);
            }
        }

        // ── Try wrapper objects with multiple field names ──
        if (dialogues.isEmpty()) {
            String wrapperJson = extractJsonObject(content);
            if (wrapperJson != null) {
                for (String fieldName : List.of("dialogues", "lines", "conversations",
                        "dialogue_list", "dialogueList")) {
                    String arrayStr = extractJsonField(wrapperJson, fieldName);
                    if (arrayStr != null && !arrayStr.isBlank()) {
                        dialogues = parseDialogueList(arrayStr, scene, characters);
                        if (!dialogues.isEmpty()) break;
                    }
                }
            }
        }

        // ── Try single object (for suggestNext) ──
        if (dialogues.isEmpty()) {
            String singleJson = extractJsonObject(content);
            if (singleJson != null) {
                Dialogue d = parseDialogueBlock(singleJson, scene, characters);
                if (d != null) dialogues.add(d);
            }
        }

        // ── Fallback 1: regex extraction from scene summary ──
        if (dialogues.isEmpty() && scene.getSummary() != null && !scene.getSummary().isBlank()) {
            dialogues = extractDialoguesFromSummary(scene, characters);
            if (!dialogues.isEmpty()) {
                log.info("DialogueAgent: extracted {} dialogues from summary for '{}'",
                        dialogues.size(), scene.getTitle());
            }
        }

        // ── Fallback 2: extract quoted strings from raw AI response text ──
        if (dialogues.isEmpty()) {
            dialogues = extractDialoguesFromNarration(content, scene, characters);
            if (!dialogues.isEmpty()) {
                log.info("DialogueAgent: extracted {} dialogues from raw AI response text for '{}'",
                        dialogues.size(), scene.getTitle());
            }
        }

        // ── Final fallback: one placeholder dialogue per scene ──
        if (dialogues.isEmpty() && characters != null && characters.size() >= 2) {
            log.warn("DialogueAgent: all parsing failed for '{}' — generating placeholder dialogue", scene.getTitle());
            dialogues = generatePlaceholderDialogues(scene, characters);
        }

        if (dialogues.isEmpty() && jsonArray == null && content.indexOf('{') < 0 && content.indexOf('[') < 0) {
            log.warn("DialogueAgent: no JSON in AI response for '{}' (first 300 chars): {}",
                    scene.getTitle(),
                    content.length() > 300 ? content.substring(0, 300) + "..." : content);
        }

        // Build replyTo chain
        for (int i = 0; i < dialogues.size(); i++) {
            if (i > 0 && dialogues.get(i).getReplyTo() == null) {
                dialogues.get(i).setReplyTo(dialogues.get(i - 1).getId());
            }
        }

        return dialogues;
    }

    /**
     * Extract quoted speech from narration/pure text (not JSON).
     * Handles patterns like: 萧炎说"三十年河东"  或  「三十年河东，三十年河西」
     */
    private List<Dialogue> extractDialoguesFromNarration(String text, Scene scene, List<Character> characters) {
        List<Dialogue> dialogues = new ArrayList<>();
        if (text == null || text.isBlank()) return dialogues;

        int seq = 0;

        // Pattern 1: 说话人 + 说/道/问 + "内容"
        java.util.regex.Pattern speechPattern = java.util.regex.Pattern.compile(
                "([^：:\"'\"'「『\\s]{1,8})" +
                "(?:冷冷|淡淡|低声|大声|轻声|小声|怒|笑|哭|吼|喊|缓缓|慢慢)?" +
                "(?:说道|说道：|说：|说|道：|道|喊道|问道|答道|回道|答|问|冷声道|笑道|怒道)" +
                "[：:\"'\"『「]?" +
                "(.{2,80})" +
                "[\"'\"」』]?");
        java.util.regex.Matcher m = speechPattern.matcher(text);
        while (m.find() && seq < 10) {
            String speaker = m.group(1).trim();
            String content = m.groupCount() >= 2 && m.group(2) != null ? m.group(2).trim() : "";
            if (content.isEmpty()) continue;
            // Skip if speaker name is clearly noise (e.g., action description)
            if (speaker.length() > 4 || CharacterNameFilter.isNoiseQuick(speaker)) continue;
            Long characterId = resolveSpeakerId(speaker, characters, seq);
            dialogues.add(Dialogue.builder()
                    .sceneId(scene.getId()).characterId(characterId)
                    .speaker(speaker).content(content)
                    .emotion(com.novel2script.common.enums.Emotion.CALM)
                    .sequence(seq + 1).build());
            seq++;
        }

        // Pattern 2: bare quotes 「...」 or "..."
        if (dialogues.isEmpty()) {
            java.util.regex.Pattern quotePattern = java.util.regex.Pattern.compile(
                    "[「\"'\"](.{2,80})[」\"'\"]");
            java.util.regex.Matcher qm = quotePattern.matcher(text);
            while (qm.find() && seq < 10) {
                String content = qm.group(1).trim();
                int idx = seq % (characters != null && !characters.isEmpty() ? characters.size() : 1);
                Character c = characters != null && !characters.isEmpty() ? characters.get(idx) : null;
                dialogues.add(Dialogue.builder()
                        .sceneId(scene.getId())
                        .characterId(c != null ? c.getId() : null)
                        .speaker(c != null ? c.getCanonicalName() : "未知")
                        .content(content)
                        .emotion(com.novel2script.common.enums.Emotion.CALM)
                        .sequence(seq + 1).build());
                seq++;
            }
        }

        return dialogues;
    }

    /** Generate placeholder dialogues when all else fails — ensures pipeline doesn't break. */
    private List<Dialogue> generatePlaceholderDialogues(Scene scene, List<Character> characters) {
        List<Dialogue> dialogues = new ArrayList<>();
        String[][] fallbackLines = {
            {"CALM", "嗯。"},
            {"CALM", "我明白了。"},
            {"SURPRISED", "什么？"},
            {"CALM", "走吧。"}
        };
        for (int i = 0; i < Math.min(2, characters.size()); i++) {
            String[] line = fallbackLines[i % fallbackLines.length];
            Character c = characters.get(i % characters.size());
            dialogues.add(Dialogue.builder()
                    .sceneId(scene.getId()).characterId(c.getId())
                    .speaker(c.getCanonicalName()).content(line[1])
                    .emotion(com.novel2script.common.enums.Emotion.valueOf(line[0]))
                    .sequence(i + 1).build());
        }
        return dialogues;
    }

    /**
     * Last-resort fallback: extract quoted speech from scene summary text
     * using regex patterns. Handles Chinese speech markers (说、道、问 etc.)
     * and direct quotes (「」, "", etc.).
     */
    private List<Dialogue> extractDialoguesFromSummary(Scene scene, List<Character> characters) {
        List<Dialogue> dialogues = new ArrayList<>();
        String summary = scene.getSummary();
        if (summary == null || summary.isBlank()) return dialogues;

        // Pattern 1: SpeakerName(optional modifier) + speechVerb + "content"
        java.util.regex.Pattern speechPattern = java.util.regex.Pattern.compile(
                "([^：:\"'\"'「『\\s]{1,6})" +
                "(?:冷冷|淡淡|低声|大声|轻声|小声|怒|笑|哭|吼|喊)?" +
                "(?:说道|说道：|说：|说|道：|道|喊道|问道|答道|回道|答|问)" +
                "[：:\"'\"『「]?" +
                "(.{2,60})" +
                "[\"'\"」』]?");
        java.util.regex.Matcher m = speechPattern.matcher(summary);

        int seq = 0;
        while (m.find() && seq < 10) {
            String speakerName = m.group(1).trim();
            String content = m.groupCount() >= 2 && m.group(2) != null
                    ? m.group(2).trim() : "";
            if (content.isEmpty()) continue;

            Long characterId = resolveSpeakerId(speakerName, characters, seq);
            dialogues.add(Dialogue.builder()
                    .sceneId(scene.getId())
                    .characterId(characterId)
                    .speaker(speakerName)
                    .content(content)
                    .emotion(com.novel2script.common.enums.Emotion.CALM)
                    .sequence(seq + 1)
                    .build());
            seq++;
        }

        // If pattern 1 found nothing, try pattern 2: 「...」 or "..." or "..."
        if (dialogues.isEmpty()) {
            java.util.regex.Pattern quotePattern = java.util.regex.Pattern.compile(
                    "[「\"'\"](.{2,80})[」\"'\"]");
            java.util.regex.Matcher qm = quotePattern.matcher(summary);
            while (qm.find() && seq < 10) {
                String content = qm.group(1).trim();
                Long characterId = characters != null && !characters.isEmpty()
                        ? characters.get(seq % characters.size()).getId()
                        : null;
                String speaker = characters != null && !characters.isEmpty()
                        ? characters.get(seq % characters.size()).getCanonicalName()
                        : "未知";
                dialogues.add(Dialogue.builder()
                        .sceneId(scene.getId())
                        .characterId(characterId)
                        .speaker(speaker)
                        .content(content)
                        .emotion(com.novel2script.common.enums.Emotion.CALM)
                        .sequence(seq + 1)
                        .build());
                seq++;
            }
        }

        return dialogues;
    }

    // ── Speaker name resolution ─────────────────────────

    /** Common honorific/kinship suffixes that can be stripped to reveal the base name */
    private static final Set<String> HONORIFIC_SUFFIXES = Set.of(
            "哥哥", "姐姐", "叔叔", "阿姨", "伯伯", "婶婶", "嫂子",
            "师傅", "师父", "老师", "师兄", "师姐", "师弟", "师妹",
            "先生", "女士", "小姐", "少爷", "夫人", "老爷", "老太太",
            "大人", "前辈", "长老", "掌门", "宗主", "门主", "帮主",
            "兄", "哥", "姐", "妹", "叔", "姨", "伯", "婶", "嫂", "爷", "奶"
    );

    /** Resolve a speaker name to a character ID from the present characters list.
     * Handles honorific suffixes (e.g. "萧炎哥哥" → "萧炎"),
     * alias matching, and noise filtering. */
    private Long resolveSpeakerId(String speakerName, List<Character> characters, int fallbackIndex) {
        if (characters == null || characters.isEmpty()) return null;
        if (speakerName == null || speakerName.isBlank()) return null;

        // 1. Quick exact match on canonical name
        for (Character c : characters) {
            if (speakerName.equals(c.getCanonicalName())) return c.getId();
            if (c.getAliases() != null && c.getAliases().contains(speakerName)) return c.getId();
        }

        // 2. Strip honorific suffixes and try again
        String baseName = stripHonorific(speakerName);
        if (!baseName.equals(speakerName)) {
            for (Character c : characters) {
                if (baseName.equals(c.getCanonicalName())) return c.getId();
                if (c.getAliases() != null && c.getAliases().contains(baseName)) return c.getId();
            }
        }

        // 3. Prefix stripping (小X, 老X, 阿X) — try with/without prefix
        String unprefixed = stripPrefix(speakerName);
        if (!unprefixed.equals(speakerName)) {
            for (Character c : characters) {
                if (c.getCanonicalName() != null && c.getCanonicalName().contains(unprefixed)) return c.getId();
                if (unprefixed.contains(c.getCanonicalName())) return c.getId();
            }
        }

        // 4. Contains match (bidirectional, but only if speakerName ≥2 chars to avoid false positives)
        if (speakerName.length() >= 2) {
            for (Character c : characters) {
                if (c.getCanonicalName() != null && c.getCanonicalName().length() >= 2) {
                    if (c.getCanonicalName().contains(speakerName)) return c.getId();
                    if (speakerName.contains(c.getCanonicalName())) return c.getId();
                }
            }
        }

        // 5. Fallback: return null instead of randomly assigning
        //    (was: characters.get(fallbackIndex % characters.size()).getId())
        return null;
    }

    /** Strip common honorific suffixes e.g. "萧炎哥哥" → "萧炎" */
    static String stripHonorific(String name) {
        if (name == null || name.length() < 2) return name;
        for (String suffix : HONORIFIC_SUFFIXES) {
            if (name.length() > suffix.length() && name.endsWith(suffix)) {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        return name;
    }

    /** Strip common name prefixes e.g. "小川" → "川" */
    private static String stripPrefix(String name) {
        if (name == null || name.length() < 2) return name;
        char first = name.charAt(0);
        if (first == '小' || first == '老' || first == '阿' || first == '大') {
            return name.substring(1);
        }
        return name;
    }

    /**
     * Parse a JSON string representing an array of dialogue objects.
     * Handles nested braces within dialogue objects.
     */
    private List<Dialogue> parseDialogueList(String jsonText, Scene scene, List<Character> characters) {
        List<Dialogue> dialogues = new ArrayList<>();
        if (jsonText == null || jsonText.isBlank()) return dialogues;

        // Find all top-level JSON objects
        Pattern dialogPattern = Pattern.compile("\\{[^}]+}");
        Matcher dialogMatcher = dialogPattern.matcher(jsonText);

        while (dialogMatcher.find()) {
            String block = dialogMatcher.group();
            Dialogue d = parseDialogueBlock(block, scene, characters);
            if (d != null) {
                dialogues.add(d);
            }
        }

        return dialogues;
    }

    private Dialogue parseDialogueBlock(String block, Scene scene, List<Character> characters) {
        String speaker = extractJsonField(block, "speaker");
        String contentText = extractJsonField(block, "content");
        if (speaker == null || contentText == null || contentText.isBlank()) {
            return null;
        }

        String emotionStr = extractJsonField(block, "emotion");
        String parenthetical = extractJsonField(block, "parenthetical");
        int replyTo = extractIntField(block, "replyTo", 0);

        Emotion emotion = Emotion.fromLabel(emotionStr);

        // Resolve character ID
        Long characterId = null;
        if (characters != null) {
            for (Character c : characters) {
                if (c.getCanonicalName().equals(speaker)) {
                    characterId = c.getId();
                    break;
                }
                if (c.getAliases() != null && c.getAliases().contains(speaker)) {
                    characterId = c.getId();
                    break;
                }
            }
        }

        return Dialogue.builder()
                .sceneId(scene.getId())
                .characterId(characterId)
                .speaker(speaker)
                .content(contentText)
                .emotion(emotion)
                .parenthetical(parenthetical != null && !"null".equals(parenthetical) ? parenthetical : null)
                .replyTo(replyTo > 0 ? (long) replyTo : null)
                .build();
    }

    // ── Helpers ─────────────────────────────────────────

    private List<Character> filterPresentCharacters(Scene scene, List<Character> characters) {
        if (characters == null) return List.of();
        if (scene.getCharacterIds() == null || scene.getCharacterIds().isEmpty()) {
            return characters; // return all if no filter specified
        }
        return characters.stream()
                .filter(c -> scene.getCharacterIds().contains(c.getId()))
                .collect(Collectors.toList());
    }

    private void assignCharacterIds(List<Dialogue> dialogues, List<Character> characters) {
        Map<String, Long> nameToId = new HashMap<>();
        if (characters != null) {
            for (Character c : characters) {
                nameToId.put(c.getCanonicalName(), c.getId());
                if (c.getAliases() != null) {
                    for (String alias : c.getAliases()) {
                        nameToId.put(alias, c.getId());
                    }
                }
            }
        }
        for (Dialogue d : dialogues) {
            if (d.getCharacterId() == null && d.getSpeaker() != null) {
                d.setCharacterId(nameToId.get(d.getSpeaker()));
            }
        }
    }

    // ── JSON Helpers ─────────────────────────────────────

    /**
     * Extract a balanced JSON array from AI response text.
     * Strips markdown fences and uses bracket-depth tracking.
     */
    private String extractJsonArray(String text) {
        // Strip markdown code fences
        String cleaned = text
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        int start = cleaned.indexOf('[');
        if (start == -1) return null;

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return cleaned.substring(start, i + 1);
            }
        }
        return null;
    }

    private String extractJsonObject(String text) {
        // Strip markdown fences
        String cleaned = text
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        int start = cleaned.indexOf('{');
        if (start == -1) return null;

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return cleaned.substring(start, i + 1);
            }
        }
        return null;
    }

    /**
     * Extract a JSON field value from a JSON object block.
     * Handles quoted strings, unquoted values (numbers/bool/null), and escaped quotes.
     */
    private String extractJsonField(String block, String fieldName) {
        // Find the field key position
        String keyPattern = "\"" + fieldName + "\"";
        int keyIdx = block.indexOf(keyPattern);
        if (keyIdx == -1) return null;

        // Find the colon after the key
        int colonIdx = block.indexOf(':', keyIdx + keyPattern.length());
        if (colonIdx == -1) return null;

        // Skip whitespace after colon
        int valStart = colonIdx + 1;
        while (valStart < block.length() && java.lang.Character.isWhitespace(block.charAt(valStart))) {
            valStart++;
        }
        if (valStart >= block.length()) return null;

        char firstChar = block.charAt(valStart);

        // Quoted string value
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
            return sb.toString(); // Unterminated string — return what we have
        }

        // Unquoted value (number, true, false, null)
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

    /**
     * Log token usage from ChatResponse metadata.
     */
    private void logTokenUsage(ChatResponse response, String prompt, String taskName) {
        try {
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                long promptTokens = usage.getPromptTokens();
                long completionTokens = usage.getCompletionTokens();
                long totalTokens = usage.getTotalTokens();
                String model = response.getMetadata().getModel() != null
                        ? response.getMetadata().getModel() : "unknown";

                log.info("📊 Token usage [{}] model={}: prompt={} completion={} total={} | prompt_chars={}",
                        taskName, model, promptTokens, completionTokens, totalTokens,
                        prompt != null ? prompt.length() : 0);
            } else {
                log.debug("📊 Token usage [{}] — no usage metadata in response (prompt_chars={})",
                        taskName, prompt != null ? prompt.length() : 0);
            }
        } catch (Exception e) {
            log.debug("Failed to extract token usage for [{}]: {}", taskName, e.getMessage());
        }
    }
}
