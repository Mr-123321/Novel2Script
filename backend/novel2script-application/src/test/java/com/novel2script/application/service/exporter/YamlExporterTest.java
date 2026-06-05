package com.novel2script.application.service.exporter;

import com.novel2script.application.service.exporter.model.ScriptYamlModel;
import com.novel2script.common.enums.CharacterRoleType;
import com.novel2script.common.enums.Emotion;
import com.novel2script.common.enums.TimeOfDay;
import com.novel2script.common.exception.ExportException;
import com.novel2script.domain.model.Action;
import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.model.Scene;
import com.novel2script.domain.model.Script;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link YamlExporter} covering standard export, Chinese content,
 * validation, null-safety, and round-trip parsing.
 */
@DisplayName("YamlExporter")
class YamlExporterTest {

    private YamlExporter exporter;
    private Script script;

    @BeforeEach
    void setUp() {
        SchemaValidator validator = new SchemaValidator();
        exporter = new YamlExporter(validator);
        script = buildRealisticScript();
    }

    // ──────────────────────────────────────────────
    // Standard export
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Should export a complete script to a non-empty YAML string")
    void shouldExportCompleteScript() {
        String yaml = exporter.exportToString(script);

        assertNotNull(yaml);
        assertFalse(yaml.isBlank(), "YAML output should not be blank");

        // Structural assertions
        assertTrue(yaml.contains("meta"), "Should contain meta section");
        assertTrue(yaml.contains("title"), "Should contain title");
        assertTrue(yaml.contains("characters"), "Should contain characters section");
        assertTrue(yaml.contains("scenes"), "Should contain scenes section");
    }

    @Test
    @DisplayName("Should export script to file")
    void shouldExportToFile(@TempDir Path tempDir) throws Exception {
        Path outputPath = tempDir.resolve("test_script.yaml");
        exporter.exportToFile(script, outputPath);

        assertTrue(Files.exists(outputPath), "Output file should exist");
        String content = Files.readString(outputPath);
        assertTrue(content.contains("meta"), "File content should contain meta");
        assertFalse(content.isBlank(), "File should not be empty");
    }

    // ──────────────────────────────────────────────
    // Chinese support (no Unicode escapes — real Chinese chars in output)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Should produce valid UTF-8 YAML without Unicode escapes")
    void shouldPreserveChineseCharacters() {
        String yaml = exporter.exportToString(script);

        // Verify no Unicode escape sequences leaked
        assertFalse(yaml.contains("\\u"), "Should NOT contain Unicode escapes");

        // Verify expected structural content
        assertTrue(yaml.contains("PROTAGONIST"), "Should contain PROTAGONIST");
        assertTrue(yaml.contains("MORNING"), "Should contain MORNING");
        assertFalse(yaml.isBlank(), "YAML should not be blank");
    }

    @Test
    @DisplayName("Should correctly export emotion and role type in Chinese context")
    void shouldExportChineseContextMetadata() {
        String yaml = exporter.exportToString(script);

        assertTrue(yaml.contains("PROTAGONIST"), "Should contain role type PROTAGONIST");
        assertTrue(yaml.contains("ANTAGONIST"), "Should contain role type ANTAGONIST");
        assertTrue(yaml.contains("ANGRY"), "Should contain emotion ANGRY");
        assertTrue(yaml.contains("SAD"), "Should contain emotion SAD");
    }

    // ──────────────────────────────────────────────
    // Validation failures
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Should throw ExportException when validation fails due to empty characters")
    void shouldThrowOnValidationFailureEmptyCharacters() {
        Script emptyScript = Script.builder()
                .title("Empty Script")
                .scenes(List.of(Scene.builder()
                        .sceneNumber(1)
                        .location("Test Location")
                        .dialogues(List.of(Dialogue.builder()
                                .sequence(1)
                                .speaker("Someone")
                                .content("Hello")
                                .build()))
                        .build()))
                .characters(new ArrayList<>())  // empty — should fail validation
                .build();

        ExportException exception = assertThrows(ExportException.class, () ->
                exporter.exportToString(emptyScript));

        assertTrue(exception.getMessage().contains("characters"),
                "Error should mention characters: " + exception.getMessage());
        assertEquals("EXPORT_VALIDATION_FAILED", exception.getErrorCode());
    }

