package com.novel2script.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ScriptGenerateRequest(
        @NotNull Long novelId,
        @Min(1) @Max(100) Integer maxScenes,
        String style,
        String focusCharacters
) {
    public ScriptGenerateRequest {
        if (maxScenes == null) {
            maxScenes = 20;
        }
    }
}
