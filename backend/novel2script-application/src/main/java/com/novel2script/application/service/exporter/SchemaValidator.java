package com.novel2script.application.service.exporter;

import com.novel2script.application.service.exporter.model.ScriptYamlModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a {@link ScriptYamlModel} before export.
 *
 * <p>Checks that mandatory fields are present, collections are non-null
 * and non-empty, and structural invariants hold. Validation errors are
 * returned as a list of human-readable messages — an empty list means
 * the model passes validation.
 */
@Component
public class SchemaValidator {

    /**
     * Validate the given model and return a list of problems.
     *
     * @param model the model to validate (may be {@code null})
     * @return list of validation error messages; empty list means valid
     */
    public List<String> validate(ScriptYamlModel model) {
        List<String> errors = new ArrayList<>();

        if (model == null) {
            errors.add("ScriptYamlModel is null");
            return errors;
        }

        // Meta
        if (model.getMeta() != null) {
            if (model.getMeta().getTitle() == null || model.getMeta().getTitle().isBlank()) {
                errors.add("meta.title is null or blank");
            }
        }

        // Characters
        if (model.getCharacters() == null) {
            errors.add("characters list is null");
        } else if (model.getCharacters().isEmpty()) {
            errors.add("characters list is empty — at least one character is required");
        } else {
            for (int i = 0; i < model.getCharacters().size(); i++) {
                var character = model.getCharacters().get(i);
                if (character == null) {
                    errors.add("characters[" + i + "] is null");
                    continue;
                }
                if (character.getName() == null || character.getName().isBlank()) {
                    errors.add("characters[" + i + "].name is null or blank");
                }
            }
        }

        // Scenes
        if (model.getScenes() == null) {
            errors.add("scenes list is null");
        } else if (model.getScenes().isEmpty()) {
            errors.add("scenes list is empty — at least one scene is required");
        } else {
            for (int i = 0; i < model.getScenes().size(); i++) {
                var scene = model.getScenes().get(i);
                if (scene == null) {
                    errors.add("scenes[" + i + "] is null");
                    continue;
                }
                if (scene.getLocation() == null || scene.getLocation().isBlank()) {
                    errors.add("scenes[" + i + "].location is null or blank (scene " + scene.getSceneNumber() + ")");
                }
                if (scene.getSequence() == null || scene.getSequence().isEmpty()) {
                    errors.add("scenes[" + i + "].sequence is null or empty (scene " + scene.getSceneNumber() + ")");
                }
            }
        }

        return errors;
    }
}