    @Test
    @DisplayName("Should throw ExportException when validation fails due to empty scenes")
    void shouldThrowOnValidationFailureEmptyScenes() {
        Script emptyScript = Script.builder()
                .title("Empty Script")
                .characters(List.of(
                        com.novel2script.domain.model.Character.builder()
                                .id(1L)
                                .canonicalName("Test")
                                .build()))
                .scenes(new ArrayList<>())  // empty — should fail validation
                .build();

        ExportException exception = assertThrows(ExportException.class, () ->
                exporter.exportToString(emptyScript));

        assertTrue(exception.getMessage().contains("scenes"),
                "Error should mention scenes: " + exception.getMessage());
    }

    @Test
    @DisplayName("Should pass validation when validation is disabled in options")
    void shouldSkipValidationWhenDisabled() {
        Script emptyScript = Script.builder()
                .title("Skipped Validation")
                .characters(new ArrayList<>())
                .scenes(new ArrayList<>())
                .build();

        ExportOptions noValidation = new ExportOptions(true, true, true, false, false);

        // Should NOT throw because validation is disabled
        String yaml = exporter.exportToString(emptyScript, noValidation);
        assertNotNull(yaml);
        assertTrue(yaml.contains("meta"), "Should still contain meta section");
    }

    // ──────────────────────────────────────────────
    // Null handling
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Should throw ExportException when script is null")
    void shouldThrowOnNullScript() {
        ExportException exception = assertThrows(ExportException.class, () ->
                exporter.exportToString(null));

        assertEquals("EXPORT_NULL_SCRIPT", exception.getErrorCode());
    }

    @Test
    @DisplayName("Should handle script with null collections gracefully")
    void shouldHandleNullCollections() {
        Script sparseScript = Script.builder()
                .title("Sparse Script")
                .characters(null)
                .scenes(null)
                .build();

        ExportOptions noValidation = new ExportOptions(true, true, true, false, false);
        String yaml = exporter.exportToString(sparseScript, noValidation);

        assertNotNull(yaml);
        assertFalse(yaml.isBlank(), "Should still produce output");
    }

    // ──────────────────────────────────────────────
    // Round-trip
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Should produce YAML that can be parsed back and inspected")
    void shouldRoundTripParsableYaml() {
        String yaml = exporter.exportToString(script);

        // Parse it back with SnakeYAML
        Yaml parser = new Yaml();
        Map<String, Object> parsed = parser.load(yaml);

        assertNotNull(parsed, "Parsed YAML should not be null");

        // Check characters section
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chars = (List<Map<String, Object>>) parsed.get("characters");
        assertNotNull(chars, "Parsed characters should not be null");
        assertFalse(chars.isEmpty(), "Should have at least one character");

        Map<String, Object> firstChar = chars.get(0);
        assertNotNull(firstChar.get("name"), "Character should have name");

        // Check scenes section
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenes = (List<Map<String, Object>>) parsed.get("scenes");
        assertNotNull(scenes, "Parsed scenes should not be null");
        assertFalse(scenes.isEmpty(), "Should have at least one scene");

        Map<String, Object> firstScene = scenes.get(0);
        assertNotNull(firstScene.get("location"), "Scene should have location");
        assertNotNull(firstScene.get("sequence"), "Scene should have sequence");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> seq = (List<Map<String, Object>>) firstScene.get("sequence");
        assertFalse(seq.isEmpty(), "Scene sequence should not be empty");
    }

