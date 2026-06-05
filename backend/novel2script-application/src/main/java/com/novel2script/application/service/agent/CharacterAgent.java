package com.novel2script.application.service.agent;

import com.novel2script.common.enums.TaskType;
import com.novel2script.common.enums.CharacterRoleType;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Character;
import com.novel2script.infrastructure.annotation.AiMonitored;
import com.novel2script.infrastructure.config.AiModelRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI agent that extracts characters from novel chapters.
 *
 * <p>Uses {@link AiModelRouter} to automatically select the best
 * AI model for creative extraction tasks (defaulting to Claude).
 */
@Slf4j
@Service
public class CharacterAgent {

    private final AiModelRouter router;

    public CharacterAgent(AiModelRouter router) {
        this.router = router;
    }

    /**
     * Extract characters from a list of novel chapters.
     *
     * @param chapters the parsed chapters to analyze
     * @return a list of extracted {@link Character} entities
     */
    @AiMonitored(value = "character-extraction", version = "1.0")
    public List<Character> extract(List<Chapter> chapters) {
        ChatModel model = router.route(TaskType.CHARACTER_EXTRACTION);
        String prompt = buildPrompt(chapters);

        log.info("CharacterAgent extracting from {} chapters using model={}", chapters.size(), model);

        ChatResponse response = model.call(new Prompt(new UserMessage(prompt)));
        return parseResponse(response, chapters);
    }

    // ── Prompt construction ───────────────────────────────

    private String buildPrompt(List<Chapter> chapters) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                你是一位专业的文学分析专家。请从以下小说章节中提取所有角色信息。

                对于每个角色，请提取：
                - 姓名（规范名）
                - 别名（如果有）
                - 性别
                - 大致年龄段
                - 角色类型（主角/反派/配角）
                - 性格特征（关键词列表）
                - 简短描述

                请以 JSON 格式返回，结构如下：
                ```json
                [{"canonicalName":"...","aliases":[...],"gender":"...","ageRange":"...","roleType":"...","personality":[...],"description":"..."}]
                ```

                以下是小说内容：
                """);
        for (Chapter ch : chapters) {
            sb.append("\n--- ").append(ch.getDisplayName()).append(" ---\n");
            sb.append(ch.getContent());
        }
        return sb.toString();
    }

    // ── Response parsing ──────────────────────────────────

    private List<Character> parseResponse(ChatResponse response, List<Chapter> chapters) {
        String content = response.getResult().getOutput().getText();
        List<Character> characters = new ArrayList<>();

        // Naive JSON extraction: find the first JSON array in the response
        Pattern pattern = Pattern.compile("\\[\\s*\\{.*?}\\s*]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        if (!matcher.find()) {
            log.warn("CharacterAgent: no JSON array found in AI response");
            return characters;
        }

        String jsonArray = matcher.group();
        // Simple parsing — in production, use Jackson ObjectMapper
        jsonArray = jsonArray.replaceAll("\"", ""); // strip quotes for simple parsing

        // Parse each character block
        Pattern charPattern = Pattern.compile("\\{[^}]+}");
        Matcher charMatcher = charPattern.matcher(jsonArray);

        while (charMatcher.find()) {
            String block = charMatcher.group();
            Character ch = parseCharacterBlock(block);
            if (ch != null) {
                characters.add(ch);
            }
        }

        log.info("CharacterAgent extracted {} characters from AI response", characters.size());
        return characters;
    }

    private Character parseCharacterBlock(String block) {
        try {
            String name = extractField(block, "canonicalName");
            if (name == null || name.isBlank()) {
                return null;
            }

            String gender = extractField(block, "gender");
            String ageRange = extractField(block, "ageRange");
            String description = extractField(block, "description");
            String roleStr = extractField(block, "roleType");

            CharacterRoleType roleType = CharacterRoleType.SUPPORTING;
            if (roleStr != null) {
                if (roleStr.contains("主角") || roleStr.contains("PROTAGONIST")) {
                    roleType = CharacterRoleType.PROTAGONIST;
                } else if (roleStr.contains("反派") || roleStr.contains("ANTAGONIST")) {
                    roleType = CharacterRoleType.ANTAGONIST;
                }
            }

            return Character.builder()
                    .canonicalName(name)
                    .gender(gender)
                    .ageRange(ageRange)
                    .description(description)
                    .roleType(roleType)
                    .personality(new ArrayList<>())
                    .aliases(new ArrayList<>())
                    .build();
        } catch (Exception e) {
            log.debug("Failed to parse character block: {}", e.getMessage());
            return null;
        }
    }

    private String extractField(String block, String fieldName) {
        Pattern p = Pattern.compile(fieldName + "\\s*:\\s*([^,\\n}]+)");
        Matcher m = p.matcher(block);
        if (m.find()) {
            return m.group(1).trim().replaceAll("^\"|\"$", "");
        }
        return null;
    }
}
