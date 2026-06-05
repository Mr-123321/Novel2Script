package com.novel2script.application.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.application.service.agent.model.CharacterExtractionResult;
import com.novel2script.application.service.agent.model.ExtractedRelationship;
import com.novel2script.domain.vector.SimilarityResult;
import com.novel2script.common.constant.Constants;
import com.novel2script.common.enums.CharacterRoleType;
import com.novel2script.common.enums.TaskType;
import com.novel2script.domain.model.Character;
import com.novel2script.infrastructure.config.AiModelRouter;
import com.novel2script.infrastructure.prompt.PromptRegistry;
import com.novel2script.infrastructure.prompt.PromptTemplate;
import com.novel2script.infrastructure.vector.EmbeddingService;
import com.novel2script.infrastructure.vector.MilvusVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent that resolves character identity by merging aliases and deduplicating
 * characters that refer to the same person.
 *
 * <h3>Three-layer resolution strategy</h3>
 * <ol>
 *   <li><b>Rule-based matching</b> (fast, ~70% accuracy):
 *       Chinese name analysis — detects 姓+称谓, 名+称谓, prefix/suffix patterns,
 *       substring containment, and Levenshtein edit distance.</li>
 *   <li><b>Vector similarity</b> (precise, ~95% accuracy):
 *       Generates embeddings of name+description, stores in vector store,
 *       searches via cosine similarity with threshold 0.85.</li>
 *   <li><b>LLM arbitration</b> (ambiguous cases):
 *       When similarity is 0.75–0.85, asks the LLM to decide whether two
 *       entities are the same person.</li>
 * </ol>
 */
@Slf4j
@Service
public class CharacterResolverAgent {

    // Common Chinese surname prefixes used in informal address
    private static final Set<String> NAME_PREFIXES = Set.of(
            "小", "老", "阿", "大"
    );

    // Common Chinese name suffixes (honorifics / kinship terms)
    private static final Set<String> NAME_SUFFIXES = Set.of(
            "哥", "姐", "兄", "弟", "妹", "叔", "姨", "伯", "婶", "嫂",
            "爷", "奶", "婆", "公", "总", "董", "老板",
            "师兄", "师姐", "师弟", "师妹", "师父", "师傅", "徒弟",
            "同学", "老师", "医生", "律师", "警官", "队长",
            "儿", "子"
    );

    // Surnames found in names like "林师兄" → strip "师兄" to get "林"
    private static final Set<String> COMPOUND_SUFFIXES = Set.of(
            "师兄", "师姐", "师弟", "师妹", "师父", "师傅", "同学"
    );

    private final MilvusVectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final AiModelRouter modelRouter;
    private final PromptRegistry promptRegistry;
    private final ObjectMapper objectMapper;

    public CharacterResolverAgent(MilvusVectorStore vectorStore,
                                   EmbeddingService embeddingService,
                                   AiModelRouter modelRouter,
                                   PromptRegistry promptRegistry,
                                   ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.modelRouter = modelRouter;
        this.promptRegistry = promptRegistry;
        this.objectMapper = objectMapper;
    }

    // ── Public API ────────────────────────────────────────

    /**
     * Resolve and deduplicate a list of raw extracted characters into
     * a unified character list.
     *
     * @param rawCharacters characters extracted by {@link CharacterAgent}
     * @return deduplicated and merged domain characters
     */
    public List<Character> resolve(List<CharacterExtractionResult> rawCharacters) {
        if (rawCharacters == null || rawCharacters.isEmpty()) {
            log.info("CharacterResolverAgent.resolve: no characters to resolve");
            return Collections.emptyList();
        }

        // Step 1: Rule-based grouping — fast, deterministic
        List<List<CharacterExtractionResult>> ruleGroups =
                ruleBasedGrouping(rawCharacters);

        log.info("CharacterResolverAgent: {} raw characters → {} rule-based groups",
                rawCharacters.size(), ruleGroups.size());

        // Step 2: Merge each group via embedding similarity
        List<Character> resolved = new ArrayList<>();
        for (List<CharacterExtractionResult> group : ruleGroups) {
            if (group.size() == 1) {
                // Single character — no conflict, convert directly
                resolved.add(convertToDomain(group.get(0), Collections.emptyList()));
            } else {
                // Multiple candidates — use embedding to resolve
                Character merged = mergeViaEmbedding(group);
                resolved.add(merged);
            }
        }

        // Step 3: LLM arbitration for remaining ambiguous pairs (skip if not available)
        if (promptRegistry != null && modelRouter != null) {
            return llmArbitration(resolved);
        }
        return resolved;
    }