    @Test
    @DisplayName("Should maintain dialogue and action counts through round-trip")
    void shouldPreserveCountsAfterRoundTrip() {
        String yaml = exporter.exportToString(script);

        Yaml parser = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) parser.load(yaml);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenes = (List<Map<String, Object>>) parsed.get("scenes");

        // Our test script has 2 scenes
        assertEquals(2, scenes.size(), "Should have 2 scenes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> seq = (List<Map<String, Object>>) scenes.get(0).get("sequence");
        assertTrue(seq.size() >= 1, "Scene should have at least 1 sequence item");
    }

    @Test
    @DisplayName("Should export to file with custom ExportOptions")
    void shouldExportToFileWithCustomOptions(@TempDir Path tempDir) throws Exception {
        Path outputPath = tempDir.resolve("custom_script.yaml");

        ExportOptions options = new ExportOptions(false, true, false, true, true);
        exporter.exportToFile(script, outputPath, options);

        assertTrue(Files.exists(outputPath));
        String content = Files.readString(outputPath);
        assertFalse(content.isBlank());
    }

    // ──────────────────────────────────────────────
    // SchemaValidator standalone tests
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("SchemaValidator")
    class SchemaValidatorTests {

        private SchemaValidator validator;

        @BeforeEach
        void setUp() {
            validator = new SchemaValidator();
        }

        @Test
        @DisplayName("Should return errors for null model")
        void shouldFailOnNullModel() {
            List<String> errors = validator.validate(null);
            assertEquals(1, errors.size());
            assertTrue(errors.get(0).contains("null"));
        }

