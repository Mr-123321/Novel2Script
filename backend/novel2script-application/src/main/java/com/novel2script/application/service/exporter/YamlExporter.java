package com.novel2script.application.service.exporter;

import com.novel2script.application.service.exporter.model.ScriptYamlModel;
import com.novel2script.common.exception.ExportException;
import com.novel2script.domain.model.Script;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Exports a {@link Script} domain object to YAML format using SnakeYAML.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * String yaml = yamlExporter.exportToString(script);
 * yamlExporter.exportToFile(script, Path.of("output/script.yaml"));
 * String yamlCustom = yamlExporter.exportToString(script, ExportOptions.defaults());
 * }</pre>
 *
 * <p>This exporter is <b>not</b> Jackson-based — it uses SnakeYAML directly
 * for full control over YAML styling (block layout, Unicode, pretty flow).
 */
@Service
public class YamlExporter {

    private final SchemaValidator schemaValidator;

    public YamlExporter(SchemaValidator schemaValidator) {
        this.schemaValidator = schemaValidator;
    }

    // ──────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────

    /**
     * Export the script to a YAML string using default options.
     *
     * @param script the domain script (must not be {@code null})
     * @return YAML string
     * @throws ExportException if validation or serialization fails
     */
    public String exportToString(Script script) {
        return exportToString(script, ExportOptions.defaults());
    }

    /**
     * Export the script to a YAML string with custom options.
     *
     * @param script  the domain script (must not be {@code null})
     * @param options export configuration
     * @return YAML string
     * @throws ExportException if validation or serialization fails
     */
    public String exportToString(Script script, ExportOptions options) {
        if (script == null) {
            throw new ExportException("EXPORT_NULL_SCRIPT", "Script must not be null");
        }
        if (options == null) {
            options = ExportOptions.defaults();
        }

        ScriptYamlModel model = ScriptYamlModel.convert(script);

        // Optional schema validation
        if (options.validateSchema()) {
            List<String> errors = schemaValidator.validate(model);
            if (!errors.isEmpty()) {
                throw new ExportException(
                        "EXPORT_VALIDATION_FAILED",
                        "Schema validation failed: " + String.join("; ", errors));
            }
        }

        // Build a single YAML document with all sections
        Yaml yaml = createYaml(options);

        Map<String, Object> root = new java.util.LinkedHashMap<>();
        if (options.includeMetadata() && model.getMeta() != null) {
            root.put("meta", createMetaMap(model.getMeta()));
        }
        root.put("characters", model.getCharacters() != null
                ? model.getCharacters() : List.of());
        root.put("scenes", model.getScenes() != null
                ? model.getScenes() : List.of());

        StringWriter writer = new StringWriter();
        yaml.dump(root, writer);

        return postProcess(writer.toString(), options);
    }

    /**
     * Export the script to a file on disk.
     *
     * @param script the domain script (must not be {@code null})
     * @param path   destination file path (parent directories will be created)
     * @throws ExportException if validation, serialization, or I/O fails
     */
    public void exportToFile(Script script, Path path) {
        exportToFile(script, path, ExportOptions.defaults());
    }

    /**
     * Export the script to a file on disk with custom options.
     *
     * @param script  the domain script (must not be {@code null})
     * @param path    destination file path
     * @param options export configuration
     * @throws ExportException if validation, serialization, or I/O fails
     */
    public void exportToFile(Script script, Path path, ExportOptions options) {
        String yaml = exportToString(script, options);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, yaml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ExportException("EXPORT_IO_ERROR", "Failed to write YAML to " + path, e);
        }
    }

    // ──────────────────────────────────────────────
    // Internal
    // ──────────────────────────────────────────────

    /**
     * Build a SnakeYAML instance with the configured options.
     * Uses a custom Representer that suppresses Java class tags.
     */
    private Yaml createYaml(ExportOptions options) {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setPrettyFlow(options.prettyPrint());
        dumperOptions.setDefaultFlowStyle(FlowStyle.BLOCK);
        dumperOptions.setAllowReadOnlyProperties(false);
        dumperOptions.setAllowUnicode(true);

        // Use standard Representer and register model classes as plain MAP tags
        Representer representer = new Representer(dumperOptions);
        representer.setDefaultFlowStyle(FlowStyle.BLOCK);
        representer.getPropertyUtils().setSkipMissingProperties(true);

        // Register all application model classes as plain MAPs to avoid !! tags
        representer.addClassTag(ScriptYamlModel.class, Tag.MAP);
        representer.addClassTag(ScriptYamlModel.MetaInfo.class, Tag.MAP);
        representer.addClassTag(ScriptYamlModel.CharacterModel.class, Tag.MAP);
        representer.addClassTag(ScriptYamlModel.RelationModel.class, Tag.MAP);
        representer.addClassTag(ScriptYamlModel.SceneModel.class, Tag.MAP);
        representer.addClassTag(ScriptYamlModel.SequenceItem.class, Tag.MAP);

        return new Yaml(representer, dumperOptions);
    }

    /**
     * Build a map for the metadata section.
     */
    private Map<String, Object> createMetaMap(ScriptYamlModel.MetaInfo meta) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("title", nullToEmpty(meta.getTitle()));
        map.put("originalNovel", nullToEmpty(meta.getOriginalNovel()));
        map.put("version", meta.getVersion());
        map.put("generatedAt", nullToEmpty(meta.getGeneratedAt()));
        map.put("generator", nullToEmpty(meta.getGenerator()));
        map.put("sceneCount", meta.getSceneCount());
        map.put("characterCount", meta.getCharacterCount());
        map.put("dialogueCount", meta.getDialogueCount());
        return map;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Clean up YAML output — remove unwanted class tags and trailing whitespace.
     */
    private String postProcess(String raw, ExportOptions options) {
        String result = raw;

        // Remove SnakeYAML class tags that leak through (e.g. !!com.novel2script...)
        result = Pattern.compile("^!!.*$", Pattern.MULTILINE).matcher(result).replaceAll("");

        // Collapse multiple blank lines into one
        result = Pattern.compile("\n{3,}").matcher(result).replaceAll("\n\n");

        // Remove trailing whitespace on each line
        result = Pattern.compile("[ \\t]+$", Pattern.MULTILINE).matcher(result).replaceAll("");

        // Ensure file ends with exactly one newline
        result = result.stripTrailing() + "\n";

        return result;
    }
}
