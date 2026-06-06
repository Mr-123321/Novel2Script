package com.novel2script.application.service.agent;

import com.novel2script.application.service.agent.model.CharacterExtractionResult;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Post-processing rule-based filter that removes noise entities — non-name
 * text fragments that the LLM mistakenly treats as character names.
 *
 * <h3>Motivation</h3>
 * LLMs sometimes extract sentence fragments like "到来，平静应" or "平静地说道"
 * as character names, polluting the character list and causing downstream errors
 * in scene segmentation and dialogue attribution.
 *
 * <h3>Design</h3>
 * Pure deterministic rules — zero LLM cost, zero latency impact.
 * The filter is deliberately <b>conservative</b>: it only rejects entries that
 * are clearly not real person names. Borderline cases are kept.
 *
 * <h3>Filter tiers</h3>
 * <ol>
 *   <li><b>Hard reject</b>: punctuation in name, empty/blank, 6+ chars</li>
 *   <li><b>Strong reject</b>: verb/speech endings, sentence-starting adverbs,
 *       common non-name nouns, pure quantifier/function-word patterns</li>
 *   <li><b>Weak reject</b>: no surname for 2-3 char name + description
 *       missing or too short (combined signal)</li>
 * </ol>
 */
@Slf4j
public final class CharacterNameFilter {

    private CharacterNameFilter() {
        // utility class
    }

    // ── Punctuation detection ─────────────────────────────

    /**
     * Matches any Unicode punctuation ({@code \p{P}}), symbol ({@code \p{S}}),
     * or whitespace ({@code \s}) — none of which belong in a person's name.
     *
     * <p>U+00B7 MIDDLE DOT is handled separately because it appears in
     * valid transliterated names (e.g. "卡尔·马克思").
     */
    private static final Pattern PUNCTUATION_PATTERN =
            Pattern.compile("[\\p{P}\\p{S}\\s]");

    // ── Common Chinese surnames ────────────────────────────

    /**
     * Top ~120 common Chinese surnames including compound surnames (复姓).
     * Used as a positive signal, NOT as a hard requirement —
     * nicknames, titles, and rare surnames are still valid names.
     */
    private static final Set<String> COMMON_SURNAMES = Set.of(
            // Top 100 single-char surnames
            "李", "王", "张", "刘", "陈", "杨",
            "赵", "黄", "周", "吴",
            "徐", "孙", "胡", "朱", "高", "林",
            "何", "郭", "马", "罗",
            "梁", "宋", "郑", "谢", "韩", "唐",
            "冯", "于", "董", "萧",
            "程", "曹", "袁", "邓", "许", "傅",
            "沈", "曾", "彭", "吕",
            "苏", "卢", "蒋", "蔡", "贾", "丁",
            "魏", "薛", "叶", "阎",
            "余", "潘", "杜", "戴", "夏", "钟",
            "汪", "田", "任", "姜",
            "范", "方", "石", "姚", "谭", "廖",
            "邹", "熊", "金", "陆",
            "郝", "孔", "白", "崔", "康", "毛",
            "邱", "秦", "江", "史",
            "顾", "侯", "邵", "孟", "龙", "万",
            "段", "雷", "钱", "汤",
            "尹", "易", "常", "武", "乔", "贺",
            "赖", "龚", "文", "庞",
            // Common compound surnames
            "欧阳", "太史", "端木", "上官",
            "司马", "东方", "独孤", "南宫",
            "万俟", "闻人", "夏侯", "诸葛",
            "尉迟", "公羊", "赫连", "澹台",
            "皇甫", "宗政", "濮阳", "公冶",
            "太叔", "申屠", "公孙", "慕容",
            "仲孙", "钟离", "长孙", "宇文",
            "司徒", "鲜于", "司空", "闾丘",
            "子车", "亓官", "司寇", "巫马",
            "公西", "漆雕", "乐正", "壤驴",
            "公良", "拓跋", "夹谷", "宰父",
            "谷梁", "段干", "百里", "东郭",
            "南门", "呼延", "羊舌", "微生",
            "梁丘", "左丘", "东门", "西门"
    );

    // ── Words that strongly indicate non-name text ─────────

