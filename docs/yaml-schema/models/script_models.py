"""
Novel2Script 剧本 YAML Schema 的 Pydantic 等价模型。
用于文档验证、Schema 校验和跨语言互操作。
"""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import List, Literal, Optional, Union

from pydantic import BaseModel, Field


# ---- 枚举类型 ----

class CharacterRoleType(str, Enum):
    PROTAGONIST = "PROTAGONIST"
    ANTAGONIST = "ANTAGONIST"
    SUPPORTING = "SUPPORTING"
    MINOR = "MINOR"


class Gender(str, Enum):
    MALE = "MALE"
    FEMALE = "FEMALE"
    UNKNOWN = "UNKNOWN"


class TimeOfDay(str, Enum):
    DAWN = "DAWN"
    MORNING = "MORNING"
    AFTERNOON = "AFTERNOON"
    EVENING = "EVENING"
    DUSK = "DUSK"
    NIGHT = "NIGHT"
    LATE_NIGHT = "LATE_NIGHT"
    UNKNOWN = "UNKNOWN"


class ActionType(str, Enum):
    ACTION = "ACTION"
    REACTION = "REACTION"
    BEAT = "BEAT"
    BUSINESS = "BUSINESS"


class Emotion(str, Enum):
    ANGRY = "ANGRY"
    HAPPY = "HAPPY"
    SAD = "SAD"
    CALM = "CALM"
    FEARFUL = "FEARFUL"
    SURPRISED = "SURPRISED"
    NEUTRAL = "NEUTRAL"


class CameraPosition(str, Enum):
    EXTREME_WIDE = "EXTREME_WIDE"
    WIDE = "WIDE"
    MEDIUM_WIDE = "MEDIUM_WIDE"
    MEDIUM = "MEDIUM"
    MEDIUM_CLOSE_UP = "MEDIUM_CLOSE_UP"
    CLOSE_UP = "CLOSE_UP"
    EXTREME_CLOSE_UP = "EXTREME_CLOSE_UP"
    POV = "POV"
    OVER_SHOULDER = "OVER_SHOULDER"


class CameraAngle(str, Enum):
    EYE_LEVEL = "EYE_LEVEL"
    HIGH_ANGLE = "HIGH_ANGLE"
    LOW_ANGLE = "LOW_ANGLE"
    DUTCH_ANGLE = "DUTCH_ANGLE"
    BIRDS_EYE = "BIRDS_EYE"
    WORMS_EYE = "WORMS_EYE"
    OVERHEAD = "OVERHEAD"


class CameraMovement(str, Enum):
    STATIC = "STATIC"
    PAN_LEFT = "PAN_LEFT"
    PAN_RIGHT = "PAN_RIGHT"
    TILT_UP = "TILT_UP"
    TILT_DOWN = "TILT_DOWN"
    DOLLY_IN = "DOLLY_IN"
    DOLLY_OUT = "DOLLY_OUT"
    ZOOM_IN = "ZOOM_IN"
    ZOOM_OUT = "ZOOM_OUT"
    HANDHELD = "HANDHELD"
    CRANE_UP = "CRANE_UP"
    CRANE_DOWN = "CRANE_DOWN"
    TRACKING = "TRACKING"
    STEADICAM = "STEADICAM"


class TransitionType(str, Enum):
    CUT = "CUT"
    DISSOLVE = "DISSOLVE"
    FADE_IN = "FADE_IN"
    FADE_OUT = "FADE_OUT"
    FADE_TO_BLACK = "FADE_TO_BLACK"
    FADE_TO_WHITE = "FADE_TO_WHITE"
    WIPE = "WIPE"
    MATCH_CUT = "MATCH_CUT"
    JUMP_CUT = "JUMP_CUT"
    SMASH_CUT = "SMASH_CUT"


# ---- 元信息 ----

class ScriptMeta(BaseModel):
    title: str = Field(..., min_length=1, max_length=200, description="剧本标题")
    original_novel: str = Field(..., min_length=1, description="原著小说名称")
    version: int = Field(..., ge=1, description="剧本版本号")
    generated_at: datetime = Field(..., description="生成时间 (ISO 8601)")
    generator: Optional[str] = Field(None, description="生成器标识")
    scene_count: Optional[int] = Field(None, ge=0, description="场景总数")
    character_count: Optional[int] = Field(None, ge=0, description="角色总数")
    dialogue_count: Optional[int] = Field(None, ge=0, description="对白总数")


# ---- 角色定义 ----

class RelationshipDef(BaseModel):
    target: str = Field(..., description="关系目标角色名")
    relation: str = Field(..., description="关系类型，如 '恋人'、'兄弟'、'师生'、'敌人'")


