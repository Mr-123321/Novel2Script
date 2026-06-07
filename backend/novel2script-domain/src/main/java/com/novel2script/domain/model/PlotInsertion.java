package com.novel2script.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("plot_insertions")
public class PlotInsertion {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("script_id")
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
    @TableField("inserted_by")
    private String insertedBy = "user";

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
