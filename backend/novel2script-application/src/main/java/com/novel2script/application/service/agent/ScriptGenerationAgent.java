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

import com.novel2script.infrastructure.config.AiModelRouter;
import com.novel2script.infrastructure.prompt.PromptRegistry;
import com.novel2script.infrastructure.prompt.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
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
 *
 * <p><b>v2.0 (staged mode):</b> generates character + scene outlines first,
 * then delegates dialogue/action generation per scene to specialized agents.
 * Uses higher max_tokens and truncated chapter content for reliability.
 */
@Slf4j
@Service
public class ScriptGenerationAgent {

    private static final String PROMPT_NAME = "script-generation";

    private final PromptRegistry promptRegistry;
    private final AiModelRouter modelRouter;
    private final ObjectMapper objectMapper;
    private final Map<String, ChatClient> streamingChatClients;

    /** Max output tokens for single-pass generation (default 16384). */
    private final int maxTokens;

    /** Temperature for single-pass generation (default 0.3, lower for JSON stability). */
    private final double temperature;

    /** Max characters per chapter in prompt (0 = no truncation). */
    private final int chapterTruncateChars;

    public ScriptGenerationAgent(PromptRegistry promptRegistry,
                                  AiModelRouter modelRouter,
                                  ObjectMapper objectMapper,
                                  Map<String, ChatClient> streamingChatClients,
                                  @org.springframework.beans.factory.annotation.Value("${script.generation.single-pass.max-tokens:16384}") int maxTokens,
                                  @org.springframework.beans.factory.annotation.Value("${script.generation.single-pass.temperature:0.3}") double temperature,
                                  @org.springframework.beans.factory.annotation.Value("${script.generation.single-pass.chapter-truncate-chars:2000}") int chapterTruncateChars) {
        this.promptRegistry = promptRegistry;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
        this.streamingChatClients = streamingChatClients;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.chapterTruncateChars = chapterTruncateChars;
        log.info("ScriptGenerationAgent: maxTokens={}, temperature={}, chapterTruncateChars={}",
                maxTokens, temperature, chapterTruncateChars);
    }

    /**
     * Convert novel chapters into a complete script in a single AI call.
     *
     * @param chapters the novel chapters to convert
     * @param novelTitle the novel's title for context
     * @param scriptId script ID for ID generation
     * @return a fully populated Script, or null if AI fails
     */
    public Script generate(List<Chapter> chapters, String novelTitle, Long scriptId) {
        return generateOutline(chapters, novelTitle, scriptId, false);
    }

