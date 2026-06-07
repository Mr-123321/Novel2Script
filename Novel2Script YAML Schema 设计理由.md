# Novel2Script YAML Schema 设计理由

## 1. 总体设计原则

### 1.1 可扩展性 (Extensibility)

**设计决策**: 使用顶层命名空间隔离核心字段与扩展字段。

| 层级 | 字段 | 必需 | 说明 |
|------|------|------|------|
| 核心 | `meta`, `characters`, `scenes` | 是 | 剧本最小可用集合 |
| 扩展 | `storyboard` | 否 | 分镜，面向视频制作 |
| 扩展 | `voiceover` | 否 | AI配音，面向音频制作 |
| 扩展 | `video` | 否 | 视频生成，面向自动化生产 |

**理由**: 遵循 Open-Closed 原则 — 对扩展开放，对修改关闭。新增功能（如字幕、音乐标记）只需添加顶层字段，不影响已有解析逻辑。

**行业对标**: Final Draft (.fdx) 使用 XML 嵌套扩展，但 XML 冗长；YAML 的扁平顶层结构更易读写。Fountain 格式使用纯文本标记，但缺乏结构化扩展能力。

### 1.2 可逆解析 (Round-trip Fidelity)

**设计决策**: YAML → Java Record → YAML 无信息丢失。

实现要点:
- 所有枚举使用大写英文常量（`PROTAGONIST`），而非中文标签，避免编码歧义
- `null` 值字段在 YAML 中省略（`MINIMIZE_QUOTES` + `compressEmpty`），反序列化时默认为 `null`
- 时间使用 ISO 8601 格式（`2026-06-05T10:30:00`），Jackson `JavaTimeModule` 原生支持
- `sequence` 使用 `oneOf` 联合类型，通过 `type` 字段区分 ACTION/DIALOGUE，Jackson `@JsonTypeInfo` 自动处理

**理由**: 剧本需要支持编辑后重新导出，任何信息丢失都会导致数据不可逆。

### 1.3 影视剧本规范 (Screenplay Convention)

**设计决策**: 场景头格式遵循好莱坞标准剧本格式。

```
内/外 地点 - 时间
```

示例: `内 高三(5)班教室 - 日`、`外 操场 - 日`

**行业对标**:
- 好莱坞标准: `INT. CLASSROOM - DAY`
- 中文剧本惯例: `内 教室 - 日`
- 本 Schema 使用中文关键词（内/外）+ 中文地点名，符合国内影视行业习惯

## 2. 核心字段设计理由

### 2.1 meta — 元信息

| 字段 | 理由 |
|------|------|
| `title` | 剧本标题，独立于原著，允许改编后改名 |
| `original_novel` | 溯源原著，版权合规和追溯需要 |
| `version` | 支持多版本迭代，同一小说可生成不同风格剧本 |
| `generated_at` | 审计追踪，判断数据时效性 |
| `generator` | 标识生成工具版本，便于问题定位 |
| `scene_count` / `character_count` / `dialogue_count` | 冗余统计字段，避免前端遍历计算 |

### 2.2 characters — 角色定义

| 字段 | 理由 |
|------|------|
| `id` | 整数 ID 而非 UUID，YAML 中更简洁，场景内引用方便 |
| `name` | 标准名，去重后的唯一标识 |
| `aliases` | 别名列表，支持"林川/川哥/小川"同一角色多称呼 |
| `role_type` | 枚举分类，决定角色在剧本中的权重和出场频率 |
| `gender` | 配音选角和代词消歧需要 |
| `age_range` | 区间而非精确年龄，小说中年龄常模糊（"看起来二十出头"） |
| `personality` | 字符串数组而非枚举，性格特征开放且难以穷举 |
| `relationships` | 关系列表而非图结构，YAML 中更直观 |

**为什么 `id` 用整数而非字符串?**
- 场景内 `present_characters: [1, 2, 4]` 比 `["lin_chuan", "li_xue", "wang_teacher"]` 更简洁
- YAML 中整数引用更高效，解析更快
- 与数据库自增主键一致

### 2.3 scenes — 场景定义

| 字段 | 理由 |
|------|------|
| `scene_number` | 全局唯一序号，保证场景顺序 |
| `heading` | 标准场景头，直接可用于剧本打印 |
| `location` | 独立于 heading，便于按地点检索和分组 |
| `time_of_day` | 枚举而非自由文本，便于时间线分析 |
| `is_interior` | 布尔值，比解析 heading 中的"内/外"更可靠 |
| `summary` | 50-200字摘要，用于快速浏览和 AI 上下文 |
| `mood` | 氛围关键词，影响配乐和灯光选择 |
| `chapter_sources` | 溯源到小说章节，支持"点击定位原文" |
| `present_characters` | 角色ID列表，冗余但避免遍历 sequence |
| `sequence` | 动作-对白时序序列，场景的核心内容 |

