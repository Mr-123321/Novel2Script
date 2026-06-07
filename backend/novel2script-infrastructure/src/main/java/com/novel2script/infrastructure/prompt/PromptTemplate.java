package com.novel2script.infrastructure.prompt;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.novel2script.domain.prompt.FewShot;
import com.novel2script.domain.prompt.JsonSchema;
import com.novel2script.domain.prompt.ValidationRule;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A versioned, YAML-backed prompt template with Mustache rendering,
 * few-shot examples, and output schema validation.
 *
 * <p>Loaded by {@link PromptRegistry} from {@code resources/prompt/}.
 */
@Slf4j
@Getter
@Builder
public class PromptTemplate {

    private static final MustacheFactory MF = new DefaultMustacheFactory();

    /** Template identifier (e.g. "character-extraction"). */
    private final String name;

    /** Semantic version string (e.g. "2.0"). */
    private final String version;

    /** Human-readable description. */
    private final String description;

    /** Recommended model name (e.g. "claude-sonnet-4-6"). */
    @Builder.Default
    private String recommendedModel = "deepseek-chat";

    /** Recommended sampling temperature. */
    @Builder.Default
    private double temperature = 0.7;

    /** Recommended max output tokens. */
    @Builder.Default
    private int maxTokens = 4096;

    /** System prompt text (plain, no Mustache). */
    private final String system;

    /** Mustache user template string. */
    private final String userTemplate;

    /** Few-shot examples for in-context learning. */
    @Builder.Default
    private List<FewShot> fewShots = Collections.emptyList();

    /** Expected JSON output schema. */
    private JsonSchema outputSchema;

    /** Validation rules applied after AI response. */
    @Builder.Default
    private List<ValidationRule> validationRules = Collections.emptyList();

    /** Max retry count on validation failure. */
    @Builder.Default
    private int maxRetries = 3;

    // ── Rendering ───────────────────────────────────────

    /**
     * Render the full chat {@link Prompt} with the given variables.
     *
     * @param variables template variables accessible in Mustache
     * @return a {@link Prompt} ready to pass to {@code ChatModel.call()}
     */
    public Prompt render(Map<String, Object> variables) {
        String userContent = renderUserTemplate(variables);
        String systemContent = buildSystemWithFewShots();

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemContent));
        messages.add(new UserMessage(userContent));

        log.debug("Rendered prompt '{}' v{} — system={} chars, user={} chars",
                name, version, systemContent.length(), userContent.length());

        return new Prompt(messages);
    }

    /**
     * Render only the user template portion (for testing / debugging).
     */
    public String renderUserTemplate(Map<String, Object> variables) {
        if (userTemplate == null || userTemplate.isBlank()) {
            return "";
        }
        Mustache mustache = MF.compile(new StringReader(userTemplate), name);
        StringWriter sw = new StringWriter();
        mustache.execute(sw, variables);
        return sw.toString();
    }

    /**
     * Build the full system prompt by appending few-shot examples.
     */
    public String buildSystemWithFewShots() {
        StringBuilder sb = new StringBuilder();

        if (system != null) {
            sb.append(system);
        }

        if (!fewShots.isEmpty()) {
            sb.append("\n\n---\n以下是一些示例：\n\n");
            for (int i = 0; i < fewShots.size(); i++) {
                FewShot fs = fewShots.get(i);
                sb.append("【示例 ").append(i + 1).append("】\n");
                sb.append(fs.toConversationBlock()).append("\n\n");
            }
            sb.append("---\n请按照以上示例的格式输出。");
        }

        // Append output schema with STRONG JSON format instructions
        // Weaker models (qwen-turbo) need very explicit formatting guidance
        if (outputSchema != null) {
            sb.append("\n\n---\n## ⚠️ 输出格式要求（必须严格遵守）\n\n");
            sb.append("1. **只输出纯 JSON** — 不要输出任何解释、说明、问候语或额外文字\n");
            sb.append("2. **不要用 markdown 代码块** — 不要用 ```json 或 ``` 包裹输出\n");
            sb.append("3. **使用双引号** — 所有键和字符串值必须用英文双引号 \" 括起来，禁止使用单引号\n");
            sb.append("4. **不要尾随逗号** — 数组和对象的最后一个元素后面不能有逗号\n");
            sb.append("5. **确保完整闭合** — 所有括号 {} [] 必须正确匹配闭合\n");
            sb.append("6. **字符串中的特殊字符需要转义** — 如内容中有双引号请用 \\\" 转义\n");
            sb.append("7. **null 值直接写 null** — 不要写成 \"null\"（带引号的字符串）\n");
            sb.append("\n你的回复必须以 { 或 [ 开头，以 } 或 ] 结尾。除此之外什么都不要输出。");
        }

        return sb.toString();
    }

    /**
     * Build a simple prompt (just system + user) without Mustache rendering.
     * Useful for simple prompts that don't need template variables.
     */
    public Prompt renderSimple(String userContent) {
        return render(Map.of("content", userContent));
    }

    @Override
    public String toString() {
        return "PromptTemplate{name='" + name + "', version='" + version + "', model='" + recommendedModel + "'}";
    }
}
