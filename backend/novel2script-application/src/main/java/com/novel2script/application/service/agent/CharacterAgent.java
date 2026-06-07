package com.novel2script.application.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.application.service.agent.model.CharacterExtractionResult;
import com.novel2script.common.enums.TaskType;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Character;
import com.novel2script.domain.prompt.ValidationRule;
import com.novel2script.infrastructure.config.AiModelRouter;
import com.novel2script.infrastructure.prompt.PromptCache;
import com.novel2script.infrastructure.prompt.PromptRegistry;
import com.novel2script.infrastructure.prompt.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI agent that extracts characters from novel chapters using a versioned
 * prompt template with few-shot examples, structured JSON output, caching,
 * validation, and automatic retry on failure.
 *
 * <h3>Key features</h3>
 * <ul>
 *   <li><b>Template-driven</b>: uses {@link PromptRegistry} to load the latest
 *       "character-extraction" template (currently v2.0).</li>
 *   <li><b>Structured output</b>: parses JSON with Jackson {@link ObjectMapper}
 *       into {@link CharacterExtractionResult} records.</li>
 *   <li><b>Automatic retry</b>: on validation failure, appends error context
 *       to the prompt and re-invokes the model up to {@code maxRetries} times.</li>
 *   <li><b>Result caching</b>: identical prompts + model + temperature hit
 *       {@link PromptCache} to avoid duplicate API calls.</li>
 *   <li><b>Incremental extraction</b>: when existing characters are provided,
 *       only new characters are returned.</li>
 *   <li><b>Audit trail</b>: every AI call is recorded via
 *       audit logging.</li>
 * </ul>
 */
@Slf4j
@Service
public class CharacterAgent {

    private static final String PROMPT_NAME = "character-extraction";

    private final PromptRegistry promptRegistry;
    private final AiModelRouter modelRouter;
    private final PromptCache promptCache;
    private final ObjectMapper objectMapper;

    public CharacterAgent(PromptRegistry promptRegistry,
                          AiModelRouter modelRouter,
                          PromptCache promptCache,
                          ObjectMapper objectMapper) {
        this.promptRegistry = promptRegistry;
        this.modelRouter = modelRouter;
        this.promptCache = promptCache;
        this.objectMapper = objectMapper;
    }

    // ── Public API ────────────────────────────────────────

    /**
     * Extract characters from a list of novel chapters.
     *
     * @param chapters        the parsed chapters to analyze
     * @param focusCharacters optional list of existing character names to
     *                        provide additional context (not for filtering)
     * @return list of extracted character results
     */
    public List<CharacterExtractionResult> extract(
            List<Chapter> chapters,
            List<String> focusCharacters) {

        if (chapters == null || chapters.isEmpty()) {
            log.info("CharacterAgent.extract: no chapters provided, returning empty list");
            return Collections.emptyList();
        }

        // 1. Get the prompt template
        PromptTemplate template = promptRegistry.getLatest(PROMPT_NAME);
        if (template == null) {
            log.error("Prompt template '{}' not found in registry", PROMPT_NAME);
            return Collections.emptyList();
        }

        // 2. Build template variables
        Map<String, Object> variables = buildVariables(chapters, focusCharacters);

        // 3. Render and check cache
        String renderedPrompt = template.renderUserTemplate(variables);
        String modelName = template.getRecommendedModel();
        double temperature = template.getTemperature();

        Optional<String> cached = promptCache.get(renderedPrompt, modelName, temperature);
        if (cached.isPresent()) {
            log.info("CharacterAgent: cache hit for {} chapters", chapters.size());
            List<CharacterExtractionResult> results = parseResponse(cached.get());
            if (!results.isEmpty()) {
                return results;
            }
            // Cache had unparseable content — fall through to re-call
            log.warn("CharacterAgent: cached response unparseable, re-calling AI");
        }

        // 4. Route to the appropriate model
        ChatModel model = modelRouter.route(TaskType.CHARACTER_EXTRACTION);

        // 5. Call with retry
        return callWithRetry(model, template, variables, renderedPrompt);
    }

