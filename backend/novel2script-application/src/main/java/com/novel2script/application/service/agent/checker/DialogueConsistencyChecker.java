package com.novel2script.application.service.agent.checker;

import com.novel2script.application.service.agent.model.ConsistencyIssue;
import com.novel2script.application.service.agent.model.ConsistencyIssue.IssueType;
import com.novel2script.application.service.agent.model.ConsistencyIssue.Severity;
import com.novel2script.common.enums.Emotion;
import com.novel2script.domain.model.Character;
import com.novel2script.domain.model.Dialogue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Validates generated dialogue for character and setting consistency
 * using a combination of rule-based heuristics and content analysis.
 *
 * <h3>Checks performed</h3>
 * <ul>
 *   <li>Gender-appropriate language (female characters shouldn't use male-only terms)</li>
 *   <li>Personality consistency (calm characters rarely use exclamation marks)</li>
 *   <li>Line length limits (Chinese dialogue max ~50 characters)</li>
 *   <li>Repetition detection (same speaker repeating identical content)</li>
 *   <li>Exposition dump detection (dialogue that reads like narration)</li>
 *   <li>Emotion consistency with character personality</li>
 * </ul>
 */
@Slf4j
@Component
public class DialogueConsistencyChecker {

    // Gender-inappropriate terms for Chinese dialogue
    private static final Set<String> MALE_ONLY_TERMS = Set.of(
            "老子", "爷们", "本少爷", "本公子", "洒家"
    );
    private static final Set<String> FEMALE_ONLY_TERMS = Set.of(
            "老娘", "本小姐", "本姑娘", "人家"
    );

    // Exposition dump indicators
    private static final List<String> EXPOSITION_PATTERNS = List.of(
            "你知道的", "如你所知", "我们认识这么多年", "你还记得吗",
            "让我来告诉你", "事情是这样的", "话说回来"
    );

    // Maximum recommended line length for Chinese dialogue
    private static final int MAX_LINE_LENGTH = 50;

    // Characters-per-second estimate for spoken Chinese
    private static final double CHARS_PER_SECOND = 3.5;

    /**
     * Check a list of dialogues for consistency issues.
     *
     * @param dialogues   the generated dialogues to check
     * @param characters  character profiles for personality reference
     * @return list of issues found (empty if all good)
     */
    public List<ConsistencyIssue> check(List<Dialogue> dialogues, List<Character> characters) {
        if (dialogues == null || dialogues.isEmpty()) {
            return List.of();
        }

        List<ConsistencyIssue> issues = new ArrayList<>();
        Map<String, Character> charMap = buildCharMap(characters);
        Set<String> seenContent = new HashSet<>();

        for (Dialogue d : dialogues) {
            Character profile = charMap.get(d.getSpeaker());
            String content = d.getContent();

            if (content == null || content.isBlank()) {
                continue;
            }

            // 1. Gender-appropriate language check
            checkGenderTerms(d, profile, issues);

            // 2. Personality consistency check
            checkPersonalityConsistency(d, profile, issues);

            // 3. Line length check
            checkLineLength(d, issues);

            // 4. Repetition detection
            checkRepetition(d, seenContent, issues);

            // 5. Exposition dump detection
            checkExpositionDump(d, issues);

            // 6. Emotion consistency
            checkEmotionConsistency(d, profile, issues);
        }

        // Log summary
        long errors = issues.stream().filter(i -> i.getSeverity() == Severity.ERROR).count();
        long warnings = issues.stream().filter(i -> i.getSeverity() == Severity.WARNING).count();
        if (!issues.isEmpty()) {
            log.info("Dialogue consistency check: {} errors, {} warnings, {} info in {} lines",
                    errors, warnings, issues.size() - errors - warnings, dialogues.size());
        }

        return issues;
    }

    // ── Individual checks ────────────────────────────────

    private void checkGenderTerms(Dialogue d, Character profile, List<ConsistencyIssue> issues) {
        if (profile == null || profile.getGender() == null) return;
        String gender = profile.getGender();
        String content = d.getContent();

        // Male terms used by female characters
        if ("FEMALE".equalsIgnoreCase(gender)) {
            for (String term : MALE_ONLY_TERMS) {
                if (content.contains(term)) {
                    issues.add(ConsistencyIssue.builder()
                            .severity(Severity.ERROR)
                            .type(IssueType.GENDER_MISMATCH)
                            .dialogueSequence(d.getSequence())
                            .speaker(d.getSpeaker())
                            .description(String.format("女性角色使用了男性自称词\"%s\"", term))
                            .suggestion(String.format("建议替换为\"我\"或更符合角色的自称"))
                            .fragment(term)
                            .build());
                }
            }
        }

        // Female terms used by male characters
        if ("MALE".equalsIgnoreCase(gender)) {
            for (String term : FEMALE_ONLY_TERMS) {
                if (content.contains(term)) {
                    issues.add(ConsistencyIssue.builder()
                            .severity(Severity.ERROR)
                            .type(IssueType.GENDER_MISMATCH)
                            .dialogueSequence(d.getSequence())
                            .speaker(d.getSpeaker())
                            .description(String.format("男性角色使用了女性自称词\"%s\"", term))
                            .suggestion(String.format("建议替换为\"我\"或更符合角色的自称"))
                            .fragment(term)
                            .build());
                }
            }
        }
    }

    private void checkPersonalityConsistency(Dialogue d, Character profile, List<ConsistencyIssue> issues) {
        if (profile == null || profile.getPersonality() == null) return;

        List<String> personality = profile.getPersonality();
        String content = d.getContent();
        boolean isCalm = personality.stream().anyMatch(p ->
                p.contains("冷静") || p.contains("沉稳") || p.contains("沉默"));
        boolean isHot = personality.stream().anyMatch(p ->
                p.contains("暴躁") || p.contains("冲动") || p.contains("热情"));

        // Calm characters using excessive exclamation marks
        if (isCalm && !isHot) {
            long exclamationCount = content.chars().filter(c -> c == '！' || c == '!').count();
            if (exclamationCount >= 2) {
                issues.add(ConsistencyIssue.builder()
                        .severity(Severity.WARNING)
                        .type(IssueType.PERSONALITY_MISMATCH)
                        .dialogueSequence(d.getSequence())
                        .speaker(d.getSpeaker())
                        .description("冷静型角色使用了过多感叹号，与性格不符")
                        .suggestion("减少感叹号使用，改为句号或省略号体现沉稳语气")
                        .build());
            }
        }

        // Hot-tempered characters with too-formal phrasing
        if (isHot) {
            if (content.matches(".*[请您烦请劳烦].*")) {
                issues.add(ConsistencyIssue.builder()
                        .severity(Severity.WARNING)
                        .type(IssueType.PERSONALITY_MISMATCH)
                        .dialogueSequence(d.getSequence())
                        .speaker(d.getSpeaker())
                        .description("急躁型角色使用了过于礼貌的表达，与性格不符")
                        .suggestion("使用更直接、简洁的表达方式")
                        .build());
            }
        }
    }

    private void checkLineLength(Dialogue d, List<ConsistencyIssue> issues) {
        String content = d.getContent();
        if (content == null) return;

        // Count Chinese characters + punctuation as spoken length
        int spokenLength = content.replaceAll("[，。！？：；、\"\"''（）\\[\\]…—\\s]", "").length();

        if (spokenLength > MAX_LINE_LENGTH) {
            double seconds = spokenLength / CHARS_PER_SECOND;
            issues.add(ConsistencyIssue.builder()
                    .severity(Severity.WARNING)
                    .type(IssueType.LINE_TOO_LONG)
                    .dialogueSequence(d.getSequence())
                    .speaker(d.getSpeaker())
                    .description(String.format("对白过长（%d字，约%.1f秒），建议分拆为多句", spokenLength, seconds))
                    .suggestion("拆分为两个对话轮次，或精简为更口语化的表达")
                    .build());
        }
    }

    private void checkRepetition(Dialogue d, Set<String> seenContent, List<ConsistencyIssue> issues) {
        String content = d.getContent();
        if (content == null) return;

        // Normalize for comparison (remove punctuation variations)
        String normalized = content.replaceAll("[，。！？：；、\"\"''（）—…\\s]", "");
        if (!seenContent.add(normalized)) {
            issues.add(ConsistencyIssue.builder()
                    .severity(Severity.WARNING)
                    .type(IssueType.REPETITION)
                    .dialogueSequence(d.getSequence())
                    .speaker(d.getSpeaker())
                    .description("检测到重复的对白内容")
                    .suggestion("同一场景中避免完全相同的对白")
                    .fragment(content.length() > 30 ? content.substring(0, 30) + "..." : content)
                    .build());
        }
    }

    private void checkExpositionDump(Dialogue d, List<ConsistencyIssue> issues) {
        String content = d.getContent();
        if (content == null) return;

        for (String pattern : EXPOSITION_PATTERNS) {
            if (content.contains(pattern)) {
                issues.add(ConsistencyIssue.builder()
                        .severity(Severity.INFO)
                        .type(IssueType.EXPOSITION_DUMP)
                        .dialogueSequence(d.getSequence())
                        .speaker(d.getSpeaker())
                        .description(String.format("对白包含信息倾泻标志\"%s\"，这是不自然的对话模式", pattern))
                        .suggestion("通过动作、场景细节或角色反应来展现信息，而非通过对话直接说明")
                        .fragment(pattern)
                        .build());
                break;
            }
        }

        // Check for suspiciously narrative content (>80 chars, reads like a paragraph)
        if (content.length() > 80 && content.contains("因为") && content.contains("所以")) {
            issues.add(ConsistencyIssue.builder()
                    .severity(Severity.INFO)
                    .type(IssueType.EXPOSITION_DUMP)
                    .dialogueSequence(d.getSequence())
                    .speaker(d.getSpeaker())
                    .description("对白长度过长且包含因果逻辑链，建议精简")
                    .suggestion("拆分为多个对话轮次，让信息自然递进")
                    .build());
        }
    }

    private void checkEmotionConsistency(Dialogue d, Character profile, List<ConsistencyIssue> issues) {
        if (profile == null || profile.getPersonality() == null || d.getEmotion() == null) return;

        // If a character has a "cold" personality but dialogue is marked EXCITED
        boolean isCold = profile.getPersonality().stream().anyMatch(p ->
                p.contains("冷酷") || p.contains("冷漠") || p.contains("冷"));

        if (isCold && d.getEmotion().isHighIntensity() && d.getEmotion() != Emotion.ANGRY) {
            issues.add(ConsistencyIssue.builder()
                    .severity(Severity.WARNING)
                    .type(IssueType.EMOTION_INCONSISTENT)
                    .dialogueSequence(d.getSequence())
                    .speaker(d.getSpeaker())
                    .description(String.format("冷漠型角色标注了高情绪\"%s\"，可能不完全匹配", d.getEmotion().getChineseLabel()))
                    .suggestion("考虑调整为冷静型情绪或添加特殊的语境说明")
                    .build());
        }
    }

    // ── Helpers ───────────────────────────────────────────

    private Map<String, Character> buildCharMap(List<Character> characters) {
        Map<String, Character> map = new HashMap<>();
        if (characters != null) {
            for (Character c : characters) {
                map.put(c.getCanonicalName(), c);
                if (c.getAliases() != null) {
                    for (String alias : c.getAliases()) {
                        map.put(alias, c);
                    }
                }
            }
        }
        return map;
    }

    /**
     * Returns only the ERROR and WARNING level issues.
     */
    public List<ConsistencyIssue> getActionableIssues(List<ConsistencyIssue> issues) {
        return issues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR || i.getSeverity() == Severity.WARNING)
                .toList();
    }

    /**
     * Returns a summary report of all issues grouped by type.
     */
    public String summarize(List<ConsistencyIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return "对话一致性检查通过，未发现问题。";
        }

        Map<IssueType, Long> counts = new LinkedHashMap<>();
        for (ConsistencyIssue issue : issues) {
            counts.merge(issue.getType(), 1L, Long::sum);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("对话一致性检查报告：\n");
        sb.append(String.format("  总问题数: %d\n", issues.size()));
        for (Map.Entry<IssueType, Long> entry : counts.entrySet()) {
            sb.append(String.format("  %s: %d\n", entry.getKey().name(), entry.getValue()));
        }
        return sb.toString();
    }
}
