package com.novel2script.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Custom {@link HttpMessageConverter} that serializes any non-String object
 * to JSON for {@code text/event-stream} (SSE) responses.
 *
 * <h3>Problem it solves</h3>
 * When calling {@code emitter.send(SseEmitter.event().data(myLinkedHashSet))},
 * Spring can't find a converter for {@code text/event-stream} that handles
 * complex objects (collections, maps, POJOs). This converter fills that gap
 * by using Jackson to serialize objects to JSON bytes.
 *
 * <h3>Registration</h3>
 * Registered via {@link WebMvcConfig} with lower priority than
 * {@code StringHttpMessageConverter}, so Strings still go through the
 * built-in converter.
 *
 * <h3>Output format</h3>
 * Writes UTF-8 JSON bytes. Spring's {@code SseEmitter} infrastructure
 * handles adding the {@code data:} prefix and {@code \n\n} delimiters.
 */
public class SseJsonConverter implements HttpMessageConverter<Object> {

    static final MediaType TEXT_EVENT_STREAM = new MediaType("text", "event-stream");

    private final ObjectMapper objectMapper;

    public SseJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── Write support ──────────────────────────────────

    @Override
    public boolean canWrite(@NonNull Class<?> clazz, @Nullable MediaType mediaType) {
        // Only activate for SSE endpoint responses
        // Skip String — StringHttpMessageConverter handles those
        return TEXT_EVENT_STREAM.equals(mediaType)
                && !String.class.isAssignableFrom(clazz);
    }

    @Override
    @NonNull
    public List<MediaType> getSupportedMediaTypes() {
        return Collections.singletonList(TEXT_EVENT_STREAM);
    }

    @Override
    @NonNull
    public List<MediaType> getSupportedMediaTypes(@NonNull Class<?> clazz) {
        return getSupportedMediaTypes();
    }

    @Override
    public void write(@NonNull Object obj, @Nullable MediaType contentType,
                      @NonNull org.springframework.http.HttpOutputMessage outputMessage)
            throws IOException {
        byte[] jsonBytes = objectMapper.writeValueAsBytes(obj);
        outputMessage.getBody().write(jsonBytes);
        outputMessage.getBody().flush();
    }

    // ── Read not supported ─────────────────────────────

    @Override
    public boolean canRead(@NonNull Class<?> clazz, @Nullable MediaType mediaType) {
        return false;
    }

    @Override
    @NonNull
    public Object read(@NonNull Class<?> clazz,
                       @NonNull org.springframework.http.HttpInputMessage inputMessage) {
        throw new UnsupportedOperationException("SSE reading is not supported");
    }
}
