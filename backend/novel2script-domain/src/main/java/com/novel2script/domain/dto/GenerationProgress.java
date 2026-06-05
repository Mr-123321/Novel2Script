package com.novel2script.domain.dto;

import com.novel2script.common.enums.WorkflowStep;
import java.time.Instant;

public record GenerationProgress(
        String executionId,
        WorkflowStep currentStep,
        double overallProgress,
        String status,
        Instant startedAt,
        Instant estimatedCompletion,
        String message
) {}
