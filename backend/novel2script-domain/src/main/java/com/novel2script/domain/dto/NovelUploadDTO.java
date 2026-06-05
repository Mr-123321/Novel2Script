package com.novel2script.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for uploading a novel file.
 */
public record NovelUploadDTO(
        @JsonProperty("title")
        String title,

        @JsonProperty("author")
        String author,

        @JsonProperty("fileName")
        String fileName,

        @JsonProperty("fileSize")
        long fileSize,

        @JsonProperty("content")
        String content
) {
    public NovelUploadDTO {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title must not be blank");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name must not be blank");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content must not be blank");
        }
    }
}