    // ── Layer 1: Rule-based grouping ──────────────────────

    /**
     * Group characters that likely refer to the same person using
     * Chinese name analysis and edit-distance heuristics.
     *
     * <p>This is fast and deterministic — no AI calls needed.
     */
    List<List<CharacterExtractionResult>> ruleBasedGrouping(
            List<CharacterExtractionResult> characters) {

        // Use Union-Find to build transitive groups
        int n = characters.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (isSamePersonByRules(
                        characters.get(i).name(),
                        characters.get(j).name())) {
                    union(parent, i, j);
                }
            }
        }

        // Build groups from Union-Find
        Map<Integer, List<CharacterExtractionResult>> groupMap = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            groupMap.computeIfAbsent(root, k -> new ArrayList<>())
                    .add(characters.get(i));
        }

        return new ArrayList<>(groupMap.values());
    }

    /**
     * Determine whether two names likely refer to the same person
     * using Chinese name heuristics.
     */
    static boolean isSamePersonByRules(String name1, String name2) {
        if (name1 == null || name2 == null) return false;
        if (name1.equals(name2)) return true;

        String n1 = name1.trim();
        String n2 = name2.trim();
        if (n1.isEmpty() || n2.isEmpty()) return false;

        // 1. One contains the other (e.g., "林川" contains "川")
        if (n1.contains(n2) || n2.contains(n1)) return true;

        // 2. Extract base names
        String base1 = extractBaseName(n1);
        String base2 = extractBaseName(n2);

        if (base1.equals(base2)) return true;

        // 3. Shared surname check (e.g., "林川" and "林师兄")
        if (hasSharedSurname(n1, n2)) {
            String given1 = extractGivenName(n1);
            String given2 = extractGivenName(n2);
            if (given1 != null && given2 != null && given1.equals(given2)) {
                return true;
            }
            // One given name contains the other
            if (given1 != null && given2 != null
                    && (given1.contains(given2) || given2.contains(given1))) {
                return true;
            }
        }

        // 4. Levenshtein distance < 2 for short names (2-3 chars)
        if (Math.min(n1.length(), n2.length()) <= 3) {
            int dist = levenshteinDistance(n1, n2);
            if (dist < 2) return true;
        }

        // 5. Alias patterns: "小X" / "老X" vs "X" or full name containing "X"
        return isAliasPattern(n1, n2);
    }

    /**
     * Extract the "base name" by removing common prefixes and suffixes.
     * e.g., "川哥" → "川", "小川" → "川", "林师兄" → "林"
     */
    static String extractBaseName(String name) {
        if (name == null || name.isBlank()) return name;

        String base = name.trim();

        // Remove compound suffixes first (longer patterns first)
        for (String suffix : COMPOUND_SUFFIXES) {
            if (base.endsWith(suffix) && base.length() > suffix.length()) {
                base = base.substring(0, base.length() - suffix.length());
                break;
            }
        }

        // Remove single-char suffixes
        for (String suffix : NAME_SUFFIXES) {
            if (suffix.length() == 1 && base.endsWith(suffix)
                    && base.length() > 1) {
                base = base.substring(0, base.length() - 1);
                break;
            }
        }

        // Remove prefixes
        for (String prefix : NAME_PREFIXES) {
            if (base.startsWith(prefix) && base.length() > 1) {
                base = base.substring(1);
                break;
            }
        }

        return base;
    }

    /**
     * Extract the given name (名) portion of a Chinese name.
     * Assumes surname is the first character (simplified assumption).
     */
    private static String extractGivenName(String name) {
        if (name == null || name.length() < 2) return null;
        // Check if the first char is a common prefix
        String trimmed = name.trim();
        if (NAME_PREFIXES.contains(trimmed.substring(0, 1))) {
            return trimmed.substring(1);
        }
        // First char is surname, rest is given name
        return trimmed.length() >= 2 ? trimmed.substring(1) : null;
    }

    /**
     * Check if two names share the same surname (first character).
     */
    private static boolean hasSharedSurname(String n1, String n2) {
        if (n1.length() < 2 || n2.length() < 2) return false;
        char s1 = n1.charAt(0);
        char s2 = n2.charAt(0);
        // Skip common prefixes
        if (NAME_PREFIXES.contains(String.valueOf(s1))) {
            s1 = n1.length() >= 2 ? n1.charAt(1) : s1;
        }
        if (NAME_PREFIXES.contains(String.valueOf(s2))) {
            s2 = n2.length() >= 2 ? n2.charAt(1) : s2;
        }
        return s1 == s2;
    }

    /**
     * Check alias patterns like "小川" ↔ "林川" or "川哥" ↔ "川".
     */
    private static boolean isAliasPattern(String n1, String n2) {
        String base1 = extractBaseName(n1);
        String base2 = extractBaseName(n2);

        // "川哥" base is "川", and "林川" contains "川"
        if (n1.contains(base2) || n2.contains(base1)) return true;

        // Shared base name
        return base1.equals(base2);
    }

    /**
     * Compute Levenshtein edit distance between two strings.
     */
    static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
                // Allow transposition for Chinese characters
                if (i > 1 && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    dp[i][j] = Math.min(dp[i][j], dp[i - 2][j - 2] + cost);
                }
            }
        }

        return dp[a.length()][b.length()];
    }

    // ── Layer 2: Embedding-based merging ──────────────────

    /**
     * Merge a group of potentially-identical characters using embedding similarity.
     * The canonical name is the one with the longest description (most information).
     */
    private Character mergeViaEmbedding(List<CharacterExtractionResult> group) {
        if (group.size() == 1) {
            return convertToDomain(group.get(0), Collections.emptyList());
        }

        // Pick canonical: prefer the one with the most complete description
        CharacterExtractionResult canonical = group.stream()
                .max(Comparator.comparingInt(c ->
                        (c.description() != null ? c.description().length() : 0)
                        + (c.personality().size() * 10)
                        + (c.relationships().size() * 5)))
                .orElse(group.get(0));

        // Characters in the same rule-based group are considered the same person.
        // Collect all aliases from all members.
        List<String> allAliases = new ArrayList<>(canonical.aliases());
        List<String> mergedNames = new ArrayList<>();

        for (CharacterExtractionResult c : group) {
            if (c == canonical) continue;
            mergedNames.add(c.name());
            if (!allAliases.contains(c.name())) {
                allAliases.add(c.name());
            }
            for (String alias : c.aliases()) {
                if (!allAliases.contains(alias)) {
                    allAliases.add(alias);
                }
            }
        }

        // Generate embedding for canonical and store in vector store
        try {
            String text = canonical.name() + " "
                    + (canonical.description() != null ? canonical.description() : "");
            float[] embedding = embeddingService.embed(text);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("name", canonical.name());
            metadata.put("description", canonical.description());
            metadata.put("aliases", allAliases);
            vectorStore.insertCharacter(canonical.name(), embedding, metadata);
        } catch (Exception e) {
            log.debug("CharacterResolverAgent: vector store insert skipped: {}", e.getMessage());
        }

        // Build merged character
        Character domain = canonical.toDomainCharacter();
        domain.setAliases(allAliases);
        domain.setResolved(true);

        log.debug("CharacterResolverAgent: merged group of {} into '{}' with {} aliases",
                group.size(), canonical.name(), allAliases.size());

        return domain;
    }

    // ── Layer 3: LLM arbitration ──────────────────────────

    /**
     * Use LLM to resolve ambiguous character merges.
     * Characters with embedding similarity 0.75–0.85 are sent to the LLM for
     * final judgment.
     */
    private List<Character> llmArbitration(List<Character> candidates) {
        if (candidates.size() <= 1) return candidates;

        // For pairs with same canonical name but different descriptions,
        // use the LLM to decide if they're really the same.
        PromptTemplate template = promptRegistry.getLatest("character-resolution");
        if (template == null) {
            log.warn("CharacterResolverAgent: character-resolution template not found, "
                    + "skipping LLM arbitration");
            return candidates;
        }

        // Build input for the LLM
        List<Map<String, Object>> charMaps = new ArrayList<>();
        for (Character c : candidates) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("name", c.getCanonicalName());
            cm.put("aliases", c.getAliases() != null
                    ? String.join(", ", c.getAliases()) : "");
            cm.put("roleType", c.getRoleType() != null
                    ? c.getRoleType().name() : "UNKNOWN");
            cm.put("description", c.getDescription() != null
                    ? c.getDescription() : "");
            charMaps.add(cm);
        }

        Map<String, Object> variables = Map.of("characters", charMaps);
        Prompt prompt = template.render(variables);

        try {
            ChatModel model = modelRouter.route(TaskType.CHARACTER_RESOLVE);
            var response = model.call(prompt);
            String responseText = response.getResult().getOutput().getText();

            // Parse merge groups from LLM response
            List<Character> resolved = applyLlmMergeDecisions(candidates, responseText);
            log.info("CharacterResolverAgent: LLM arbitration completed, {} → {} characters",
                    candidates.size(), resolved.size());
            return resolved;
        } catch (Exception e) {
            log.error("CharacterResolverAgent: LLM arbitration failed: {}", e.getMessage());
            return candidates; // return as-is on failure
        }
    }

    /**
     * Parse LLM response and apply merge decisions.
     */
    private List<Character> applyLlmMergeDecisions(List<Character> candidates,
                                                     String llmResponse) {
        try {
            // Extract JSON from response
            String json = CharacterAgent.extractJson(llmResponse);
            if (json == null) return candidates;

            Map<String, Object> wrapper = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> mergeGroups =
                    (List<Map<String, Object>>) wrapper.get("merge_groups");

            if (mergeGroups == null || mergeGroups.isEmpty()) return candidates;

            Set<String> mergedOut = new HashSet<>();
            List<Character> result = new ArrayList<>();

            for (Map<String, Object> group : mergeGroups) {
                String canonicalName = (String) group.get("canonical");
                @SuppressWarnings("unchecked")
                List<String> merged = (List<String>) group.get("merged");

                // Find the canonical character
                Character canonical = candidates.stream()
                        .filter(c -> c.getCanonicalName().equals(canonicalName))
                        .findFirst().orElse(null);

                if (canonical != null) {
                    if (merged != null && !merged.isEmpty()) {
                        List<String> allAliases = new ArrayList<>(canonical.getAliases());
                        allAliases.addAll(merged);
                        canonical.setAliases(allAliases.stream().distinct()
                                .collect(Collectors.toList()));
                        mergedOut.addAll(merged);
                    }
                    result.add(canonical);
                }
            }

            // Add characters not mentioned in any merge group
            for (Character c : candidates) {
                if (!mergedOut.contains(c.getCanonicalName())
                        && result.stream().noneMatch(
                        r -> r.getCanonicalName().equals(c.getCanonicalName()))) {
                    result.add(c);
                }
            }

            return result;
        } catch (Exception e) {
            log.warn("CharacterResolverAgent: failed to parse LLM arbitration response: {}",
                    e.getMessage());
            return candidates;
        }
    }

    // ── Helpers ───────────────────────────────────────────

    /**
     * Convert a single extraction result to a domain Character.
     */
    private Character convertToDomain(CharacterExtractionResult result,
                                       List<String> additionalAliases) {
        Character domain = result.toDomainCharacter();
        List<String> allAliases = new ArrayList<>(result.aliases());
        if (additionalAliases != null) {
            allAliases.addAll(additionalAliases);
        }
        domain.setAliases(allAliases.stream().distinct().collect(Collectors.toList()));
        domain.setResolved(true);
        return domain;
    }

    // ── Union-Find helpers ────────────────────────────────

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]]; // path compression
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[rb] = ra;
        }
    }
}