    /**
     * Common verbs that should NEVER appear as a person's name.
     */
    private static final Set<String> COMMON_VERBS = Set.of(
            "到来", "离开", "走过", "走向",
            "看向", "望向", "听到", "看见",
            "说道", "问道", "答道", "回道",
            "喊道", "叫道", "笑着", "哭着",
            "觉得", "感到", "想到", "知道",
            "发现", "出现", "消失", "变成",
            "回来", "回去", "过来", "过去",
            "进来", "进去", "出来", "出去",
            "上来", "上去", "下来", "下去",
            "起来", "坐下", "站起", "转身",
            "抬头", "低头", "点头", "摇头",
            "摆手", "挥手", "伸手", "收回",
            "睁开", "闭眼", "看去", "望去",
            "传来", "响起", "走进", "走出"
    );

    /**
     * Common adjectives / adverbs that signal descriptive text.
     */
    private static final Set<String> COMMON_ADJECTIVES = Set.of(
            "平静", "安静", "冷静", "热闹",
            "冷清", "温柔", "冷漠",
            "愤怒", "生气", "高兴", "快乐",
            "悲伤", "难过", "忧郁", "兴奋",
            "紧张", "轻松",
            "慢慢", "缓缓", "忽然", "突然",
            "轻轻", "悄悄",
            "渐渐", "逐渐", "微微", "淡淡",
            "深深", "远远", "静静", "默默",
            "冷冷", "狠狠", "重重",
            "暖暖", "凉凉", "明明", "暗暗"
    );

    /**
     * Common sentence connectors and function words.
     */
    private static final Set<String> FUNCTION_WORDS = Set.of(
            "所以", "因为", "但是", "虽然",
            "然而", "于是", "然后", "接着",
            "不过", "而且", "或者", "如果",
            "只要", "只有", "无论", "不管",
            "这个", "那个", "哪个",
            "什么", "怎么", "怎样", "这么",
            "那么",
            "这里", "那里", "哪里",
            "这边", "那边",
            "今天", "明天", "昨天",
            "现在", "刚才", "已经", "正在",
            "将要", "可以", "应该", "必须",
            "没有", "不是", "不会", "不能",
            "不要", "不用", "不敢"
    );

    /**
     * Body part characters — if a name contains a body part character
     * AND an action character, it's likely a description, not a name.
     * e.g., "睫毛颤动", "手指轻弹", "垂落的睫毛"
     */
    private static final Set<Character> BODY_PART_CHARS = Set.of(
            '头', '眼', '睛', '耳', '鼻', '嘴', '唇', '舌', '牙',
            '脸', '额', '眉', '睫', '手', '掌', '指', '拳', '臂', '肘',
            '肩', '颈', '胸', '背', '腰', '腹', '腿', '膝', '脚', '趾',
            '身', '躯', '体', '肤', '肌', '骨', '血', '泪', '汗', '毛', '发'
    );

    /**
     * Common nouns that refer to groups/roles, not individual names.
     */
    private static final Set<String> NON_NAME_NOUNS = Set.of(
            "众人", "大家", "所有人",
            "没人", "有人", "某人", "任何人",
            "一个人", "两个人", "几个人",
            "一群人", "年轻人", "中年人",
            "老年人", "男人", "女人",
            "男孩", "女孩", "少年", "少女",
            "弟子", "徒弟", "师父", "掌门",
            "长老", "门主", "宗主",
            "守卫", "侍女", "仆人", "路人",
            "百姓", "士兵", "将军",
            "皇帝", "皇后", "太子", "公主",
            "王爷", "大臣", "太监"
    );

    // ── Character patterns for noise detection ─────────────

    /**
     * Characters that, when appearing at the end of a 2-4 char "name",
     * strongly suggest a speech/action tag rather than a person.
     * e.g., "平静道" (道 = said), "笑着说" (说 = said)
     */
    private static final Set<Character> SPEECH_ACTION_SUFFIX_CHARS = Set.of(
            '道', '说', '问', '答', '喊', '叫',
            '讲', '谈', '喝',
            '笑', '哭', '叹', '呼', '骂', '吼',
            '嚷', '言', '语'
    );

    /**
     * Characters that suggest a descriptive/verbal prefix.
     */
    private static final Set<String> ADVERBIAL_STARTS = Set.of(
            "忽", "突", "猛", "急", "快", "慢",
            "轻", "重", "悄",
            "微", "略", "稍", "渐", "骤", "徐",
            "缓", "疾", "速"
    );

