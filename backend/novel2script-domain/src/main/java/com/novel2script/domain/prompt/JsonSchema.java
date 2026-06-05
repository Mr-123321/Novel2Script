package com.novel2script.domain.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents the expected JSON Schema for AI output validation.
 * Supports nested object definitions via {@code $ref} references.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JsonSchema {

    /** JSON Schema type: "object", "array", "string", etc. */
    private String type;

    /** Object property definitions (when type = "object"). */
    private Map<String, PropertyDef> properties;

    /** Array item schema (when type = "array"). */
    private JsonSchema items;

    /** Which properties are required. */
    private List<String> required;

    /** Reusable type definitions referenced via $ref. */
    private Map<String, JsonSchema> definitions;

    // ── Helper methods ─────────────────────────────────

    public boolean isObject() { return "object".equals(type); }
    public boolean isArray()  { return "array".equals(type); }

    /**
     * Resolve a {@code $ref} reference like "#/definitions/Character"
     * to the actual schema definition.
     */
    public JsonSchema resolveRef(String ref) {
        if (ref == null || definitions == null) return null;
        String key = ref.replace("#/definitions/", "");
        return definitions.get(key);
    }

    // ── Inner types ─────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PropertyDef {
        private String type;
        private JsonSchema items;        // for array properties
        private List<String> enumValues; // for enum constraints
        private List<String> required;   // for nested object properties
    }
}
