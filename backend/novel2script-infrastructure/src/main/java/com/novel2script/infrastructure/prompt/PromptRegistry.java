package com.novel2script.infrastructure.prompt;

import com.novel2script.domain.prompt.FewShot;
import com.novel2script.domain.prompt.JsonSchema;
import com.novel2script.domain.prompt.ValidationRule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry that loads all prompt templates from {@code resources/prompt/}
 * at startup and provides versioned lookup.
 *
 * <h3>Directory convention</h3>
 * <pre>
 * resources/prompt/
 *   character/extraction/
 *     v1.0.yml
 *     v2.0.yml
 *   dialogue/generation/
 *     v1.0.yml
 * </pre>
 *
 * <p>Each YAML file declares its {@code name} and {@code version}.
 * Multiple versions of the same named prompt are supported simultaneously.
 */
@Slf4j
@Component
public class PromptRegistry {

    private static final String PROMPT_LOCATION = "classpath*:prompt/**/*.yml";

    /**
     * Registered templates keyed by name → ordered list of versions (latest first).
     */
    private final Map<String, List<PromptTemplate>> templates = new ConcurrentHashMap<>();

    // ── Lifecycle ───────────────────────────────────────

    @PostConstruct
    public void loadAll() {
        log.info("Loading prompt templates from {}", PROMPT_LOCATION);
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(PROMPT_LOCATION);

            int loaded = 0;
            for (Resource resource : resources) {
                try {
                    PromptTemplate template = loadFromResource(resource);
                    register(template);
                    loaded++;
                } catch (Exception e) {
                    log.error("Failed to load prompt template from {}: {}", resource.getFilename(), e.getMessage());
                }
            }

            // Sort each list by version descending
            templates.forEach((name, list) ->
                    list.sort((a, b) -> compareVersions(b.getVersion(), a.getVersion())));

            log.info("Loaded {} prompt templates across {} names: {}",
                    loaded, templates.size(), templates.keySet());
        } catch (Exception e) {
            log.error("Failed to scan prompt templates", e);
        }
    }

    // ── Public API ──────────────────────────────────────

    /**
     * Get the latest version of a named prompt template.
     *
     * @param name template name (e.g. "character-extraction")
     * @return the latest version, or {@code null} if not found
     */
    public PromptTemplate getLatest(String name) {
        List<PromptTemplate> versions = templates.get(name);
        if (versions == null || versions.isEmpty()) {
            log.warn("No templates registered for name '{}'", name);
            return null;
        }
        return versions.get(0); // sorted latest-first
    }

    /**
     * Get a specific version of a named prompt template.
     *
     * @param name    template name
     * @param version semantic version string (e.g. "2.0")
     * @return the matching template, or {@code null} if not found
     */
    public PromptTemplate getVersion(String name, String version) {
        List<PromptTemplate> versions = templates.get(name);
        if (versions == null) return null;
        return versions.stream()
                .filter(t -> t.getVersion().equals(version))
                .findFirst()
                .orElse(null);
    }

    /**
     * List all versions for a named prompt, latest first.
     */
    public List<PromptTemplate> listVersions(String name) {
        return templates.getOrDefault(name, Collections.emptyList());
    }

    /**
     * List all registered prompt names.
     */
    public Set<String> getRegisteredNames() {
        return Collections.unmodifiableSet(templates.keySet());
    }

    /**
     * Total number of registered templates (all versions).
     */
    public int getTotalCount() {
        return templates.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Hot-reload: re-read all prompt templates from disk at runtime.
     * Useful during development without restarting the application.
     */
    public void reload(String name) {
        log.info("Reloading prompt templates for '{}'", name);
        List<PromptTemplate> existing = templates.remove(name);
        if (existing != null) {
            log.info("Removed {} versions of '{}' — will reload on next scan", existing.size(), name);
        }
        // Trigger re-scan for this name
        loadAll();
    }

    /**
     * Reload all templates.
     */
    public void reloadAll() {
        templates.clear();
        loadAll();
    }

    // ── Internal ────────────────────────────────────────

    private void register(PromptTemplate template) {
        templates.computeIfAbsent(template.getName(), k -> new ArrayList<>()).add(template);
        log.debug("Registered prompt template: {} v{}", template.getName(), template.getVersion());
    }

    @SuppressWarnings("unchecked")
    private PromptTemplate loadFromResource(Resource resource) throws Exception {
        Yaml yaml = new Yaml();
        try (InputStream is = resource.getInputStream()) {
            Map<String, Object> data = yaml.load(is);

            // ── Parse model config ───────────────────────
            Map<String, Object> model = (Map<String, Object>) data.getOrDefault("model", Map.of());
            String recommendedModel = (String) model.getOrDefault("recommended", "deepseek-chat");
            double temperature = toDouble(model.get("temperature"), 0.7);
            int maxTokens = toInt(model.get("max_tokens"), 4096);

            // ── Parse few-shot examples ──────────────────
            List<FewShot> fewShots = new ArrayList<>();
            List<Map<String, String>> fewShotList = (List<Map<String, String>>) data.get("few_shot");
            if (fewShotList != null) {
                for (Map<String, String> fs : fewShotList) {
                    fewShots.add(FewShot.builder()
                            .input(fs.get("input"))
                            .output(fs.get("output"))
                            .build());
                }
            }

            // ── Parse output schema ──────────────────────
            JsonSchema outputSchema = null;
            Map<String, Object> schemaData = (Map<String, Object>) data.get("output_schema");
            if (schemaData != null) {
                outputSchema = parseJsonSchema(schemaData);
            }

            // ── Parse validation rules ───────────────────
            List<ValidationRule> validationRules = new ArrayList<>();
            Map<String, Object> validation = (Map<String, Object>) data.get("validation");
            if (validation != null) {
                List<Map<String, String>> rules = (List<Map<String, String>>) validation.get("rules");
                if (rules != null) {
                    for (Map<String, String> rule : rules) {
                        validationRules.add(ValidationRule.builder()
                                .field(rule.get("field"))
                                .rule(rule.get("rule"))
                                .param(rule.get("param"))
                                .build());
                    }
                }
                Map<String, Object> retry = (Map<String, Object>) validation.get("retry");
                if (retry != null && retry.get("max_retries") != null) {
                    // max_retries is read below
                }
            }

            int maxRetries = 3;
            if (validation != null) {
                Map<String, Object> retry = (Map<String, Object>) validation.get("retry");
                if (retry != null && retry.get("max_retries") != null) {
                    maxRetries = toInt(retry.get("max_retries"), 3);
                }
            }

            return PromptTemplate.builder()
                    .name((String) data.get("name"))
                    .version((String) data.get("version"))
                    .description((String) data.get("description"))
                    .recommendedModel(recommendedModel)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .system((String) data.get("system"))
                    .userTemplate((String) data.get("user_template"))
                    .fewShots(fewShots)
                    .outputSchema(outputSchema)
                    .validationRules(validationRules)
                    .maxRetries(maxRetries)
                    .build();
        }
    }

    @SuppressWarnings("unchecked")
    private JsonSchema parseJsonSchema(Map<String, Object> data) {
        JsonSchema.JsonSchemaBuilder builder = JsonSchema.builder()
                .type((String) data.get("type"))
                .required((List<String>) data.get("required"));

        // Properties
        Map<String, Object> props = (Map<String, Object>) data.get("properties");
        if (props != null) {
            Map<String, JsonSchema.PropertyDef> parsed = new HashMap<>();
            props.forEach((key, value) -> {
                Map<String, Object> propMap = (Map<String, Object>) value;
                JsonSchema.PropertyDef.PropertyDefBuilder propBuilder = JsonSchema.PropertyDef.builder()
                        .type((String) propMap.get("type"))
                        .enumValues((List<String>) propMap.get("enum"));
                // Nested items
                Map<String, Object> items = (Map<String, Object>) propMap.get("items");
                if (items != null) {
                    propBuilder.items(parseJsonSchema(items));
                }
                // Nested required
                propBuilder.required((List<String>) propMap.get("required"));
                parsed.put(key, propBuilder.build());
            });
            builder.properties(parsed);
        }

        // Definitions
        Map<String, Object> defs = (Map<String, Object>) data.get("definitions");
        if (defs != null) {
            Map<String, JsonSchema> parsedDefs = new HashMap<>();
            defs.forEach((key, value) ->
                    parsedDefs.put(key, parseJsonSchema((Map<String, Object>) value)));
            builder.definitions(parsedDefs);
        }

        return builder.build();
    }

    // ── Version comparison ──────────────────────────────

    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int n2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (n1 != n2) return n1 - n2;
        }
        return 0;
    }

    private static double toDouble(Object val, double def) {
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) return Double.parseDouble(s);
        return def;
    }

    private static int toInt(Object val, int def) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) return Integer.parseInt(s);
        return def;
    }
}
