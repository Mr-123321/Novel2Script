package com.novel2script.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Spring MVC configuration for SSE content negotiation.
 *
 * <p>Registers {@link SseJsonConverter} so that non-String objects
 * (collections, maps, POJOs) can be auto-serialized as JSON when sent
 * via {@code SseEmitter.send()}.
 *
 * <h3>Converter order (first-match-wins)</h3>
 * <ol>
 *   <li>{@code StringHttpMessageConverter} — handles plain strings</li>
 *   <li>{@code SseJsonConverter} ← <b>ours</b> — handles everything else as JSON</li>
 *   <li>Other standard converters (Jackson, etc.) — fallback for non-SSE</li>
 * </ol>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ObjectMapper objectMapper;

    public WebMvcConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Insert SseJsonConverter right after StringHttpMessageConverter.
        // This ensures:
        //   - String payloads → StringHttpMessageConverter (no change)
        //   - Collection/Map/POJO payloads → SseJsonConverter writes JSON bytes
        int insertIndex = 0;
        for (int i = 0; i < converters.size(); i++) {
            if (converters.get(i) instanceof StringHttpMessageConverter) {
                insertIndex = i + 1;
                break;
            }
        }
        converters.add(insertIndex, new SseJsonConverter(objectMapper));
    }
}
