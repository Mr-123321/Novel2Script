package com.novel2script.schema;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Novel2Script 剧本 YAML Schema 的 Java Record 等价模型。
 * 用于 YAML ↔ Java 对象的双向序列化，保证无信息丢失。
 *
 * @see com.novel2script.domain.model.Script
 */

// ---- 元信息 ----
public record ScriptMeta(
    String title,
    String originalNovel,
    int version,
    LocalDateTime generatedAt,
    String generator,
    Integer sceneCount,
    Integer characterCount,
    Integer dialogueCount
) {}

// ---- 角色定义 ----
public record CharacterDef(
    int id,
    String name,
    List<String> aliases,
    String roleType,       // PROTAGONIST / ANTAGONIST / SUPPORTING / MINOR
    String gender,         // MALE / FEMALE / UNKNOWN
    String ageRange,       // e.g. "18-22"
    String description,
    List<String> personality,
    List<RelationshipDef> relationships
) {}

public record RelationshipDef(
    String target,
    String relation
) {}

// ---- 场景定义 ----
public record SceneDef(
    int sceneNumber,
    String heading,
    String location,
    String timeOfDay,      // DAWN / MORNING / AFTERNOON / EVENING / DUSK / NIGHT / LATE_NIGHT
    boolean isInterior,
    String summary,
    String mood,
    List<Integer> chapterSources,
    List<Integer> presentCharacters,
    List<SequenceItem> sequence
) {}

// ---- 动作-对白序列 (sealed interface) ----
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ActionItem.class, name = "ACTION"),
    @JsonSubTypes.Type(value = DialogueItem.class, name = "DIALOGUE")
})
public sealed interface SequenceItem permits ActionItem, DialogueItem {}

public record ActionItem(
    String type,           // always "ACTION"
    String character,      // null = 环境动作
    String actionType,     // ACTION / REACTION / BEAT / BUSINESS
    String description,
    Integer durationMs
) implements SequenceItem {}

public record DialogueItem(
    String type,           // always "DIALOGUE"
    String speaker,
    String emotion,        // ANGRY / HAPPY / SAD / CALM / FEARFUL / SURPRISED / NEUTRAL
    String content,
    String parenthetical,
    Integer replyTo
) implements SequenceItem {}

// ---- 分镜扩展 ----
public record StoryboardDef(
    int sceneNumber,
    List<ShotDef> shots
) {}

public record ShotDef(
    int shotNumber,
    String camera,         // EXTREME_WIDE / WIDE / MEDIUM / CLOSE_UP / ...
    String angle,          // EYE_LEVEL / HIGH_ANGLE / LOW_ANGLE / ...
    double durationSec,
    String description,
    String movement,       // STATIC / PAN / TILT / DOLLY / ZOOM / ...
    String transition      // CUT / DISSOLVE / FADE / WIPE / ...
) {}

// ---- AI 配音扩展 ----
public record VoiceoverDef(
    int characterId,
    String voiceId,
    Double speed,
    Integer pitch,
    Integer pauseBetween
) {}

// ---- 视频生成扩展 ----
public record VideoDef(
    String defaultTransition,
    List<SceneTransitionDef> sceneTransitions
) {}

public record SceneTransitionDef(
    int fromScene,
    int toScene,
    String transition,
    Double durationSec
) {}

// ---- 顶层剧本模型 ----
public record ScriptYamlModel(
    ScriptMeta meta,
    List<CharacterDef> characters,
    List<SceneDef> scenes,
    List<StoryboardDef> storyboard,
    List<VoiceoverDef> voiceover,
    VideoDef video
) {}
