package com.novel2script.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A user-inserted plot description block that appears between scenes
 * in the generated script. Users can manually add narrative context,
 * director's notes, or transitional descriptions.
 *
 * <p>Unlike {@link PlotEvent} (which is AI-extracted from the novel),
 * PlotInsertion is explicitly created by the user after generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlotInsertion {

    private Long id;
    private Long scriptId;

    /** The markdown-formatted plot text to display */
    private String text;

    /**
     * Insertion position: 0 = before first scene,
     * 1 = after scene 1, 2 = after scene 2, etc.
     */
    private int position;

    /** Who created this insertion */
    @Builder.Default
    private String insertedBy = "user";

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