    // ── Public API ────────────────────────────────────────

    /**
     * Filter a list of extracted characters, removing entries that are
     * clearly noise text rather than real person names.
     *
     * @param characters the raw extraction results from AI
     * @return filtered list with noise entries removed (never null)
     */
    public static List<CharacterExtractionResult> filter(
            List<CharacterExtractionResult> characters) {
        if (characters == null || characters.isEmpty()) {
            return Collections.emptyList();
        }

        List<CharacterExtractionResult> kept = new ArrayList<>();
        List<String> removed = new ArrayList<>();

        for (CharacterExtractionResult c : characters) {
            String reason = getNoiseReason(c);
            if (reason == null) {
                kept.add(c);
            } else {
                removed.add(c.name() + " (" + reason + ")");
            }
        }

        if (!removed.isEmpty()) {
            log.info("CharacterNameFilter: removed {} noise entries: {}",
                    removed.size(), removed);
        }

        return kept;
    }

    /**
     * Check whether a single extraction result is noise.
     *
     * @param character the extracted character to check
     * @return {@code true} if this entry should be filtered out
     */
    public static boolean isNoise(CharacterExtractionResult character) {
        return getNoiseReason(character) != null;
    }

    /**
     * Quick check on a raw name string — useful for filtering speaker names
     * extracted from narration text (e.g., in {@code DialogueAgent}).
     * Only applies Tier 1 (hard reject) and Tier 2 (strong reject) checks,
     * skipping Tier 3 (which requires description context).
     *
     * @param name the raw name string to check
     * @return {@code true} if this string is clearly not a person's name
     */
    // Common adverbial starters that indicate action descriptions, not names
    private static final java.util.Set<Character> ADVERBIAL_STARTERS = java.util.Set.of(
            '忽', '突', '猛', '骤', '急', '缓', '轻', '重', '悄', '暗',
            '偷', '微', '渐', '徐', '疾', '霍', '猝', '遽'
    );

    // Numeral/quantity starters that indicate non-names
    private static final java.util.Set<Character> NUMERAL_STARTERS = java.util.Set.of(
            '一', '两', '三', '四', '五', '六', '七', '八', '九', '十',
            '几', '数', '多', '少', '各', '每', '某', '众', '全', '半'
    );

    public static boolean isNoiseQuick(String name) {
        if (name == null || name.isBlank()) return true;
        String t = name.trim();
        // Tier 1: hard reject
        if (PUNCTUATION_PATTERN.matcher(t).find()) {
            String withoutDot = t.replace("·", "");
            if (PUNCTUATION_PATTERN.matcher(withoutDot).find()) return true;
        }
        if (t.length() >= 6) return true;
        // Check adverbial starters: 忽/突/猛/骤... + verb = action description
        if (t.length() >= 2 && ADVERBIAL_STARTERS.contains(t.charAt(0))) return true;
        // Check numeral/quantity starters: 一/两/几/众... = not a name
        if (t.length() >= 1 && NUMERAL_STARTERS.contains(t.charAt(0))) return true;
        // Tier 2: strong reject (speech suffix + word lists only)
        if (t.length() >= 2 && t.length() <= 5) {
            char last = t.charAt(t.length() - 1);
            if (SPEECH_ACTION_SUFFIX_CHARS.contains(last)) {
                String stem = t.substring(0, t.length() - 1);
                String resolved = stripAdverbialParticles(stem);
                if (resolved.length() >= 1
                        && (COMMON_ADJECTIVES.contains(resolved)
                            || COMMON_VERBS.contains(resolved))) return true;
            }
        }
        if (t.length() >= 3 && !hasCommonSurname(t)) {
            for (int i = 0; i < t.length(); i++) {
                if (BODY_PART_CHARS.contains(t.charAt(i))) return true;
            }
        }
        if (COMMON_VERBS.contains(t)) return true;
        if (COMMON_ADJECTIVES.contains(t)) return true;
        if (FUNCTION_WORDS.contains(t)) return true;
        if (NON_NAME_NOUNS.contains(t)) return true;
        return false;
    }

    // ── Noise detection logic ──────────────────────────────

