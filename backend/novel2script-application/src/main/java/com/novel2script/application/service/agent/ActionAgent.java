package com.novel2script.application.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.application.service.agent.model.ActionDialogueSequence;
import com.novel2script.common.enums.ActionType;
import com.novel2script.common.enums.CharacterRoleType;
import com.novel2script.common.enums.TaskType;
import com.novel2script.domain.model.Action;
import com.novel2script.domain.model.Character;
import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.model.Scene;
import com.novel2script.infrastructure.annotation.AiMonitored;
import com.novel2script.infrastructure.config.AiModelRouter;
import com.novel2script.infrastructure.prompt.PromptRegistry;
import com.novel2script.infrastructure.prompt.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI agent that generates visual, filmable action descriptions for scenes.
 *
 * <h3>Design principles</h3>
 * <ol>
 *   <li><b>Visualization</b>: All actions must be externally visible — no inner
 *       states ("感到", "意识到"), no abstract narration ("他想起了…").</li>
 *   <li><b>Action type classification</b>: Each action is classified as
 *       {@link ActionType#ACTION}, {@link ActionType#REACTION},
 *       {@link ActionType#BEAT}, or {@link ActionType#BUSINESS}.</li>
 *   <li><b>Dialogue interleaving</b>: Actions and dialogues are merged into a
 *       unified {@link ActionDialogueSequence} timeline via {@link #interleave}.</li>
 *   <li><b>Narration-free</b>: Post-generation validation rejects descriptions
 *       containing forbidden narrative phrases.</li>
 * </ol>
 *
 * <p>Uses {@link AiModelRouter} to select the optimal model
 * (defaults to Qwen for batch action generation via {@link TaskType#ACTION_GENERATE}).
 */
@Slf4j
@Service
public class ActionAgent {

    private static final String PROMPT_NAME = "action-generation";
    private static final int ACTION_MAX_TOKENS = 4096;
    private static final int STREAM_TIMEOUT_SECONDS = 45;

    private final AiModelRouter router;
    private final PromptRegistry promptRegistry;
    private final ObjectMapper objectMapper;
    private final Map<String, ChatClient> streamingChatClients;
    /** Global sequence for assigning unique IDs to parsed actions */
    private final java.util.concurrent.atomic.AtomicLong actionIdSeq = new java.util.concurrent.atomic.AtomicLong(10000);

    public ActionAgent(AiModelRouter router,
                       PromptRegistry promptRegistry,
                       ObjectMapper objectMapper,
                       Map<String, ChatClient> streamingChatClients) {
        this.router = router;
        this.promptRegistry = promptRegistry;
        this.objectMapper = objectMapper;
        this.streamingChatClients = streamingChatClients;
    }

    // ──────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────

    /**
     * Generate all actions for a scene, informed by its dialogues and
     * present characters.
     *
     * @param scene              the scene to generate actions for
     * @param dialogues          dialogues within this scene (for interleaving context)
     * @param presentCharacters  characters appearing in this scene
     * @return list of {@link Action} in chronological order (by sequence)
     */
    @AiMonitored(value = PROMPT_NAME, version = "1.0")
    public List<Action> generate(Scene scene,
                                 List<Dialogue> dialogues,
                                 List<Character> presentCharacters) {
        if (scene == null) {
            log.warn("ActionAgent.generate: null scene");
            return Collections.emptyList();
        }

        // 1. Call AI (with template or inline fallback)
        //    Character names are resolved during parsing + enrichment
        List<Action> actions = aiGenerate(scene, dialogues, presentCharacters);

        // 2. Validate visualization & narration rules
        List<String> violations = validateVisualization(actions);
        if (!violations.isEmpty()) {
            log.warn("ActionAgent: {} visualization violations in scene '{}': {}",
                    violations.size(), scene.getTitle(), violations);
        }

        // 3. Classify any unclassified actions
        for (Action action : actions) {
            if (action.getActionType() == null || action.getActionType().isBlank()) {
                ActionType classified = ActionType.classify(action.getDescription());
                action.setActionType(classified.name());
            }
        }

        log.info("ActionAgent: generated {} actions for scene '{}'", actions.size(), scene.getTitle());
        return actions;
    }

    /**
     * Interleave actions and dialogues into a unified timeline ordered by
     * their respective sequence numbers.
     *
     * <p>This merges the two parallel tracks (visual action + spoken dialogue)
     * into a single chronological sequence suitable for script formatting.
     *
     * @param actions   actions in the scene
     * @param dialogues dialogues in the scene
     * @return merged timeline, sorted by sequence number
     */
    public List<ActionDialogueSequence> interleave(
            List<Action> actions,
            List<Dialogue> dialogues) {

        List<ActionDialogueSequence> timeline = new ArrayList<>();

        if (actions != null) {
            for (Action a : actions) {
                timeline.add(new ActionDialogueSequence.ActionItem(a));
            }
        }
        if (dialogues != null) {
            for (Dialogue d : dialogues) {
                timeline.add(new ActionDialogueSequence.DialogueItem(d));
            }
        }

        // Sort by sequence number
        timeline.sort(Comparator.comparingInt(item -> {
            if (item instanceof ActionDialogueSequence.ActionItem ai) {
                return ai.sequence();
            } else if (item instanceof ActionDialogueSequence.DialogueItem di) {
                return di.sequence();
            }
            return 0;
        }));

        log.debug("ActionAgent.interleave: {} actions + {} dialogues → {} timeline items",
                actions != null ? actions.size() : 0,
                dialogues != null ? dialogues.size() : 0,
                timeline.size());

        return timeline;
    }

    // ──────────────────────────────────────────────────
    //  AI Generation
    // ──────────────────────────────────────────────────

    /**
     * Call the AI model to generate actions for a scene.
     */
    private List<Action> aiGenerate(Scene scene,
                                    List<Dialogue> dialogues,
                                    List<Character> presentCharacters) {
        ChatModel model = router.route(TaskType.ACTION_GENERATE);
        String prompt = buildPrompt(scene, dialogues, presentCharacters);

        log.debug("ActionAgent: generating actions for scene '{}' with model={}",
                scene.getTitle(), model);

        try {
            String content = callWithStreaming(model, prompt, ACTION_MAX_TOKENS);
            Map<Integer, String> seqToCharName = new HashMap<>();
            List<Action> actions = parseActionsWithNames(content, scene.getId(), seqToCharName);
            return enrichActions(actions, scene, presentCharacters, seqToCharName);
        } catch (Exception e) {
            log.error("ActionAgent: AI call failed for scene '{}': {}",
                    scene.getTitle(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Call the AI model with SSE streaming, falling back to blocking.
     */
    private String callWithStreaming(ChatModel model, String promptText, int maxTokens) {
        String provider = TaskType.ACTION_GENERATE.getDefaultProvider();
        ChatClient streamingClient = streamingChatClients.get(provider);

        if (streamingClient != null) {
            try {
                log.debug("ActionAgent: using SSE streaming (maxTokens={})", maxTokens);
                StringBuilder fullResponse = new StringBuilder();

                var promptBuilder = streamingClient.prompt()
                        .user(promptText);
                if (maxTokens > 0) {
                    promptBuilder.options(OpenAiChatOptions.builder()
                            .maxTokens(maxTokens).build());
                }

                Flux<ChatResponse> stream = promptBuilder.stream().chatResponse();
                List<ChatResponse> chunks = stream.collectList()
                        .block(Duration.ofSeconds(STREAM_TIMEOUT_SECONDS));

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
                        log.debug("ActionAgent: streaming collected {} chunks → {} chars",
                                chunks.size(), text.length());
                        return text;
                    }
                }
                log.debug("ActionAgent: streaming returned empty, falling back to blocking");
            } catch (Exception e) {
                log.debug("ActionAgent: streaming failed ({}), falling back to blocking", e.getMessage());
            }
        }

        // ── Blocking fallback ──
        try {
            ChatResponse response = model.call(
                    new Prompt(new org.springframework.ai.chat.messages.UserMessage(promptText)));
            logTokenUsage(response, promptText);
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("ActionAgent: blocking call failed: {}", e.getMessage());
            throw new RuntimeException("Action AI generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Build the prompt using registered template or inline fallback.
     */
    private String buildPrompt(Scene scene,
                               List<Dialogue> dialogues,
                               List<Character> presentCharacters) {
        // Try registered prompt template first
        PromptTemplate template = promptRegistry.getLatest(PROMPT_NAME);
        if (template != null) {
            Map<String, Object> vars = buildTemplateVariables(scene, dialogues, presentCharacters);
            return template.renderUserTemplate(vars);
        }
        return buildInlinePrompt(scene, dialogues, presentCharacters);
    }

    /**
     * Build template variables for Mustache rendering.
     */
    private Map<String, Object> buildTemplateVariables(
            Scene scene,
            List<Dialogue> dialogues,
            List<Character> presentCharacters) {

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("sceneTitle", scene.getTitle() != null ? scene.getTitle() : "");
        vars.put("location", scene.getLocation() != null ? scene.getLocation() : "未知");
        vars.put("timeOfDay", scene.getTimeOfDay() != null
                ? scene.getTimeOfDay().getScriptLabel() : "UNKNOWN");
        vars.put("interiorExterior", scene.isInterior() ? "INT" : "EXT");
        vars.put("mood", scene.getMood() != null ? scene.getMood() : "中性");
        vars.put("summary", scene.getSummary() != null ? scene.getSummary() : "");

        // Characters
        boolean hasCharacters = presentCharacters != null && !presentCharacters.isEmpty();
        vars.put("hasCharacters", hasCharacters);
        if (hasCharacters) {
            List<Map<String, String>> charList = new ArrayList<>();
            for (Character c : presentCharacters) {
                Map<String, String> cm = new LinkedHashMap<>();
                cm.put("name", c.getCanonicalName() != null ? c.getCanonicalName() : "未知");
                cm.put("roleType", c.getRoleType() != null
                        ? c.getRoleType().name() : "UNKNOWN");
                cm.put("description", c.getDescription() != null
                        ? c.getDescription() : "");
                charList.add(cm);
            }
            vars.put("characters", charList);
        }

        // Dialogues
        boolean hasDialogues = dialogues != null && !dialogues.isEmpty();
        vars.put("hasDialogues", hasDialogues);
        if (hasDialogues) {
            List<Map<String, Object>> diaList = new ArrayList<>();
            for (Dialogue d : dialogues) {
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("speaker", d.getSpeaker() != null ? d.getSpeaker() : "");
                dm.put("content", d.getContent() != null ? d.getContent() : "");
                dm.put("emotion", d.getEmotion() != null
                        ? d.getEmotion().getChineseLabel() : "");
                diaList.add(dm);
            }
            vars.put("dialogues", diaList);
        }

        return vars;
    }

    /**
     * Inline fallback prompt when no YAML template is registered.
     */
    private String buildInlinePrompt(Scene scene,
                                     List<Dialogue> dialogues,
                                     List<Character> presentCharacters) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                你是一位专业的影视动作指导。请为以下场景生成可视化、可拍摄的动作描述。

                ## 核心规则

                1. **可视化**: 所有动作必须可被摄影机拍摄——只描述外部可见的行为。
                   禁止: "感到"、"意识到"、"想起了"、"知道"、"觉得"
                   允许: "握紧拳头"、"擦了擦眼角"、"后退半步"、"目光交汇"

                2. **避免旁白**: 禁止"他想起了"、"她意识到"、"他们知道"、"这时"、"此刻"等叙事性描述

                3. **动作类型**: 每个动作标注类型
                   - ACTION: 主动动作（推/拉/踢/跑/拿/走/站/坐）
                   - REACTION: 反应（回头/抬头/后退/惊退/躲避）
                   - BEAT: 节拍（沉默/停顿/犹豫/停下/愣住）
                   - BUSINESS: 背景动作（环境/路人/氛围）

                4. **格式**: 15-40字/动作，简洁有力，使用影视术语

                """);

        sb.append("## 场景信息\n\n");
        sb.append("- 标题: ").append(scene.getTitle() != null ? scene.getTitle() : "无").append("\n");
        sb.append("- 地点: ").append(scene.getLocation() != null ? scene.getLocation() : "未知").append("\n");
        sb.append("- 时间: ").append(scene.getTimeOfDay() != null
                ? scene.getTimeOfDay().getScriptLabel() : "UNKNOWN").append("\n");
        sb.append("- 景别: ").append(scene.isInterior() ? "INT" : "EXT").append("\n");
        sb.append("- 氛围: ").append(scene.getMood() != null ? scene.getMood() : "中性").append("\n");
        sb.append("- 概要: ").append(scene.getSummary() != null ? scene.getSummary() : "").append("\n\n");

        if (presentCharacters != null && !presentCharacters.isEmpty()) {
            sb.append("## 出场角色\n\n");
            for (Character c : presentCharacters) {
                sb.append("- **").append(c.getCanonicalName()).append("** (")
                        .append(c.getRoleType() != null ? c.getRoleType().name() : "UNKNOWN")
                        .append(")");
                if (c.getDescription() != null && !c.getDescription().isBlank()) {
                    sb.append(": ").append(c.getDescription());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (dialogues != null && !dialogues.isEmpty()) {
            sb.append("## 对话\n\n");
            for (Dialogue d : dialogues) {
                sb.append("- **").append(d.getSpeaker()).append("**: ").append(d.getContent());
                if (d.getEmotion() != null) {
                    sb.append(" [").append(d.getEmotion().getChineseLabel()).append("]");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("""
                ## 输出格式

                以 JSON 格式输出：

                ```json
                {
                  "actions": [
                    {"sequence": 1, "character": "角色名", "actionType": "ACTION", "description": "动作描述", "durationMs": 1500},
                    {"sequence": 2, "character": null, "actionType": "BUSINESS", "description": "环境动作描述", "durationMs": 2000}
                  ]
                }
                ```

                character 为 null 表示环境/背景动作。
                请确保输出是有效的 JSON。
                """);

        return sb.toString();
    }

    // ──────────────────────────────────────────────────
    //  Response Parsing
    // ──────────────────────────────────────────────────

    /**
     * Parse the AI response text into a list of {@link Action} objects.
     */
    List<Action> parseResponse(ChatResponse response, Long sceneId) {
        String content = response.getResult().getOutput().getText();
        return parseActionsFromJson(content, sceneId);
    }

    /**
     * Parse action list from JSON text (exposed for testing).
     * Character names are not resolved — use {@link #parseActionsWithNames}
     * when character resolution is needed.
     */
    List<Action> parseActionsFromJson(String jsonText, Long sceneId) {
        return parseActionsWithNames(jsonText, sceneId, new HashMap<>());
    }

    /**
     * Parse actions from JSON and record character names for later resolution.
     */
    @SuppressWarnings("unchecked")
    private List<Action> parseActionsWithNames(String jsonText, Long sceneId,
                                                Map<Integer, String> seqToCharName) {
        if (jsonText == null || jsonText.isBlank()) {
            return Collections.emptyList();
        }

        // Extract JSON block
        String json = extractJson(jsonText);
        if (json == null) {
            log.warn("ActionAgent: no JSON block found in response");
            return Collections.emptyList();
        }

        try {
            // Try parsing as {"actions": [...]}
            if (json.trim().startsWith("{")) {
                Map<String, Object> wrapper = objectMapper.readValue(json,
                        new TypeReference<Map<String, Object>>() {});
                @SuppressWarnings("unchecked")
                List<Object> rawActions = (List<Object>) wrapper.get("actions");
                if (rawActions != null) {
                    return parseActionList(rawActions, sceneId, seqToCharName);
                }
                log.warn("ActionAgent: JSON object found but no 'actions' key");
                return Collections.emptyList();
            }

            // Try parsing as bare array [...]
            if (json.trim().startsWith("[")) {
                List<Object> list = objectMapper.readValue(json,
                        new TypeReference<List<Object>>() {});
                return parseActionList(list, sceneId, seqToCharName);
            }

            log.warn("ActionAgent: unexpected JSON structure: {}",
                    truncate(json, 100));
            return Collections.emptyList();
        } catch (JsonProcessingException e) {
            log.warn("ActionAgent: JSON parse error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Convert raw JSON objects into Action domain objects.
     * Character names are recorded in {@code seqToCharName} for later resolution.
     */
    @SuppressWarnings("unchecked")
    private List<Action> parseActionList(List<Object> rawList, Long sceneId,
                                          Map<Integer, String> seqToCharName) {
        List<Action> actions = new ArrayList<>();

        for (Object item : rawList) {
            try {
                Map<String, Object> map = (Map<String, Object>) item;

                int sequence = getInt(map, "sequence", actions.size() + 1);
                String characterName = (String) map.get("character");
                String actionTypeStr = (String) map.get("actionType");
                String description = (String) map.get("description");
                int durationMs = getInt(map, "durationMs", 1500);

                if (description == null || description.isBlank()) {
                    log.debug("ActionAgent: skipping action with blank description at seq {}", sequence);
                    continue;
                }

                // Normalize action type
                ActionType actionType = ActionType.fromString(actionTypeStr);
                if (actionType == null) {
                    actionType = ActionType.classify(description);
                }

                // Record character name for later resolution
                if (characterName != null && !characterName.isBlank()) {
                    seqToCharName.put(sequence, characterName.trim());
                }

                Action action = Action.builder()
                        .id(actionIdSeq.getAndIncrement())
                        .sceneId(sceneId)
                        .characterId(null) // resolved in enrichActions
                        .sequence(sequence)
                        .actionType(actionType.name())
                        .description(description.trim())
                        .durationMs(durationMs > 0 ? durationMs : null)
                        .build();

                actions.add(action);
            } catch (Exception e) {
                log.debug("ActionAgent: skipping unparseable action entry: {}", e.getMessage());
            }
        }

        return actions;
    }

    /**
     * Extract a JSON block from text that may contain markdown fences or
     * surrounding commentary.
     */
    static String extractJson(String text) {
        return CharacterAgent.extractJson(text);
    }

    // ──────────────────────────────────────────────────
    //  Enrichment & Character Resolution
    // ──────────────────────────────────────────────────

    /**
     * Resolve character names to IDs and finalize action metadata.
     *
     * @param actions           the parsed actions
     * @param scene             the scene (for sceneId)
     * @param presentCharacters characters appearing in the scene
     * @param seqToCharName     map of sequence → character name from AI output
     */
    private List<Action> enrichActions(List<Action> actions,
                                        Scene scene,
                                        List<Character> presentCharacters,
                                        Map<Integer, String> seqToCharName) {
        if (actions.isEmpty()) return actions;

        // Build name → ID lookup from present characters
        Map<String, Long> nameToId = new HashMap<>();
        if (presentCharacters != null) {
            for (Character c : presentCharacters) {
                if (c.getCanonicalName() != null) {
                    nameToId.put(c.getCanonicalName(), c.getId());
                }
                if (c.getAliases() != null) {
                    for (String alias : c.getAliases()) {
                        nameToId.putIfAbsent(alias, c.getId());
                    }
                }
            }
        }

        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);

            // Ensure sequence is set
            if (action.getSequence() <= 0) {
                action.setSequence(i + 1);
            }

            // Ensure scene ID is set
            if (action.getSceneId() == null && scene != null) {
                action.setSceneId(scene.getId());
            }

            // Resolve character name → characterId
            if (action.getCharacterId() == null) {
                String charName = seqToCharName.get(action.getSequence());
                if (charName != null) {
                    Long charId = nameToId.get(charName);
                    if (charId == null) {
                        // Try partial match: check if any present character's name
                        // appears in or contains the AI-returned character name
                        for (Map.Entry<String, Long> entry : nameToId.entrySet()) {
                            if (charName.contains(entry.getKey())
                                    || entry.getKey().contains(charName)) {
                                charId = entry.getValue();
                                break;
                            }
                        }
                    }
                    action.setCharacterId(charId); // null means environmental / unresolved
                }
            }

            // Re-classify if action type is still not set
            if (action.getActionType() == null || action.getActionType().isBlank()) {
                ActionType classified = ActionType.classify(action.getDescription());
                action.setActionType(classified.name());
            }
        }

        return actions;
    }

    // ──────────────────────────────────────────────────
    //  Visualization Validation
    // ──────────────────────────────────────────────────

    /** Phrases that indicate inner states — NOT filmable. */
    private static final Set<String> INNER_STATE_PHRASES = Set.of(
            "感到", "感觉到", "觉得", "认为", "以为",
            "意识到", "认识到", "知道", "明白",
            "想起了", "回忆起", "记起", "想起",
            "心想", "心里想", "暗想", "心中",
            "内心", "在心里", "心里面", "心里",
            "突然意识到", "忽然间", "一瞬间",
            "他意识到", "她意识到", "他们意识到",
            "他感到", "她感到", "他们感到",
            "他想起", "她想起", "他们想起",
            "他知道了", "她知道了", "他们知道了"
    );

    /** Phrases that indicate narration — NOT allowed in action descriptions. */
    private static final Set<String> NARRATION_PHRASES = Set.of(
            "他想起了", "她想起了", "他们想起了",
            "这时", "此刻", "此时",
            "原来", "其实",
            "突然", "忽然",
            "莫名", "不知为何",
            "仿佛", "好像",
            "谁也不", "没人知", "无人"
    );

    /**
     * Validate that all actions are visualizable (no inner states)
     * and narration-free.
     *
     * @return list of violation messages; empty means all passed
     */
    List<String> validateVisualization(List<Action> actions) {
        if (actions == null || actions.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> violations = new ArrayList<>();

        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);
            String desc = action.getDescription();
            if (desc == null || desc.isBlank()) continue;

            boolean violationFound = false;

            // Check inner state phrases
            for (String phrase : INNER_STATE_PHRASES) {
                if (desc.contains(phrase)) {
                    violations.add(String.format(
                            "动作[%d] 包含内心状态词 '%s': \"%s\"",
                            action.getSequence(), phrase, desc));
                    violationFound = true;
                    break;
                }
            }

            if (violationFound) continue;

            // Check narration phrases
            for (String phrase : NARRATION_PHRASES) {
                if (desc.contains(phrase)) {
                    violations.add(String.format(
                            "动作[%d] 包含旁白叙事词 '%s': \"%s\"",
                            action.getSequence(), phrase, desc));
                    break;
                }
            }
        }

        return violations;
    }

    /**
     * Check whether a single action description is visualizable.
     *
     * @return true if the description contains no inner-state or narration phrases
     */
    public static boolean isVisualizable(String description) {
        if (description == null || description.isBlank()) return false;

        for (String phrase : INNER_STATE_PHRASES) {
            if (description.contains(phrase)) return false;
        }
        for (String phrase : NARRATION_PHRASES) {
            if (description.contains(phrase)) return false;
        }
        return true;
    }

    /**
     * Check whether a description is narration-free.
     *
     * @return true if no narration phrases are present
     */
    public static boolean isNarrationFree(String description) {
        if (description == null || description.isBlank()) return true;

        for (String phrase : NARRATION_PHRASES) {
            if (description.contains(phrase)) return false;
        }
        return true;
    }

    // ──────────────────────────────────────────────────
    //  Utilities
    // ──────────────────────────────────────────────────

    private static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
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

                log.info("📊 Token usage [action-generation] model={}: prompt={} completion={} total={} | prompt_chars={}",
                        model, promptTokens, completionTokens, totalTokens,
                        prompt != null ? prompt.length() : 0);
            }
        } catch (Exception e) {
            log.debug("Failed to extract token usage [action-generation]: {}", e.getMessage());
        }
    }
}
