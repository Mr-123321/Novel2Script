package com.novel2script.application.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.common.enums.*;
import com.novel2script.domain.model.Action;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.model.Scene;
import com.novel2script.domain.model.Script;
import com.novel2script.domain.model.Character;
import com.novel2script.infrastructure.annotation.AiMonitored;
import com.novel2script.infrastructure.config.AiModelRouter;
import com.novel2script.infrastructure.prompt.PromptRegistry;
import com.novel2script.infrastructure.prompt.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Single-pass AI agent that converts narrative text directly into a complete
 * script JSON. Replaces the multi-step pipeline (character → plot → scene →
 * dialogue → action → compose) with one AI call.
 *
 * <p><b>Core principle:</b> only extract what's actually in the text.
 * No fabrication, no hallucination, no placeholder data.
 */
@Slf4j
@Service
public class ScriptGenerationAgent {

    private static final String PROMPT_NAME = "script-generation";

    private final PromptRegistry promptRegistry;
    private final AiModelRouter modelRouter;
    private final ObjectMapper objectMapper;

    public ScriptGenerationAgent(PromptRegistry promptRegistry,
                                  AiModelRouter modelRouter,
                                  ObjectMapper objectMapper) {
        this.promptRegistry = promptRegistry;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
    }

    /**
     * Convert novel chapters into a complete script in a single AI call.
     *
     * @param chapters the novel chapters to convert
     * @param novelTitle the novel's title for context
     * @param scriptId script ID for ID generation
     * @return a fully populated Script, or null if AI fails
     */
    @AiMonitored(value = "script-generation", version = "1.0")
    public Script generate(List<Chapter> chapters, String novelTitle, Long scriptId) {
        if (chapters == null || chapters.isEmpty()) {
            log.warn("ScriptGenerationAgent: no chapters provided");
            return null;
        }

        PromptTemplate template = promptRegistry.getLatest(PROMPT_NAME);
        if (template == null) {
            log.error("Prompt template '{}' not found", PROMPT_NAME);
            return null;
        }

        ChatModel model = modelRouter.route(TaskType.SCRIPT_COMPOSE);
        String prompt = buildPrompt(template, chapters, novelTitle);

        log.info("ScriptGenerationAgent: sending {} chapters ({} chars) to model={}",
                chapters.size(),
                chapters.stream().mapToInt(c -> c.getContent() != null ? c.getContent().length() : 0).sum(),
                model);

        try {
            ChatResponse response = model.call(
                    new org.springframework.ai.chat.prompt.Prompt(
                            new org.springframework.ai.chat.messages.UserMessage(prompt)));

            // ── Log token usage ──
            logTokenUsage(response, prompt);

            String responseText = response.getResult().getOutput().getText();
            log.info("ScriptGenerationAgent: received response ({} chars)", responseText.length());

            Script script = parseScriptResponse(responseText, scriptId);
            if (script != null) {
                log.info("ScriptGenerationAgent: ✅ parsed {} characters, {} scenes, {} dialogues",
                        script.getCharacters().size(),
                        script.getScenes().size(),
                        script.getDialogueCount());
            }
            return script;
        } catch (Exception e) {
            log.error("ScriptGenerationAgent: AI call failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildPrompt(PromptTemplate template, List<Chapter> chapters, String novelTitle) {
        List<Map<String, Object>> chapterList = new ArrayList<>();
        for (Chapter ch : chapters) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("chapterNumber", ch.getChapterNumber());
            cm.put("title", ch.getTitle() != null ? ch.getTitle() : "");
            cm.put("content", ch.getContent() != null ? ch.getContent() : "");
            chapterList.add(cm);
        }

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("chapters", chapterList);
        if (novelTitle != null && !novelTitle.isBlank()) {
            vars.put("novelTitle", novelTitle);
        }

        return template.renderUserTemplate(vars);
    }

    /**
     * Parse the AI response into a Script domain object.
     * Handles both markdown-wrapped JSON and bare JSON.
     */
    private Script parseScriptResponse(String responseText, Long scriptId) {
        String json = CharacterAgent.extractJson(responseText);
        if (json == null) {
            log.warn("ScriptGenerationAgent: no JSON found in response");
            return null;
        }

        try {
            Map<String, Object> root = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            @SuppressWarnings("unchecked")
            Map<String, Object> scriptData = (Map<String, Object>) root.get("script");
            if (scriptData == null) {
                log.warn("ScriptGenerationAgent: no 'script' key in response");
                return null;
            }

            return buildScript(scriptData, scriptId);
        } catch (Exception e) {
            log.error("ScriptGenerationAgent: JSON parse failed: {}", e.getMessage());
            log.debug("  Response (first 500 chars): {}",
                    responseText.length() > 500 ? responseText.substring(0, 500) + "..." : responseText);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Script buildScript(Map<String, Object> data, Long scriptId) {
        String title = (String) data.getOrDefault("title", "未命名剧本");

        // Build characters
        AtomicLong charIdSeq = new AtomicLong(scriptId * 1000);
        List<Character> characters = new ArrayList<>();
        List<Map<String, Object>> charList = (List<Map<String, Object>>) data.get("characters");
        if (charList != null) {
            for (Map<String, Object> cd : charList) {
                Character c = new Character();
                c.setId(charIdSeq.getAndIncrement());
                c.setScriptId(scriptId);
                c.setCanonicalName((String) cd.getOrDefault("canonicalName", "未知"));
                c.setRoleType(parseRoleType((String) cd.get("roleType")));
                c.setGender((String) cd.getOrDefault("gender", "未知"));
                c.setDescription((String) cd.getOrDefault("description", ""));
                c.setPersonality((List<String>) cd.getOrDefault("personality", List.of("未知")));
                c.setAliases(new ArrayList<>());
                c.setResolved(true);
                c.setCreatedAt(LocalDateTime.now());

                // Parse relationships
                List<Map<String, Object>> rels = (List<Map<String, Object>>) cd.get("relationships");
                if (rels != null && !rels.isEmpty()) {
                    List<Character.Relationship> relationships = new ArrayList<>();
                    for (Map<String, Object> r : rels) {
                        Character.Relationship rel = new Character.Relationship();
                        rel.setTarget((String) r.get("target"));
                        rel.setRelation((String) r.get("relation"));
                        relationships.add(rel);
                    }
                    c.setRelationships(relationships);
                } else {
                    c.setRelationships(new ArrayList<>());
                }
                characters.add(c);
            }
        }

        // Build scenes with dialogues and actions
        AtomicLong sceneIdSeq = new AtomicLong(scriptId * 1000 + 100);
        AtomicLong dialogueIdSeq = new AtomicLong(1);
        AtomicLong actionIdSeq = new AtomicLong(10000);
        List<Scene> scenes = new ArrayList<>();
        int totalDialogues = 0;

        List<Map<String, Object>> sceneList = (List<Map<String, Object>>) data.get("scenes");
        if (sceneList != null) {
            for (Map<String, Object> sd : sceneList) {
                Scene scene = new Scene();
                scene.setId(sceneIdSeq.getAndIncrement());
                scene.setScriptId(scriptId);
                scene.setSceneNumber(getInt(sd, "sceneNumber", scenes.size() + 1));
                scene.setLocation((String) sd.getOrDefault("location", "未知地点"));
                scene.setTimeOfDay(parseTimeOfDay((String) sd.get("timeOfDay")));
                scene.setInterior(getBoolean(sd, "interior", true));
                scene.setTitle((String) sd.getOrDefault("title", "场景" + scene.getSceneNumber()));
                scene.setSummary((String) sd.getOrDefault("summary",
                        (String) sd.getOrDefault("title", "")));
                scene.setMood((String) sd.getOrDefault("mood", "中性"));
                scene.setSceneHeading(scene.getSceneHeader());
                scene.setSourceReason(SourceReason.CHAPTER_BOUNDARY);
                scene.setChapterIds(new ArrayList<>());
                scene.setCreatedAt(LocalDateTime.now());

                // Dialogues
                List<Dialogue> dialogues = new ArrayList<>();
                List<Map<String, Object>> diaList = (List<Map<String, Object>>) sd.get("dialogues");
                if (diaList != null) {
                    for (int di = 0; di < diaList.size(); di++) {
                        Map<String, Object> dd = diaList.get(di);
                        Dialogue d = new Dialogue();
                        d.setId(dialogueIdSeq.getAndIncrement());
                        d.setSceneId(scene.getId());
                        d.setSequence(di + 1);
                        d.setSpeaker((String) dd.getOrDefault("speaker", "未知"));
                        d.setContent((String) dd.getOrDefault("content", ""));
                        d.setEmotion(parseEmotion((String) dd.get("emotion")));
                        d.setCreatedAt(LocalDateTime.now());

                        // Map speaker name to character ID
                        for (Character c : characters) {
                            if (d.getSpeaker().equals(c.getCanonicalName())) {
                                d.setCharacterId(c.getId());
                                break;
                            }
                        }
                        dialogues.add(d);
                    }
                    totalDialogues += dialogues.size();
                }
                scene.setDialogues(dialogues);

                // Actions
                List<Action> actions = new ArrayList<>();
                List<Map<String, Object>> actList = (List<Map<String, Object>>) sd.get("actions");
                if (actList != null) {
                    for (int ai = 0; ai < actList.size(); ai++) {
                        Map<String, Object> ad = actList.get(ai);
                        Action a = new Action();
                        a.setId(actionIdSeq.getAndIncrement());
                        a.setSceneId(scene.getId());
                        a.setSequence(ai + 1);
                        a.setActionType((String) ad.getOrDefault("actionType", "ACTION"));
                        a.setDescription((String) ad.getOrDefault("description", ""));
                        a.setCreatedAt(LocalDateTime.now());
                        actions.add(a);
                    }
                }
                scene.setActions(actions);

                // Map character names to IDs
                List<Long> charIds = new ArrayList<>();
                for (Character c : characters) {
                    // Check if character appears in dialogues or if name appears in scene context
                    boolean inScene = scene.getDialogues().stream()
                            .anyMatch(d -> c.getCanonicalName().equals(d.getSpeaker()));
                    if (inScene && !charIds.contains(c.getId())) {
                        charIds.add(c.getId());
                    }
                }
                scene.setCharacterIds(charIds);

                scenes.add(scene);
            }
        }

        Script script = new Script();
        script.setId(scriptId);
        script.setTitle(title);
        script.setCharacters(characters);
        script.setScenes(scenes);
        script.setPlotEvents(new ArrayList<>());
        script.setCharacterCount(characters.size());
        script.setSceneCount(scenes.size());
        script.setDialogueCount(totalDialogues);
        script.setVersion(1);
        return script;
    }

    private CharacterRoleType parseRoleType(String s) {
        if (s == null) return CharacterRoleType.SUPPORTING;
        return switch (s.toUpperCase()) {
            case "PROTAGONIST" -> CharacterRoleType.PROTAGONIST;
            case "ANTAGONIST" -> CharacterRoleType.ANTAGONIST;
            case "EXTRAS", "MINOR" -> CharacterRoleType.MINOR;
            default -> CharacterRoleType.SUPPORTING;
        };
    }

    private TimeOfDay parseTimeOfDay(String s) {
        if (s == null) return TimeOfDay.UNKNOWN;
        return switch (s.toUpperCase()) {
            case "DAWN" -> TimeOfDay.DAWN;
            case "MORNING" -> TimeOfDay.MORNING;
            case "AFTERNOON" -> TimeOfDay.AFTERNOON;
            case "EVENING" -> TimeOfDay.EVENING;
            case "NIGHT" -> TimeOfDay.NIGHT;
            case "LATE_NIGHT" -> TimeOfDay.LATE_NIGHT;
            default -> TimeOfDay.UNKNOWN;
        };
    }

    private Emotion parseEmotion(String s) {
        if (s == null) return Emotion.CALM;
        return switch (s.toUpperCase()) {
            case "ANGRY" -> Emotion.ANGRY;
            case "HAPPY" -> Emotion.HAPPY;
            case "SAD" -> Emotion.SAD;
            case "SURPRISED" -> Emotion.SURPRISED;
            case "UNKNOWN" -> Emotion.CALM;
            default -> Emotion.CALM;
        };
    }

    private int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultVal) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return defaultVal;
    }

    /**
     * Log token usage from ChatResponse metadata.
     */
    private void logTokenUsage(ChatResponse response, String prompt) {
        try {
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                long promptTokens = usage.getPromptTokens();
                long completionTokens = usage.getCompletionTokens();
                long totalTokens = usage.getTotalTokens();
                String model = response.getMetadata().getModel() != null
                        ? response.getMetadata().getModel() : "unknown";

                log.info("📊 Token usage [script-generation] model={}: prompt={} completion={} total={} | prompt_chars={} ratio={:.1f}",
                        model, promptTokens, completionTokens, totalTokens,
                        prompt != null ? prompt.length() : 0,
                        prompt != null && promptTokens > 0 ? (double) prompt.length() / promptTokens : 0);
            }
        } catch (Exception e) {
            log.debug("Failed to extract token usage: {}", e.getMessage());
        }
    }
}