**为什么 `heading` 和 `location`/`time_of_day`/`is_interior` 冗余?**
- `heading` 是给人看的（剧本打印），`location` 等是给程序用的（检索/分析）
- 避免解析 heading 字符串来提取结构化信息（容易出错）
- 冗余换来的可靠性值得

### 2.4 sequence — 动作-对白序列

**设计决策**: 使用 `oneOf` 联合类型，通过 `type` 字段区分。

```yaml
sequence:
  - type: "ACTION"
    character: "林川"
    description: "林川猛地睁开眼睛"
  - type: "DIALOGUE"
    speaker: "张大伟"
    emotion: "HAPPY"
    content: "川哥，你可算醒了！"
```

**为什么不用两个独立数组 `actions` 和 `dialogues`?**
- 影视剧本中动作和对白是交错的，时序关系至关重要
- 分离后无法表达"说话前先做了一个动作"这种时序
- 合并序列可直接按序渲染为剧本格式

**为什么 `ActionItem.character` 可为 null?**
- 环境描写（"上课铃声响起"）没有执行者
- null 语义清晰，避免使用 "NARRATOR" 等占位符

**为什么 `DialogueItem.reply_to` 用索引而非 ID?**
- YAML 中没有全局 ID 机制，使用序列内索引更自然
- 索引从 0 开始，与编程语言一致

## 3. 扩展字段设计理由

### 3.1 storyboard — 分镜

| 字段 | 理由 |
|------|------|
| `camera` | 机位枚举，覆盖从大特写到全景的所有标准机位 |
| `angle` | 角度枚举，包含仰拍/俯拍/荷兰角等 |
| `movement` | 运镜枚举，14种标准运镜方式 |
| `transition` | 转场枚举，10种标准转场 |
| `duration_sec` | 浮点数（秒），比毫秒更符合影视行业习惯 |

**行业对标**: 分镜字段设计参考了 Storyboarder 和 ShotPro 的数据结构，但简化为纯文本描述（不含图片），适配 AI 生成场景。

### 3.2 voiceover — AI 配音

| 字段 | 理由 |
|------|------|
| `voice_id` | 字符串而非枚举，不同 TTS 引擎的音色 ID 不同 |
| `speed` | 浮点数倍率（1.0=正常），比 BPM 更直观 |
| `pitch` | 整数半音偏移，音乐领域通用标准 |
| `pause_between` | 毫秒级精度，控制语速节奏 |

### 3.3 video — 视频生成

| 字段 | 理由 |
|------|------|
| `default_transition` | 全局默认转场，减少重复配置 |
| `scene_transitions` | 场景间转场覆盖，粒度更细 |

## 4. 中文友好设计

| 设计点 | 实现方式 |
|--------|----------|
| 角色名 | 直接使用中文字符串，SnakeYAML 原生支持 UTF-8 |
| 对白内容 | 中文不转义：`ESCAPE_NON_ASCII = false` |
| 场景头 | 中文格式 "内 教室 - 日"，符合国内剧本惯例 |
| 枚举值 | 使用英文大写常量（`PROTAGONIST`），避免中文枚举的编码问题 |
| 括号说明 | 中文括号说明（`(低声)`、`(冷笑)`），符合国内剧本写法 |

## 5. Schema 版本演进策略

| 变更类型 | 策略 | 示例 |
|----------|------|------|
| 新增可选字段 | 直接添加，不升版本 | 新增 `meta.author` |
| 新增扩展模块 | 新增顶层字段 | 新增 `subtitles` |
| 修改必填字段 | 升级 `meta.version` | `heading` 格式变更 |
| 删除字段 | 永不删除，标记 deprecated | — |

**向后兼容原则**: 旧版本 YAML 必须能被新版本 Schema 解析（缺失新字段使用默认值）。

## 6. 与数据库 Schema 的映射

| YAML 字段 | 数据库表 | 映射方式 |
|-----------|----------|----------|
| `meta` | `scripts` | 直接映射，`yaml_content` 存储完整 YAML |
| `characters[]` | `characters` | 一一映射，`aliases`/`personality`/`relationships` 存为 JSON |
| `scenes[]` | `scenes` | 一一映射，`chapter_sources` → `chapter_ids` (JSON) |
| `sequence[ACTION]` | `actions` | 过滤 type=ACTION 后映射 |
| `sequence[DIALOGUE]` | `dialogues` | 过滤 type=DIALOGUE 后映射 |
| `storyboard` | `shots` | 可选映射 |
