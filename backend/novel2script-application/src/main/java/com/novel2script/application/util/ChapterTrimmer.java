package com.novel2script.application.util;

/**
 * Utility for trimming chapter content before feeding it into AI prompts.
 *
 * <h3>Why</h3>
 * Full chapter content (often 30k+ characters across all chapters) causes
 * enormous prompt token counts, leading to slow AI responses (48-57 seconds
 * for character/scene extraction). Most of that text is narration detail
 * that isn't needed for structured extraction tasks.
 *
 * <h3>Strategy</h3>
 * Take the beginning and end of each chapter — character introductions and
 * scene transitions typically happen at chapter boundaries. This preserves
 * the most information-dense parts while cutting total input by ~80%.
 */
public final class ChapterTrimmer {

    private ChapterTrimmer() {
        // utility class
    }

    /**
     * Trim a chapter's content to first {@code headChars} + last {@code tailChars} characters.
     * If the content is shorter than head+tail, returns it unchanged.
     * Inserts a truncation marker between head and tail sections.
     *
     * @param content   the full chapter content (may be null)
     * @param headChars characters to keep from the beginning
     * @param tailChars characters to keep from the end
     * @return trimmed content string
     */
    public static String trim(String content, int headChars, int tailChars) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        int total = headChars + tailChars;
        if (content.length() <= total) {
            return content;
        }
        String head = content.substring(0, headChars);
        String tail = content.substring(content.length() - tailChars);
        return head + "\n\n[… 中间内容已省略，共 " + (content.length() - total) + " 字符 …]\n\n" + tail;
    }

    /**
     * Trim for character extraction: wider window (first 800 + last 400 chars)
     * because we need to catch character names, aliases, and relationship cues.
     */
    public static String trimForCharacterExtraction(String content) {
        return trim(content, 800, 400);
    }

    /**
     * Trim for scene segmentation: narrower window (first 600 + last 300 chars)
     * because scene boundaries are usually at chapter start/end transitions.
     */
    public static String trimForSceneSegmentation(String content) {
        return trim(content, 600, 300);
    }
}