    /**
     * Determine why a character entry is noise, or {@code null} if it's valid.
     * Each tier is checked in order; the first matching tier returns its reason.
     */
    static String getNoiseReason(CharacterExtractionResult character) {
        if (character == null) {
            return "null entry";
        }

        String name = character.name();
        if (name == null || name.isBlank()) {
            return "blank name";
        }

        String trimmed = name.trim();

        // ── Tier 1: Hard reject — definitely not a name ──

        // 1a. Contains punctuation (e.g., "到来，平静应")
        //     Exception: U+00B7 MIDDLE DOT is allowed for
        //     transliterated names like "卡尔·马克思"
        if (PUNCTUATION_PATTERN.matcher(trimmed).find()) {
            // Allow middle dot · (U+00B7) in transliterated names
            String withoutMiddleDot = trimmed.replace("·", "");
            if (PUNCTUATION_PATTERN.matcher(withoutMiddleDot).find()) {
                return "contains punctuation";
            }
        }

        // 1b. Too long: Chinese person names are <=4 chars;
        //     titles/nicknames can be 5 (e.g., "幽冥老人"), but 6+ is noise
        if (trimmed.length() >= 6) {
            return "too long (" + trimmed.length() + " chars)";
        }

        // 1c. Pure ASCII in Chinese novel context — suspicious
        //     unless it looks like a known transliterated name
        if (trimmed.matches("[A-Za-z0-9]+") && trimmed.length() <= 3
                && !looksLikeTransliteratedName(trimmed)) {
            return "ASCII non-name";
        }

        // ── Tier 2: Strong reject — sentence fragment patterns ──

        // 2a. Ends with a speech/action suffix character
        //     e.g., "平静道", "笑着说", "问道"
        //     Exception: legitimate names like "陈道" (surname 陈 + given 道)
        //     or "悟道" (Dharma name with rich description) are kept.
        if (trimmed.length() >= 2 && trimmed.length() <= 5) {
            char lastChar = trimmed.charAt(trimmed.length() - 1);
            if (SPEECH_ACTION_SUFFIX_CHARS.contains(lastChar)) {
                // Check if the stem is a known adjective/verb
                // Also handle adverbial particles: e.g., "狠狠地说"
                // → strip '说' → "狠狠地" → strip '地' → "狠狠"
                String stem = trimmed.substring(0, trimmed.length() - 1);
                String resolvedStem = stripAdverbialParticles(stem);
                if (resolvedStem.length() >= 1
                        && (COMMON_ADJECTIVES.contains(resolvedStem)
                            || COMMON_VERBS.contains(resolvedStem))) {
                    return "adjective/verb + speech suffix '"
                            + lastChar + "'";
                }
                // Otherwise only reject if no surname AND weak description
                if (!hasCommonSurname(trimmed)
                        && hasWeakDescription(character)) {
                    return "ends with speech/action char '" + lastChar + "'";
                }
            }
        }

        // 2b. Name is exactly a common verb/adjective/function word
        if (COMMON_VERBS.contains(trimmed)) {
            return "is a common verb";
        }
        if (COMMON_ADJECTIVES.contains(trimmed)) {
            return "is a common adjective/adverb";
        }
        if (FUNCTION_WORDS.contains(trimmed)) {
            return "is a function word";
        }
        if (NON_NAME_NOUNS.contains(trimmed)) {
            return "is a common noun, not a person name";
        }

        // 2c. Starts with a quantifier/function word prefix
        //     e.g., "一个", "这个", "那个", "没有"
        for (String fw : FUNCTION_WORDS) {
            if (trimmed.startsWith(fw) && trimmed.length() > fw.length()) {
                return "starts with function word '" + fw + "'";
            }
        }

        // 2d. Name is a common verb/adjective + 地/得/的/了/着/过
        //     e.g., "平静地" (calmly)
        if (trimmed.length() >= 3 && trimmed.length() <= 5) {
            char[] particles = {'地', '得', '的',
                                '了', '着', '过'};
            for (char suffix : particles) {
                if (trimmed.charAt(trimmed.length() - 1) == suffix) {
                    String stem = trimmed.substring(0, trimmed.length() - 1);
                    if (COMMON_ADJECTIVES.contains(stem)
                            || COMMON_VERBS.contains(stem)) {
                        return "adjective/verb + '" + suffix + "'";
                    }
                }
            }
        }

        // 2e. Contains body part character → likely description, not name
        //     e.g., "睫毛颤动", "垂落的睫毛", "手指轻弹"
        //     Exception: names with common surnames (e.g., "林眉" is a real name)
        if (trimmed.length() >= 3 && !hasCommonSurname(trimmed)) {
            boolean hasBodyPart = false;
            for (int i = 0; i < trimmed.length(); i++) {
                if (BODY_PART_CHARS.contains(trimmed.charAt(i))) {
                    hasBodyPart = true;
                    break;
                }
            }
            if (hasBodyPart) {
                return "contains body part character — likely description";
            }
        }

        // ── Tier 3: Weak reject — combined signals ──

        // 3a. 4-5 char name with no known surname AND weak description
        if (trimmed.length() >= 4 && trimmed.length() <= 5) {
            if (!hasCommonSurname(trimmed) && hasWeakDescription(character)) {
                return "long name without surname + weak description";
            }
        }

        // 3b. 2-3 char name: adverbial start + no surname + weak description
        if (trimmed.length() >= 2 && trimmed.length() <= 3) {
            String firstChar = String.valueOf(trimmed.charAt(0));
            if (ADVERBIAL_STARTS.contains(firstChar)
                    && !hasCommonSurname(trimmed)
                    && hasWeakDescription(character)) {
                return "adverbial start + no surname + weak description";
            }
        }

        // 3c. Single-char name with weak description
        //     (single-char names exist but are rare)
        if (trimmed.length() == 1 && hasWeakDescription(character)) {
            String desc = character.description();
            if (desc == null || desc.trim().length() < 10) {
                return "single-char name with no meaningful description";
            }
        }

        return null; // passes all checks — not noise
    }