    /**
     * Incremental extraction: only extract characters from new chapters,
     * excluding those that already exist in the character list.
     *
     * @param newChapters         newly added chapters
     * @param existingCharacters  characters already known to the system
     * @return only newly discovered characters
     */
    public List<CharacterExtractionResult> extractIncremental(
            List<Chapter> newChapters,
            List<Character> existingCharacters) {

        if (newChapters == null || newChapters.isEmpty()) {
            log.info("CharacterAgent.extractIncremental: no new chapters");
            return Collections.emptyList();
        }

        // Build focus list from existing character canonical names
        List<String> focusNames;
        if (existingCharacters != null && !existingCharacters.isEmpty()) {
            focusNames = existingCharacters.stream()
                    .map(Character::getCanonicalName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        } else {
            focusNames = Collections.emptyList();
        }

        // Extract all characters from new chapters with focus context
        List<CharacterExtractionResult> allExtracted = extract(newChapters, focusNames);

        // Filter out characters that already exist (by canonical name match)
        Set<String> existingNames = focusNames.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        List<CharacterExtractionResult> newOnes = allExtracted.stream()
                .filter(r -> !existingNames.contains(r.name().toLowerCase()))
                // Also check aliases
                .filter(r -> r.aliases().stream()
                        .noneMatch(a -> existingNames.contains(a.toLowerCase())))
                .collect(Collectors.toList());

        log.info("CharacterAgent.extractIncremental: {} total extracted, {} new ({} existing)",
                allExtracted.size(), newOnes.size(), existingNames.size());

        return newOnes;
    }

    // ── Core extraction with retry ───────────────────────

    /**
     * Call the AI model with retry logic: on validation failure, append
     * error context to the prompt and retry up to {@code maxRetries} times.
     */
    private List<CharacterExtractionResult> callWithRetry(
            ChatModel model,
            PromptTemplate template,
            Map<String, Object> variables,
            String baseUserPrompt) {

        int maxRetries = template.getMaxRetries();
        List<ValidationRule> rules = template.getValidationRules();
        StringBuilder retryContext = new StringBuilder();
        int totalInputTokens = 0;
        int totalOutputTokens = 0;
        String modelName = template.getRecommendedModel();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            Instant start = Instant.now();

            // Build the prompt, appending retry context for retries
            String userPrompt = attempt == 0
                    ? baseUserPrompt
                    : baseUserPrompt + "\n\n【重要提示 — 第" + attempt + "次重试】\n"
                      + "上一次输出验证失败，请严格按照以下要求修正：\n" + retryContext;

            Prompt prompt = buildFullPrompt(template, variables, userPrompt, attempt);

            try {
                ChatResponse response = model.call(prompt);
                String responseText = response.getResult().getOutput().getText();
                long latencyMs = Duration.between(start, Instant.now()).toMillis();

                // Extract token counts if available
                if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                    totalInputTokens += (int) response.getMetadata().getUsage().getPromptTokens();
                    totalOutputTokens += (int) response.getMetadata().getUsage().getCompletionTokens();
                }

                // Parse the JSON response
                List<CharacterExtractionResult> results = parseResponse(responseText);
                if (results.isEmpty()) {
                    retryContext.setLength(0);
                    retryContext.append("无法从响应中解析JSON。请确保输出是有效的JSON数组，"
                            + "每个元素包含 name, role_type, description 字段。\n");
                    retryContext.append("你的响应片段：").append(truncate(responseText, 300));
                    log.warn("CharacterAgent: attempt {} returned unparseable JSON", attempt + 1);
                    auditFailure(template, modelName, totalInputTokens, totalOutputTokens,
                            latencyMs, attempt, "Unparseable JSON response");
                    continue;
                }

                // Validate results
                List<String> validationErrors = validateResults(results, rules);
                if (!validationErrors.isEmpty()) {
                    retryContext.setLength(0);
                    retryContext.append("以下验证失败，请修正：\n");
                    for (String error : validationErrors) {
                        retryContext.append("  - ").append(error).append('\n');
                    }
                    retryContext.append("\n原始响应片段：").append(truncate(responseText, 200));
                    log.warn("CharacterAgent: attempt {} validation failed: {}",
                            attempt + 1, validationErrors);
                    auditFailure(template, modelName, totalInputTokens, totalOutputTokens,
                            latencyMs, attempt, String.join("; ", validationErrors));
                    continue;
                }

                // Success — cache the result
                promptCache.put(baseUserPrompt, modelName, template.getTemperature(), responseText);
                log.info("CharacterAgent: extracted {} characters in {} attempt(s), {}ms",
                        results.size(), attempt + 1, latencyMs);

                auditSuccess(template, modelName, totalInputTokens, totalOutputTokens,
                        latencyMs, attempt, responseText);
                return results;

            } catch (Exception e) {
                long latencyMs = Duration.between(start, Instant.now()).toMillis();
                log.error("CharacterAgent: attempt {} failed with exception: {}",
                        attempt + 1, e.getMessage());
                auditFailure(template, modelName, totalInputTokens, totalOutputTokens,
                        latencyMs, attempt, e.getClass().getSimpleName() + ": " + e.getMessage());

                if (attempt >= maxRetries) {
                    log.error("CharacterAgent: all {} retries exhausted", maxRetries);
                    return Collections.emptyList();
                }
                retryContext.setLength(0);
                retryContext.append("上次调用失败：").append(e.getMessage())
                        .append("\n请重新生成完整的JSON响应。");
            }
        }