class CharacterDef(BaseModel):
    id: int = Field(..., ge=1, description="角色唯一标识")
    name: str = Field(..., min_length=1, max_length=50, description="角色标准名称")
    aliases: List[str] = Field(default_factory=list, description="别名列表")
    role_type: CharacterRoleType = Field(..., description="角色类型")
    gender: Optional[Gender] = Field(None, description="性别")
    age_range: Optional[str] = Field(None, pattern=r"^\d+-\d+$", description="年龄段")
    description: str = Field(..., min_length=1, description="角色描述")
    personality: List[str] = Field(default_factory=list, description="性格特征列表")
    relationships: List[RelationshipDef] = Field(default_factory=list, description="角色关系列表")


# ---- 动作-对白序列 ----

class ActionItem(BaseModel):
    type: Literal["ACTION"] = "ACTION"
    character: Optional[str] = Field(None, description="执行者名称，null表示环境动作")
    action_type: ActionType = Field(..., description="动作类型")
    description: str = Field(..., min_length=1, description="动作描述 (必须可视化可拍摄)")
    duration_ms: Optional[int] = Field(None, ge=0, description="预估持续时间 (毫秒)")


class DialogueItem(BaseModel):
    type: Literal["DIALOGUE"] = "DIALOGUE"
    speaker: str = Field(..., min_length=1, description="说话人名称")
    emotion: Emotion = Field(..., description="情绪类型")
    content: str = Field(..., min_length=1, max_length=200, description="对白内容")
    parenthetical: Optional[str] = Field(None, description="括号说明，如 '(低声)'、'(冷笑)'")
    reply_to: Optional[int] = Field(None, ge=0, description="回复的对白序号")


SequenceItem = Union[ActionItem, DialogueItem]


# ---- 场景定义 ----

class SceneDef(BaseModel):
    scene_number: int = Field(..., ge=1, description="场景序号")
    heading: str = Field(..., min_length=1, description="标准场景头")
    location: str = Field(..., min_length=1, description="场景地点")
    time_of_day: TimeOfDay = Field(..., description="时间段")
    is_interior: bool = Field(..., description="是否为室内场景")
    summary: str = Field(..., min_length=10, max_length=500, description="场景摘要")
    mood: Optional[str] = Field(None, description="场景氛围")
    chapter_sources: List[int] = Field(default_factory=list, description="来源章节编号列表")
    present_characters: List[int] = Field(default_factory=list, description="出场角色ID列表")
    sequence: List[SequenceItem] = Field(..., min_length=1, description="场景内动作-对白时序序列")


# ---- 分镜扩展 ----

class ShotDef(BaseModel):
    shot_number: int = Field(..., ge=1, description="镜头序号")
    camera: CameraPosition = Field(..., description="机位")
    angle: CameraAngle = Field(..., description="角度")
    duration_sec: float = Field(..., gt=0, description="镜头时长 (秒)")
    description: str = Field(..., min_length=1, description="画面描述")
    movement: Optional[CameraMovement] = Field(None, description="运镜")
    transition: Optional[TransitionType] = Field(None, description="转场")


class StoryboardDef(BaseModel):
    scene_number: int = Field(..., ge=1, description="场景序号")
    shots: List[ShotDef] = Field(default_factory=list, description="镜头列表")


# ---- AI 配音扩展 ----

class VoiceoverDef(BaseModel):
    character_id: int = Field(..., ge=1, description="角色ID")
    voice_id: str = Field(..., min_length=1, description="音色标识")
    speed: Optional[float] = Field(None, gt=0, description="语速倍率")
    pitch: Optional[int] = Field(None, description="音调偏移 (半音)")
    pause_between: Optional[int] = Field(None, ge=0, description="句间停顿 (毫秒)")


# ---- 视频生成扩展 ----

class SceneTransitionDef(BaseModel):
    from_scene: int = Field(..., ge=1, description="源场景序号")
    to_scene: int = Field(..., ge=1, description="目标场景序号")
    transition: TransitionType = Field(..., description="转场类型")
    duration_sec: Optional[float] = Field(None, gt=0, description="转场时长 (秒)")


class VideoDef(BaseModel):
    default_transition: Optional[TransitionType] = Field(None, description="默认转场")
    scene_transitions: List[SceneTransitionDef] = Field(default_factory=list, description="场景转场列表")


# ---- 顶层剧本模型 ----

class ScriptYamlModel(BaseModel):
    """Novel2Script 剧本 YAML 顶层模型"""
    meta: ScriptMeta = Field(..., description="剧本元信息")
    characters: List[CharacterDef] = Field(..., min_length=1, description="角色定义列表")
    scenes: List[SceneDef] = Field(..., min_length=1, description="场景列表")
    storyboard: Optional[List[StoryboardDef]] = Field(None, description="分镜定义 (可选)")
    voiceover: Optional[List[VoiceoverDef]] = Field(None, description="AI配音标记 (可选)")
    video: Optional[VideoDef] = Field(None, description="视频生成标记 (可选)")