    // ── Helper methods ─────────────────────────────────────

    /**
     * Recursively strip common adverbial particles (地, 得, 的)
     * from the end of a word to reveal the underlying adjective/verb stem.
     * e.g., "狠狠地" → "狠狠", "平静地" → "平静"
     */
    private static String stripAdverbialParticles(String word) {
        if (word == null || word.length() < 2) return word;
        String result = word;
        char[] particles = {'地', '得', '的'};
        boolean changed;
        do {
            changed = false;
            for (char p : particles) {
                if (result.length() > 1
                        && result.charAt(result.length() - 1) == p) {
                    result = result.substring(0, result.length() - 1);
                    changed = true;
                    break;
                }
            }
        } while (changed && result.length() >= 1);
        return result;
    }

    /**
     * Check if the name starts with a common Chinese surname.
     * Handles both single-char (e.g., "林") and compound (e.g., "慕容") surnames.
     */
    static boolean hasCommonSurname(String name) {
        if (name == null || name.length() < 2) return false;

        // Check compound surname first (2 chars)
        if (name.length() >= 2) {
            String firstTwo = name.substring(0, 2);
            if (COMMON_SURNAMES.contains(firstTwo)) {
                return true;
            }
        }

        // Check single-char surname
        String firstChar = name.substring(0, 1);
        return COMMON_SURNAMES.contains(firstChar);
    }

    /**
     * Check if the character has a suspiciously weak description —
     * missing, blank, very short, or just repeats the name.
     */
    static boolean hasWeakDescription(CharacterExtractionResult character) {
        String desc = character.description();
        if (desc == null || desc.isBlank()) {
            return true;
        }

        String trimmedDesc = desc.trim();

        // Description is too short (< 5 chars)
        if (trimmedDesc.length() < 5) {
            return true;
        }

        // Description just repeats the name (e.g., name="到来" desc="到来的人")
        String name = character.name();
        if (name != null && trimmedDesc.length() <= name.length() + 3) {
            if (trimmedDesc.contains(name)) {
                return true;
            }
        }

        // Description is a single extremely generic word
        Set<String> genericDescs = Set.of(
                "主角", "配角", "反派",
                "路人", "角色", "人物",
                "未知", "不详");
        if (genericDescs.contains(trimmedDesc)) {
            return true;
        }

        return false;
    }

    /**
     * Quick check for plausible transliterated foreign names.
     * e.g., "Tom", "Sara", "Lee"
     */
    private static boolean looksLikeTransliteratedName(String name) {
        if (name == null || name.length() < 2 || name.length() > 15) {
            return false;
        }
        // Must start with uppercase letter and be all alphabetic
        return Character.isUpperCase(name.charAt(0))
                && name.chars().allMatch(c -> Character.isLetter(c));
    }
}