        log.error("CharacterAgent: max retries ({}) exhausted without valid result", maxRetries);
        return Collections.emptyList();
    }

    // ── Prompt building ──────────────────────────────────

    /**
     * Build template variables from chapters and focus characters.
     */
    private Map<String, Object> buildVariables(List<Chapter> chapters,
                                                List<String> focusCharacters) {
        // Build chapter list for Mustache iteration
        List<Map<String, Object>> chapterMaps = new ArrayList<>();
        for (Chapter ch : chapters) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("chapterNumber", ch.getChapterNumber());
            cm.put("title", ch.getTitle() != null ? ch.getTitle() : "");
            cm.put("content", ch.getContent() != null ? ch.getContent() : "");
            chapterMaps.add(cm);
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("chapters", chapterMaps);

        if (focusCharacters != null && !focusCharacters.isEmpty()) {
            variables.put("focusCharacters", String.join("、", focusCharacters));
        } else {
            variables.put("focusCharacters", "");
        }

        return variables;
    }

    /**
     * Build the complete Spring AI {@link Prompt} from the template and variables.
     * Uses the template's system prompt + few-shot examples, plus the rendered
     * user prompt with optional retry context.
     */
    private Prompt buildFullPrompt(PromptTemplate template,
                                   Map<String, Object> variables,
                                   String userContent,
                                   int attempt) {
        // For retries, we render a fresh user template each time;
        // for first attempt, the userContent is already the rendered template.
        // We use renderSimple which wraps userContent directly.
        if (attempt == 0) {
            // Use the full template rendering with system + few-shots
            return template.render(variables);
        }
        // On retry, we append retry context — build manually
        String system = template.buildSystemWithFewShots();
        return new Prompt(
                new org.springframework.ai.chat.messages.SystemMessage(system),
                new org.springframework.ai.chat.messages.UserMessage(userContent));
    }

    // ── Response parsing ─────────────────────────────────

    /**
     * Parse the AI response text into a list of {@link CharacterExtractionResult}.
     * Handles both the top-level object wrapper {@code {"characters": [...]}}
     * and a bare JSON array.
     */
    List<CharacterExtractionResult> parseResponse(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return Collections.emptyList();
        }

        // Extract the JSON block from the response
        String json = extractJson(responseText);
        if (json == null) {
            log.warn("CharacterAgent: no JSON block found in response");
            return Collections.emptyList();
        }

        try {
            // Try parsing as {"characters": [...]}
            if (json.trim().startsWith("{")) {
                Map<String, Object> wrapper = objectMapper.readValue(json,
                        new TypeReference<Map<String, Object>>() {});
                @SuppressWarnings("unchecked")
                List<Object> characters = (List<Object>) wrapper.get("characters");
                if (characters != null) {
                    return parseCharacterList(characters);
                }
                log.warn("CharacterAgent: JSON object found but no 'characters' key");
                return Collections.emptyList();
            }

            // Try parsing as bare array [...]
            if (json.trim().startsWith("[")) {
                List<Object> list = objectMapper.readValue(json,
                        new TypeReference<List<Object>>() {});
                return parseCharacterList(list);
            }

            log.warn("CharacterAgent: unexpected JSON structure: {}", truncate(json, 100));
            return Collections.emptyList();
        } catch (JsonProcessingException e) {
            log.warn("CharacterAgent: JSON parse error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Convert a list of raw JSON objects into {@link CharacterExtractionResult} records.
     * Objects that fail to deserialize are silently skipped.
     * Noise entries (non-name text fragments) are filtered out via
     * {@link CharacterNameFilter}.
     */
    private List<CharacterExtractionResult> parseCharacterList(List<Object> rawList) {
        List<CharacterExtractionResult> results = new ArrayList<>();
        for (Object item : rawList) {
            try {
                CharacterExtractionResult result = objectMapper.convertValue(
                        item, CharacterExtractionResult.class);
                if (result.name() != null && !result.name().isBlank()) {
                    results.add(result);
                }
            } catch (Exception e) {
                log.debug("CharacterAgent: skipping unparseable character entry: {}",
                        e.getMessage());
            }
        }
        // Post-processing: filter out noise entries (sentence fragments, etc.)
        return CharacterNameFilter.filter(results);
    }

    /**
     * Extract a JSON block (object or array) from AI response text that may
     * contain markdown fences or explanatory text around the JSON.
     * Uses bracket-depth tracking to handle nested structures correctly.
     */
    static String extractJson(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return null;
        }

        // 1. Try extracting from ```json ... ``` fence
        Pattern fencePattern = Pattern.compile(
                "```(?:json)?\\s*([\\s\\S]*?)```", Pattern.DOTALL);
        Matcher fenceMatcher = fencePattern.matcher(responseText);
        if (fenceMatcher.find()) {
            String inner = fenceMatcher.group(1).trim();
            if (inner.startsWith("[") || inner.startsWith("{")) {
                return extractBalancedJson(inner, 0);
            }
        }

        // 2. Find first [ or { and extract balanced block
        for (int i = 0; i < responseText.length(); i++) {
            char c = responseText.charAt(i);
            if (c == '[' || c == '{') {
                String extracted = extractBalancedJson(responseText, i);
                if (extracted != null) {
                    return extracted;
                }
            }
        }

        return null;
    }

    /**
     * Extract a balanced JSON value starting at {@code startPos} in the text.
     * Tracks bracket/brace depth to correctly handle nested arrays and objects.
     *
     * @return the balanced JSON substring, or {@code null} if unbalanced
     */
    private static String extractBalancedJson(String text, int startPos) {
        if (startPos >= text.length()) return null;

        char openChar = text.charAt(startPos);
        char closeChar = (openChar == '[') ? ']' : '}';

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = startPos; i < text.length(); i++) {
            char c = text.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }

            if (c == openChar) {
                depth++;
            } else if (c == closeChar) {
                depth--;
                if (depth == 0) {
                    return text.substring(startPos, i + 1);
                }
            } else if (c == '{' && openChar == '[') {
                depth++; // nested object inside array
            } else if (c == '}') {
                depth--; // close nested object inside array
            } else if (c == '[') {
                depth++; // nested array inside object
            } else if (c == ']') {
                depth--; // close nested array inside object
            }
        }

        return null; // unbalanced
    }

    // ── Validation ───────────────────────────────────────

    /**
     * Validate extracted results against the template's validation rules.
     *
     * @return list of error messages; empty list means all validations passed
     */
    List<String> validateResults(List<CharacterExtractionResult> results,
                                  List<ValidationRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> errors = new ArrayList<>();

        for (ValidationRule rule : rules) {
            if (rule.isNotBlank() && rule.getField() != null) {
                errors.addAll(validateNotBlank(results, rule.getField()));
            }
            if (rule.isEnumMatch() && rule.getField() != null) {
                errors.addAll(validateEnumMatch(results, rule.getField(), rule.getParam()));
            }
            if (rule.isNotNull() && rule.getField() != null) {
                errors.addAll(validateNotNull(results, rule.getField()));
            }
        }

        return errors;
    }

    private List<String> validateNotBlank(List<CharacterExtractionResult> results,
                                           String fieldPath) {
        List<String> errors = new ArrayList<>();
        // characters[].name → check each result's name
        if (fieldPath.contains("name")) {
            for (int i = 0; i < results.size(); i++) {
                if (results.get(i).name() == null || results.get(i).name().isBlank()) {
                    errors.add("character[" + i + "].name 不能为空");
                }
            }
        }
        if (fieldPath.contains("description")) {
            for (int i = 0; i < results.size(); i++) {
                if (results.get(i).description() == null
                        || results.get(i).description().isBlank()) {
                    errors.add("character[" + i + "].description 不能为空");
                }
            }
        }
        return errors;
    }

    private List<String> validateEnumMatch(List<CharacterExtractionResult> results,
                                            String fieldPath, String param) {
        if (param == null) return Collections.emptyList();

        Set<String> allowedValues = Arrays.stream(param.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        List<String> errors = new ArrayList<>();
        if (fieldPath.contains("role_type")) {
            for (int i = 0; i < results.size(); i++) {
                String roleType = results.get(i).roleType() != null
                        ? results.get(i).roleType().name() : null;
                if (roleType == null || !allowedValues.contains(roleType.toUpperCase())) {
                    errors.add("character[" + i + "].role_type '"
                            + roleType + "' 不在允许的值中: " + param);
                }
            }
        }
        return errors;
    }

    private List<String> validateNotNull(List<CharacterExtractionResult> results,
                                          String fieldPath) {
        List<String> errors = new ArrayList<>();
        if (fieldPath.contains("role_type")) {
            for (int i = 0; i < results.size(); i++) {
                if (results.get(i).roleType() == null) {
                    errors.add("character[" + i + "].role_type 不能为 null");
                }
            }
        }
        return errors;
    }

    // ── Audit helpers (log-only since DB audit removed) ───

    private void auditSuccess(PromptTemplate template, String modelName,
                               int inputTokens, int outputTokens,
                               long latencyMs, int retryCount,
                               String fullResponse) {
        log.debug("{} v{} audit: SUCCESS model={} tokens={}/{} latency={}ms retries={}",
                template.getName(), template.getVersion(), modelName,
                inputTokens, outputTokens, latencyMs, retryCount);
    }

    private void auditFailure(PromptTemplate template, String modelName,
                               int inputTokens, int outputTokens,
                               long latencyMs, int retryCount,
                               String errorMessage) {
        log.debug("{} v{} audit: FAILED model={} tokens={}/{} latency={}ms retries={} error={}",
                template.getName(), template.getVersion(), modelName,
                inputTokens, outputTokens, latencyMs, retryCount, errorMessage);
    }

    // ── Utility ──────────────────────────────────────────

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
