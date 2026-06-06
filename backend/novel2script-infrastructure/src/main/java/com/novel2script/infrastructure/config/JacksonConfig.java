package com.novel2script.infrastructure.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.novel2script.common.enums.CharacterRoleType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration — registers custom deserializers for tolerant
 * AI model output handling.
 */
@Configuration
public class JacksonConfig {

    /**
     * Register {@link CharacterRoleTypeDeserializer} so that unknown
     * role types from AI responses (e.g. "DEUTERAGONIST") are handled
     * gracefully instead of throwing deserialization errors.
     */
    @Bean
    public Module characterRoleTypeModule() {
        SimpleModule module = new SimpleModule("CharacterRoleTypeModule");
        module.addDeserializer(CharacterRoleType.class, new CharacterRoleTypeDeserializer());
        return module;
    }
}
