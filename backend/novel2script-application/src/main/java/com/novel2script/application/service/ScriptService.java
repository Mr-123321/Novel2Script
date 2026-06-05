package com.novel2script.application.service;

import com.novel2script.common.enums.ScriptStatus;
import com.novel2script.common.exception.BusinessException;
import com.novel2script.domain.model.Script;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Application service for script generation and management.
 */
@Slf4j
@Service
public class ScriptService {

    /**
     * Start the AI-powered script generation pipeline for a given novel.
     */
    public Script generateScript(Long novelId, int maxScenes, String style,
                                  List<String> focusCharacters) {
        log.info("Starting script generation: novelId={}, maxScenes={}, style='{}'",
                novelId, maxScenes, style);

        if (maxScenes < 1 || maxScenes > 50) {
            throw new BusinessException("INVALID_SCENE_COUNT",
                    "maxScenes must be between 1 and 50, got: " + maxScenes);
        }

        Script script = Script.builder()
                .novelId(novelId)
                .status(ScriptStatus.DRAFT)
                .progress(0.0)
                .build();

        script.startGeneration();
        log.info("Script generation started: scriptId={}", script.getId());
        return script;
    }

    /**
     * Find a script by its ID.
     */
    public Optional<Script> findById(Long scriptId) {
        // TODO: delegate to repository
        log.debug("Finding script by id={}", scriptId);
        return Optional.empty();
    }

    /**
     * Get generation progress for a script.
     */
    public double getProgress(Long scriptId) {
        // TODO: delegate to repository / workflow state
        return 0.0;
    }

    /**
     * Get the YAML export of a completed script.
     */
    public String getYaml(Long scriptId) {
        // TODO: delegate to repository
        log.debug("Getting YAML for script id={}", scriptId);
        return null;
    }

    /**
     * List all scripts.
     */
    public List<Script> listAll() {
        // TODO: delegate to repository
        return List.of();
    }
}