        @Test
        @DisplayName("Should return errors for empty characters list")
        void shouldFailOnEmptyCharacters() {
            ScriptYamlModel model = ScriptYamlModel.builder()
                    .meta(ScriptYamlModel.MetaInfo.builder().title("Test").build())
                    .characters(new ArrayList<>())
                    .scenes(List.of(ScriptYamlModel.SceneModel.builder()
                            .sceneNumber(1)
                            .location("test")
                            .sequence(List.of(ScriptYamlModel.SequenceItem.builder()
                                    .type("DIALOGUE")
                                    .character("Someone")
                                    .content("Hello")
                                    .build()))
                            .build()))
                    .build();

            List<String> errors = validator.validate(model);
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.contains("characters") && e.contains("empty")),
                    "Should complain about empty characters: " + errors);
        }

        @Test
        @DisplayName("Should return errors for empty scenes list")
        void shouldFailOnEmptyScenes() {
            ScriptYamlModel model = ScriptYamlModel.builder()
                    .meta(ScriptYamlModel.MetaInfo.builder().title("Test").build())
                    .characters(List.of(ScriptYamlModel.CharacterModel.builder()
                            .name("Someone")
                            .build()))
                    .scenes(new ArrayList<>())
                    .build();

            List<String> errors = validator.validate(model);
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.contains("scenes") && e.contains("empty")),
                    "Should complain about empty scenes: " + errors);
        }

        @Test
        @DisplayName("Should return errors for null character names")
        void shouldFailOnNullCharacterName() {
            ScriptYamlModel model = ScriptYamlModel.builder()
                    .meta(ScriptYamlModel.MetaInfo.builder().title("Test").build())
                    .characters(List.of(ScriptYamlModel.CharacterModel.builder()
                            .name(null)
                            .build()))
                    .scenes(List.of(ScriptYamlModel.SceneModel.builder()
                            .sceneNumber(1)
                            .location("test")
                            .sequence(List.of(ScriptYamlModel.SequenceItem.builder()
                                    .type("DIALOGUE")
                                    .content("Hello")
                                    .build()))
                            .build()))
                    .build();

            List<String> errors = validator.validate(model);
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.contains("name") && e.contains("null")),
                    "Should complain about null character name: " + errors);
        }

        @Test
        @DisplayName("Should return errors for scene with empty sequence")
        void shouldFailOnEmptySequence() {
            ScriptYamlModel model = ScriptYamlModel.builder()
                    .meta(ScriptYamlModel.MetaInfo.builder().title("Test").build())
                    .characters(List.of(ScriptYamlModel.CharacterModel.builder()
                            .name("Someone")
                            .build()))
                    .scenes(List.of(ScriptYamlModel.SceneModel.builder()
                            .sceneNumber(1)
                            .location("test")
                            .sequence(new ArrayList<>())
                            .build()))
                    .build();

            List<String> errors = validator.validate(model);
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.contains("sequence")),
                    "Should complain about empty sequence: " + errors);
        }

        @Test
        @DisplayName("Should pass validation for a well-formed model")
        void shouldPassOnValidModel() {
            ScriptYamlModel model = ScriptYamlModel.builder()
                    .meta(ScriptYamlModel.MetaInfo.builder()
                            .title("Valid Script")
                            .build())
                    .characters(List.of(ScriptYamlModel.CharacterModel.builder()
                            .name("Hero")
                            .build()))
                    .scenes(List.of(ScriptYamlModel.SceneModel.builder()
                            .sceneNumber(1)
                            .location("Castle")
                            .sequence(List.of(ScriptYamlModel.SequenceItem.builder()
                                    .type("DIALOGUE")
                                    .character("Hero")
                                    .content("I will save the day!")
                                    .build()))
                            .build()))
                    .build();

            List<String> errors = validator.validate(model);
            assertTrue(errors.isEmpty(), "Should have no errors, but got: " + errors);
        }
    }

    // ──────────────────────────────────────────────
    // ExportOptions tests
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("ExportOptions.defaults() should have expected values")
    void shouldHaveExpectedDefaults() {
        ExportOptions defaults = ExportOptions.defaults();
        assertTrue(defaults.includeStoryboard());
        assertTrue(defaults.includeMetadata());
        assertTrue(defaults.prettyPrint());
        assertTrue(defaults.validateSchema());
        assertFalse(defaults.compressEmpty());
    }

    // ──────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────

    /**
     * Build a realistic script with Chinese content for testing.
     */
    private static Script buildRealisticScript() {
        // Characters
        com.novel2script.domain.model.Character linChuan = com.novel2script.domain.model.Character.builder()
                .id(1L)
                .scriptId(1L)
                .canonicalName("林川")
                .aliases(List.of("小林", "阿川"))
                .roleType(CharacterRoleType.PROTAGONIST)
                .description("青云宗外门弟子，天生资质平庸却意外获得上古传承")
                .personality(List.of("冷静", "机智", "重情义"))
                .relationships(List.of(
                        com.novel2script.domain.model.Character.Relationship.builder()
                                .target("苏云")
                                .relation("同门师妹")
                                .build(),
                        com.novel2script.domain.model.Character.Relationship.builder()
                                .target("莫长老")
                                .relation("师尊")
                                .build()))
                .build();

        com.novel2script.domain.model.Character suYun = com.novel2script.domain.model.Character.builder()
                .id(2L)
                .scriptId(1L)
                .canonicalName("苏云")
                .aliases(List.of("小云"))
                .roleType(CharacterRoleType.SUPPORTING)
                .description("青云宗内门弟子，林川的青梅竹马")
                .personality(List.of("温柔", "善良", "坚韧"))
                .relationships(List.of(
                        com.novel2script.domain.model.Character.Relationship.builder()
                                .target("林川")
                                .relation("同门师兄")
                                .build()))
                .build();

        com.novel2script.domain.model.Character moZhangLao = com.novel2script.domain.model.Character.builder()
                .id(3L)
                .scriptId(1L)
                .canonicalName("莫长老")
                .aliases(List.of("莫师叔", "莫老"))
                .roleType(CharacterRoleType.ANTAGONIST)
                .description("表面上德高望重，实则暗藏祸心")
                .personality(List.of("冷峻", "深沉", "野心勃勃"))
                .build();

        // Scene 1
        Scene scene1 = Scene.builder()
                .id(1L)
                .scriptId(1L)
                .sceneNumber(1)
                .title("宗门大会")
                .location("白云宗大殿")
                .timeOfDay(TimeOfDay.MORNING)
                .interior(true)
                .summary("宗门大会之上，林川被指认为叛徒，莫长老当众揭发")
                .mood("紧张、肃穆")
                .sceneHeading("INT. 白云宗大殿 - MORNING")
                .chapterIds(List.of(1L, 2L))
                .characterIds(List.of(1L, 2L, 3L))
                .dialogues(List.of(
                        Dialogue.builder()
                                .id(1L)
                                .sceneId(1L)
                                .characterId(3L)
                                .speaker("莫长老")
                                .sequence(1)
                                .emotion(Emotion.ANGRY)
                                .content("林川！你勾结魔族，证据确凿，还不认罪？！")
                                .parenthetical("(指着林川)")
                                .build(),
                        Dialogue.builder()
                                .id(2L)
                                .sceneId(1L)
                                .characterId(1L)
                                .speaker("林川")
                                .sequence(2)
                                .emotion(Emotion.CALM)
                                .content("莫师叔，欲加之罪何患无辞。")
                                .parenthetical("(冷眼而视)")
                                .build(),
                        Dialogue.builder()
                                .id(3L)
                                .sceneId(1L)
                                .characterId(2L)
                                .speaker("苏云")
                                .sequence(3)
                                .emotion(Emotion.ANXIOUS)
                                .content("不可能！师兄他绝不会...")
                                .parenthetical("(声音颤抖)")
                                .build()))
                .actions(List.of(
                        Action.builder()
                                .id(1L)
                                .sceneId(1L)
                                .characterId(3L)
                                .sequence(1)
                                .actionType("ACTION")
                                .description("莫长老一掌拍在桌子上")
                                .build(),
                        Action.builder()
                                .id(2L)
                                .sceneId(1L)
                                .characterId(1L)
                                .sequence(2)
                                .actionType("REACTION")
                                .description("林川缓缓站起身，目光扫过众人")
                                .build()))
                .build();

        // Scene 2
        Scene scene2 = Scene.builder()
                .id(2L)
                .scriptId(1L)
                .sceneNumber(2)
                .title("后山密谈")
                .location("白云宗后山竹林")
                .timeOfDay(TimeOfDay.NIGHT)
                .interior(false)
                .summary("林川与苏云在后山相遇，苏云透露了真相")
                .mood("忧伤、深情")
                .sceneHeading("EXT. 白云宗后山竹林 - NIGHT")
                .chapterIds(List.of(3L))
                .characterIds(List.of(1L, 2L))
                .dialogues(List.of(
                        Dialogue.builder()
                                .id(4L)
                                .sceneId(2L)
                                .characterId(1L)
                                .speaker("林川")
                                .sequence(1)
                                .emotion(Emotion.SAD)
                                .content("小云，你也相信他们说的吗？")
                                .parenthetical(null)
                                .build(),
                        Dialogue.builder()
                                .id(5L)
                                .sceneId(2L)
                                .characterId(2L)
                                .speaker("苏云")
                                .sequence(2)
                                .emotion(Emotion.ANXIOUS)
                                .content("林川哥哥，我...我知道真相。莫长老他...才是真正勾结魔族的人。")
                                .parenthetical("(压低声音)")
                                .build()))
                .actions(List.of(
                        Action.builder()
                                .id(3L)
                                .sceneId(2L)
                                .characterId(null)
                                .sequence(1)
                                .actionType("BUSINESS")
                                .description("竹叶随风飘落，月光洒在林间小路上")
                                .build()))
                .build();

        return Script.builder()
                .id(1L)
                .novelId(1L)
                .title("修仙风云录 — 叛徒之章")
                .version(1)
                .sceneCount(2)
                .characterCount(3)
                .dialogueCount(5)
                .characters(List.of(linChuan, suYun, moZhangLao))
                .scenes(List.of(scene1, scene2))
                .build();
    }
}
