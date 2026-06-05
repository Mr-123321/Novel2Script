package com.novel2script.application.service.workflow.model;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * Top-level definition of a workflow — a named, ordered collection of steps,
 * optionally with dependencies, that together accomplish a goal (e.g. full
 * script generation).
 */
@Data
@Builder
public class Workflow {

    /** Human-readable name, e.g. "fullGeneration" */
    private String name;

    /** Steps that compose this workflow */
    @Builder.Default
    private List<Step> steps = Collections.emptyList();

    /** Optional description of what the workflow does */
    private String description;
}
