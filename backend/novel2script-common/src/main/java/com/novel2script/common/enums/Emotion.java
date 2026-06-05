package com.novel2script.common.enums;

import java.util.List;

/**
 * Emotions used for dialogue delivery and character state.
 *
 * <p>Each emotion carries a typical speech style pattern that
 * can be used to guide dialogue generation.
 */
public enum Emotion {

    ANGRY("愤怒", "语气激烈，短句为主，常用反问和感叹", 8),
    HAPPY("高兴", "语气轻快，语句流畅，多用肯定词", 5),
    SAD("悲伤", "语气低沉，句子断续，声调下沉", 6),
    CALM("平静", "语气平稳，逻辑清晰，用词克制", 2),
    FEARFUL("恐惧", "语气犹豫，常用省略号，句子不完整", 7),
    SURPRISED("惊讶", "语气突转，常用感叹词开头", 6),
    NEUTRAL("中性", "自然语气，无明显情绪色彩", 3),
    ANXIOUS("焦虑", "语速急促，重复用词，自我反问", 7),
    COLD("冷淡", "用词简洁，回避回应，句子极短", 4),
    EXCITED("兴奋", "语速快，句子长而连贯，充满感叹", 8),
    GENTLE("温柔", "语气柔和，多用叠词和委婉表达", 3),
    SARCASTIC("讽刺", "言不由衷，表面礼貌实含嘲讽", 5),
    PROUD("傲慢", "用词夸张，居高临下的语气", 6),
    DESPERATE("绝望", "语言破碎，充满否定，语气下沉", 9);

    private final String chineseLabel;
    private final String speechPattern;
    private final int intensity; // 1-10, emotional intensity level

    Emotion(String chineseLabel, String speechPattern, int intensity) {
        this.chineseLabel = chineseLabel;
        this.speechPattern = speechPattern;
        this.intensity = intensity;
    }

    public String getChineseLabel() {
        return chineseLabel;
    }

    public String getSpeechPattern() {
        return speechPattern;
    }

    public int getIntensity() {
        return intensity;
    }

    public boolean isHighIntensity() {
        return intensity >= 7;
    }

    public boolean isLowIntensity() {
        return intensity <= 3;
    }

    /**
     * Parse from Chinese label or enum name.
     */
    public static Emotion fromLabel(String text) {
        if (text == null || text.isBlank()) {
            return NEUTRAL;
        }
        String trimmed = text.trim();

        for (Emotion e : values()) {
            if (e.chineseLabel.equals(trimmed) || e.name().equalsIgnoreCase(trimmed)) {
                return e;
            }
        }

        // Fuzzy match
        if (trimmed.contains("怒") || trimmed.contains("生气") || trimmed.contains("愤")) return ANGRY;
        if (trimmed.contains("高兴") || trimmed.contains("开心") || trimmed.contains("快乐") || trimmed.contains("喜")) return HAPPY;
        if (trimmed.contains("悲") || trimmed.contains("伤心") || trimmed.contains("难过") || trimmed.contains("哭")) return SAD;
        if (trimmed.contains("平静") || trimmed.contains("冷静") || trimmed.contains("镇定")) return CALM;
        if (trimmed.contains("怕") || trimmed.contains("恐惧") || trimmed.contains("害怕") || trimmed.contains("恐")) return FEARFUL;
        if (trimmed.contains("惊") || trimmed.contains("意外")) return SURPRISED;
        if (trimmed.contains("焦虑") || trimmed.contains("紧张") || trimmed.contains("不安")) return ANXIOUS;
        if (trimmed.contains("冷淡") || trimmed.contains("冷漠")) return COLD;
        if (trimmed.contains("兴奋") || trimmed.contains("激动")) return EXCITED;
        if (trimmed.contains("温柔") || trimmed.contains("温和")) return GENTLE;
        if (trimmed.contains("讽刺") || trimmed.contains("嘲讽")) return SARCASTIC;
        if (trimmed.contains("傲慢") || trimmed.contains("骄傲")) return PROUD;
        if (trimmed.contains("绝望") || trimmed.contains("无望")) return DESPERATE;

        return NEUTRAL;
    }

    /**
     * Infer the most likely speech style from a list of personality traits.
     *
     * @param personality character personality keywords (e.g. ["冷静", "理智"])
     * @return a human-readable speech style description
     */
    public static String inferSpeechStyle(List<String> personality) {
        if (personality == null || personality.isEmpty()) {
            return "自然随和";
        }

        String joined = String.join(" ", personality);

        // Check for patterns and return matching style
        if (containsAny(joined, "冷静", "理智", "沉稳", "沉着")) {
            return "冷峻简洁，逻辑清晰，不轻易表露情感";
        }
        if (containsAny(joined, "热情", "开朗", "活泼", "外向")) {
            return "热情奔放，语句流畅，充满感染力";
        }
        if (containsAny(joined, "冷酷", "无情", "冷漠", "阴冷")) {
            return "言辞冰冷，字字如刀，话少意重";
        }
        if (containsAny(joined, "温柔", "善良", "体贴", "温和")) {
            return "语气柔和，善用关心之词，说话留有余地";
        }
        if (containsAny(joined, "傲慢", "自负", "骄傲", "狂妄")) {
            return "居高临下，用词夸张，不屑与常人多言";
        }
        if (containsAny(joined, "谨慎", "多疑", "警觉", "小心")) {
            return "言简意赅，话中有试探，不轻易表态";
        }
        if (containsAny(joined, "幽默", "风趣", "乐观")) {
            return "轻松诙谐，常带玩笑，善于化解紧张";
        }
        if (containsAny(joined, "暴躁", "冲动", "易怒", "粗鲁")) {
            return "语速快，用词直接，常带粗口或感叹";
        }
        if (containsAny(joined, "忧郁", "深沉", "内向", "沉默")) {
            return "话少而意味深长，喜欢用短句，常有沉默";
        }
        if (containsAny(joined, "智慧", "聪明", "狡猾", "机智")) {
            return "语带机锋，话中有话，善于反问和设套";
        }

        return "自然随和，根据情境调整语气";
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