    /**
     * Generate character + scene outlines from chapter content (v2.0 staged mode).
     * <p>
     * This is the primary entry point when {@code script.generation.single-pass.staged=true}.
     * It produces a Script with characters and scenes populated (but without dialogues/actions),
     * using chapter summaries instead of full content to reduce prompt size.
     *
     * @param chapters the novel chapters
     * @param novelTitle the novel's title for context
     * @param scriptId script ID for ID generation
     * @param savePartial whether to attempt partial save on failure (via callback)
     * @return a Script with characters + scene outlines, or null if AI fails
     */
    public Script generateOutline(List<Chapter> chapters, String novelTitle, Long scriptId, boolean savePartial) {
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
        String prompt = buildPromptWithTruncation(template, chapters, novelTitle, chapterTruncateChars);

        int totalChars = chapters.stream()
                .mapToInt(c -> c.getContent() != null ? c.getContent().length() : 0).sum();
        int truncatedChars = chapters.stream()
                .mapToInt(c -> {
                    String content = c.getContent();
                    if (content == null) return 0;
                    return Math.min(content.length(), chapterTruncateChars > 0 ? chapterTruncateChars : content.length());
                }).sum();
        log.info("ScriptGenerationAgent v2.0: sending {} chapters ({} chars total, {} chars after truncation={}) to model={}",
                chapters.size(), totalChars, truncatedChars, chapterTruncateChars, model);

        try {
            // ── Try streaming first (SSE) to avoid read-timeout on long generations ──
            String responseText = callWithStreaming(model, prompt, maxTokens, temperature);

            log.info("ScriptGenerationAgent: received response ({} chars)", responseText.length());

            Script script = parseScriptResponse(responseText, scriptId);
            if (script != null) {
                log.info("ScriptGenerationAgent v2.0: ✅ parsed {} characters, {} scene outlines",
                        script.getCharacters().size(),
                        script.getScenes().size());
            } else if (savePartial) {
                log.warn("ScriptGenerationAgent: outline generation failed, partial save requested");
            }
            return script;
        } catch (Exception e) {
            log.error("ScriptGenerationAgent: AI call failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildPrompt(PromptTemplate template, List<Chapter> chapters, String novelTitle) {
        return buildPromptWithTruncation(template, chapters, novelTitle, 0);
    }

    /**
     * Build the user prompt with optional chapter content truncation.
     *
     * @param template the prompt template
     * @param chapters the novel chapters
     * @param novelTitle the novel's title
     * @param truncateChars max characters per chapter (0 = full content, no truncation)
     * @return rendered prompt string
     */
    private String buildPromptWithTruncation(PromptTemplate template, List<Chapter> chapters,
                                              String novelTitle, int truncateChars) {
        List<Map<String, Object>> chapterList = new ArrayList<>();
        for (Chapter ch : chapters) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("chapterNumber", ch.getChapterNumber());
            cm.put("title", ch.getTitle() != null ? ch.getTitle() : "");

            String content = ch.getContent() != null ? ch.getContent() : "";
            if (truncateChars > 0 && content.length() > truncateChars) {
                // Truncate and add ellipsis marker
                content = content.substring(0, truncateChars) + "\n\n[… 后续内容已截断，请基于以上摘要生成 …]";
                log.debug("Chapter {} truncated: {} → {} chars", ch.getChapterNumber(),
                        ch.getContent().length(), content.length());
            }
            cm.put("content", content);
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
     * Handles multiple response formats the model might produce:
     * <ol>
     *   <li>{@code {"script": {...}}} — standard format</li>
     *   <li>{@code {...}} with script-like keys (characters, scenes) at root</li>
     *   <li>Alternative wrapper keys: {@code output}, {@code result}, {@code content}</li>
     *   <li>Markdown-fenced JSON with explanatory text</li>
     *   <li>Pure text (no JSON) — logs warning, returns null for graceful degradation</li>
     * </ol>
     */
    private Script parseScriptResponse(String responseText, Long scriptId) {
        // ── Log raw response for diagnosis ──
        log.debug("ScriptGenerationAgent: raw response (first 800 chars):\n{}",
                responseText.length() > 800 ? responseText.substring(0, 800) + "…" : responseText);

        String json = CharacterAgent.extractJson(responseText);
        if (json == null) {
            // No fabrication: a plain-text response is NOT a script. Previously this
            // fabricated a placeholder character ("AI_Response") plus a pseudo scene,
            // which then leaked into the saved script. Return null instead so the
            // orchestrator falls back to the real multi-step pipeline.
            log.warn("ScriptGenerationAgent: no JSON found in AI response — "
                    + "response may be plain text. Returning null (no fabrication); "
                    + "orchestrator will fall back to the multi-step pipeline. "
                    + "First 300 chars: {}",
                    responseText.length() > 300 ? responseText.substring(0, 300) + "…" : responseText);
            return null;
        }

        log.debug("ScriptGenerationAgent: extracted JSON block ({} chars)", json.length());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            // ── Strategy 1: Look for "script" wrapper key ──
            @SuppressWarnings("unchecked")
            Map<String, Object> scriptData = (Map<String, Object>) root.get("script");
            if (scriptData != null && !scriptData.isEmpty()) {
                return buildScript(scriptData, scriptId);
            }

            // ── Strategy 2: Try alternative wrapper keys ──
            for (String altKey : List.of("output", "result", "content", "data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> altData = (Map<String, Object>) root.get(altKey);
                if (altData != null && !altData.isEmpty()) {
                    log.info("ScriptGenerationAgent: using alternative key '{}' as script data", altKey);
                    return buildScript(altData, scriptId);
                }
            }

            // ── Strategy 3: Root itself looks like script data ──
            // If root has "characters" or "scenes" key, treat it as the script
            if (root.containsKey("characters") || root.containsKey("scenes")
                    || root.containsKey("title")) {
                log.info("ScriptGenerationAgent: root object has script-like keys, using as script data");
                return buildScript(root, scriptId);
            }

            // ── Strategy 4: Try nested alternatives ──
            // Some models wrap in extra layers: {"choices": [{"message": {"content": "{\"script\":...}"}}]}
            if (root.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) root.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    if (message != null) {
                        Object contentObj = message.get("content");
                        if (contentObj instanceof String contentStr) {
                            log.info("ScriptGenerationAgent: found nested choices[0].message.content, re-parsing");
                            return parseScriptResponse(contentStr, scriptId); // recursive
                        }
                    }
                }
            }

            log.warn("ScriptGenerationAgent: JSON parsed but no recognizable script structure found. "
                    + "Root keys: {}", root.keySet());
            return null;

        } catch (Exception e) {
            log.error("ScriptGenerationAgent: JSON parse failed: {}", e.getMessage());
            log.debug("  Failed JSON (first 500 chars): {}",
                    json.length() > 500 ? json.substring(0, 500) + "..." : json);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Script buildScript(Map<String, Object> data, Long scriptId) {
        String title = (String) data.getOrDefault("title", "未命名剧本");

        // Build characters — may be at top level OR nested inside scenes
        AtomicLong charIdSeq = new AtomicLong(scriptId * 1000);
        Map<String, Character> charNameMap = new LinkedHashMap<>();

        List<Map<String, Object>> charList = (List<Map<String, Object>>) data.get("characters");
        if (charList != null) {
            for (Map<String, Object> cd : charList) {
                Character c = parseCharacter(cd, charIdSeq.getAndIncrement(), scriptId);
                charNameMap.put(c.getCanonicalName(), c);
            }
        }

        // Build scenes
        AtomicLong sceneIdSeq = new AtomicLong(scriptId * 1000 + 100);
        AtomicLong dialogueIdSeq = new AtomicLong(1);
        AtomicLong actionIdSeq = new AtomicLong(10000);
        List<Scene> scenes = new ArrayList<>();
        int totalDialogues = 0;

        List<Map<String, Object>> sceneList = (List<Map<String, Object>>) data.get("scenes");
        if (sceneList != null) {
            log.info("ScriptGenerationAgent: found {} scenes in response", sceneList.size());
            for (Map<String, Object> sd : sceneList) {
                Scene scene = new Scene();
                scene.setId(sceneIdSeq.getAndIncrement());
                scene.setScriptId(scriptId);

                // ── Flexible scene number: sceneNumber / scene_id / id / index ──
                int sceneNum = scenes.size() + 1;
                if (sd.containsKey("sceneNumber")) sceneNum = getInt(sd, "sceneNumber", sceneNum);
                else if (sd.containsKey("scene_id")) {
                    String sid = String.valueOf(sd.get("scene_id"));
                    sceneNum = sid.replaceAll("[^0-9]", "").isEmpty()
                            ? sceneNum : Integer.parseInt(sid.replaceAll("[^0-9]", ""));
                } else if (sd.containsKey("id")) sceneNum = getInt(sd, "id", sceneNum);
                else if (sd.containsKey("index")) sceneNum = getInt(sd, "index", sceneNum);
                scene.setSceneNumber(sceneNum);

                scene.setLocation((String) sd.getOrDefault("location", "未知地点"));
                scene.setTimeOfDay(parseTimeOfDay((String) sd.get("timeOfDay")));
                scene.setInterior(getBoolean(sd, "interior", true));
                scene.setTitle((String) sd.getOrDefault("title", "场景" + sceneNum));

                // ── Flexible summary: summary / narration / description / content ──
                String summary = (String) sd.get("summary");
                if (summary == null) summary = (String) sd.get("narration");
                if (summary == null) summary = (String) sd.get("description");
                if (summary == null) summary = (String) sd.get("content");
                if (summary == null) summary = (String) sd.getOrDefault("title", "");
                scene.setSummary(summary);

                scene.setMood((String) sd.getOrDefault("mood", "中性"));
                scene.setSceneHeading(scene.getSceneHeader());
                scene.setSourceReason(SourceReason.CHAPTER_BOUNDARY);
                scene.setChapterIds(new ArrayList<>());
                scene.setCreatedAt(LocalDateTime.now());

                // ── Per-scene characters (models may nest characters inside scenes) ──
                List<Map<String, Object>> sceneChars = (List<Map<String, Object>>) sd.get("characters");
                if (sceneChars != null) {
                    for (Map<String, Object> sc : sceneChars) {
                        String name = (String) sc.getOrDefault("name",
                                sc.getOrDefault("canonicalName", "未知"));
                        if (!charNameMap.containsKey(name)) {
                            Character c = parseCharacter(sc, charIdSeq.getAndIncrement(), scriptId);
                            charNameMap.put(name, c);
                        }
                    }
                }

                // ── Dialogues: dialogues / lines / conversations ──
                List<Dialogue> dialogues = new ArrayList<>();
                List<Map<String, Object>> diaList = (List<Map<String, Object>>) sd.get("dialogues");
                if (diaList == null) diaList = (List<Map<String, Object>>) sd.get("lines");
                if (diaList == null) diaList = (List<Map<String, Object>>) sd.get("conversations");
                if (diaList != null) {
                    for (int di = 0; di < diaList.size(); di++) {
                        Map<String, Object> dd = diaList.get(di);
                        Dialogue d = new Dialogue();
                        d.setId(dialogueIdSeq.getAndIncrement());
                        d.setSceneId(scene.getId());
                        d.setSequence(di + 1);
                        d.setSpeaker((String) dd.getOrDefault("speaker",
                                dd.getOrDefault("character", "未知")));
                        d.setContent((String) dd.getOrDefault("content",
                                dd.getOrDefault("line", "")));
                        d.setEmotion(parseEmotion((String) dd.get("emotion")));
                        d.setCreatedAt(LocalDateTime.now());

                        // Map speaker name to character ID
                        for (Character c : charNameMap.values()) {
                            if (d.getSpeaker().equals(c.getCanonicalName())
                                    || (c.getAliases() != null && c.getAliases().contains(d.getSpeaker()))) {
                                d.setCharacterId(c.getId());
                                break;
                            }
                        }
                        dialogues.add(d);
                    }
                    totalDialogues += dialogues.size();
                }
                scene.setDialogues(dialogues);

                // ── Actions ──
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

                // Map character names appearing in dialogues to scene character IDs
                List<Long> charIds = new ArrayList<>();
                for (Character c : charNameMap.values()) {
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

        List<Character> characters = new ArrayList<>(charNameMap.values());

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

    /** Parse a single character from a JSON map, handling multiple field name conventions. */
    private Character parseCharacter(Map<String, Object> cd, long id, Long scriptId) {
        Character c = new Character();
        c.setId(id);
        c.setScriptId(scriptId);
        // Flexible name: canonicalName / name
        c.setCanonicalName((String) cd.getOrDefault("canonicalName",
                cd.getOrDefault("name", "未知")));
        // Flexible role: roleType / role / type
        String roleStr = (String) cd.get("roleType");
        if (roleStr == null) roleStr = (String) cd.get("role");
        if (roleStr == null) roleStr = (String) cd.get("type");
        c.setRoleType(parseRoleType(roleStr));
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
        return c;
    }

    private CharacterRoleType parseRoleType(String s) {
        if (s == null) return CharacterRoleType.SUPPORTING;
        return switch (s.toUpperCase()) {
            case "PROTAGONIST" -> CharacterRoleType.PROTAGONIST;
            case "DEUTERAGONIST" -> CharacterRoleType.DEUTERAGONIST;
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
     * Call the AI model with streaming (SSE) for long-generation tasks.
     * Falls back to blocking call if streaming is not supported.
     *
     * <p>Streaming keeps the HTTP connection alive by sending data incrementally,
     * preventing {@code SocketTimeoutException} during multi-minute generations.
     *
     * @param model  the chat model (may or may not implement {@link StreamingChatModel})
     * @param prompt the rendered user prompt
     * @return the complete AI response text
     */
    private String callWithStreaming(ChatModel model, String prompt) {
        return callWithStreaming(model, prompt, maxTokens, temperature);
    }

    /**
     * Call the AI model with streaming and explicit token/temperature overrides.
     *
     * @param model        the chat model
     * @param prompt       the rendered user prompt
     * @param maxTokens    max output tokens override (0 = use provider default)
     * @param temperature  temperature override (-1 = use provider default)
     * @return the complete AI response text
     */
    private String callWithStreaming(ChatModel model, String prompt, int maxTokens, double temperature) {
        // Attempt streaming via ChatClient (uses SSE under the hood)
        String provider = TaskType.SCRIPT_COMPOSE.getDefaultProvider();
        ChatClient streamingClient = streamingChatClients.get(provider);

        if (streamingClient != null) {
            try {
                log.info("ScriptGenerationAgent: using SSE streaming (maxTokens={}, temperature={})",
                        maxTokens, temperature);
                StringBuilder fullResponse = new StringBuilder();

                // Build the prompt request with overridden options
                var promptBuilder = streamingClient.prompt().user(prompt);

                // Apply max_tokens and temperature overrides if specified
                if (maxTokens > 0 || temperature >= 0) {
                    var optionsBuilder = OpenAiChatOptions.builder();
                    if (maxTokens > 0) optionsBuilder.maxTokens(maxTokens);
                    if (temperature >= 0) optionsBuilder.temperature(temperature);
                    promptBuilder.options(optionsBuilder.build());
                }

                Flux<ChatResponse> stream = promptBuilder
                        .stream()
                        .chatResponse();

                // Collect all SSE chunks with a 180s timeout (longer for v2.0 with higher maxTokens)
                int timeoutSeconds = maxTokens > 8192 ? 180 : 120;
                List<ChatResponse> chunks = stream
                        .collectList()
                        .block(Duration.ofSeconds(timeoutSeconds));

                if (chunks != null && !chunks.isEmpty()) {
                    for (ChatResponse chunk : chunks) {
                        if (chunk.getResult() != null
                                && chunk.getResult().getOutput() != null
                                && chunk.getResult().getOutput().getText() != null) {
                            fullResponse.append(chunk.getResult().getOutput().getText());
                        }
                    }
                    String text = fullResponse.toString();
                    if (!text.isBlank()) {
                        log.info("ScriptGenerationAgent: streaming collected {} chunks → {} chars",
                                chunks.size(), text.length());
                        return text;
                    }
                }
                log.warn("ScriptGenerationAgent: streaming returned empty, falling back to blocking call");
            } catch (Exception e) {
                log.warn("ScriptGenerationAgent: streaming failed ({}), falling back to blocking call",
                        e.getMessage());
            }
        }

        // ── Blocking fallback (uses increased 120s readTimeout) ──
        log.info("ScriptGenerationAgent: using blocking call (readTimeout=120s)");
        try {
            ChatResponse response = model.call(
                    new org.springframework.ai.chat.prompt.Prompt(
                            new org.springframework.ai.chat.messages.UserMessage(prompt)));

            logTokenUsage(response, prompt);
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("ScriptGenerationAgent: blocking call also failed: {}", e.getMessage());
            throw new RuntimeException("AI generation failed: " + e.getMessage(), e);
        }
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
