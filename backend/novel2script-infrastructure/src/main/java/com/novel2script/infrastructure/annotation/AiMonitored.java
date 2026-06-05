package com.novel2script.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an AI model invocation that should be monitored.
 *
 * <p>When applied, {@link com.novel2script.infrastructure.aop.AiMonitorAspect}
 * automatically records the following metrics to the {@code prompt_audits} table:
 * <ul>
 *   <li>Prompt name (from the annotation's value)</li>
 *   <li>Model name</li>
 *   <li>Input / output token counts</li>
 *   <li>Response latency in milliseconds</li>
 *   <li>Success / failure status</li>
 *   <li>Retry count</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 *   @AiMonitored("character-extraction")
 *   public List<Character> extract(List<Chapter> chapters) {
 *       ChatResponse response = model.call(new Prompt(prompt));
 *       return parseResponse(response);
 *   }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiMonitored {

    /**
     * The prompt template name, used as the {@code prompt_name} column value.
     */
    String value();

    /**
     * Optional prompt version tag.
     */
    String version() default "1.0";
}
