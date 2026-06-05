package com.novel2script.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A significant plot event extracted from the novel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlotEvent {

    private Long id;
    private Long scriptId;
    private int eventOrder;
    private String title;
    private String description;
    private String location;
    private String timePoint;
    private String conflictType;   // PERSON_VS_PERSON / PERSON_VS_SELF / PERSON_VS_SOCIETY / ...
    private List<Long> chapterIds;
    private List<Long> characterIds;
    private int importance;        // 1-5

    private LocalDateTime createdAt;

    public boolean isCritical() {
        return importance >= 4;
    }
}
