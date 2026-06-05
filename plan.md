# Novel2Script — 项目架构方案

> **版本**: v2.0  
> **日期**: 2026-06-05  
> **核心变更**: PostgreSQL → MySQL，架构全面优化

---

## 目录

1. [项目概述](#1-项目概述)
2. [核心架构决策](#2-核心架构决策)
3. [数据库选型：MySQL 替代 PostgreSQL](#3-数据库选型mysql-替代-postgresql)
4. [项目目录结构](#4-项目目录结构)
5. [Maven 模块划分](#5-maven-模块划分)
6. [DDD 分层设计](#6-ddd-分层设计)
7. [MySQL 数据库设计](#7-mysql-数据库设计)
8. [向量存储方案](#8-向量存储方案)
9. [API 设计](#9-api-设计)
10. [Agent 工作流引擎](#10-agent-工作流引擎)
11. [前端架构](#11-前端架构)
12. [Docker 部署方案](#12-docker-部署方案)
13. [实施路线图](#13-实施路线图)
14. [风险与对策](#14-风险与对策)

---

## 1. 项目概述

### 1.1 项目定位

Novel2Script 是一个 AI 驱动的小说转剧本系统，支持将 3 章以上、最长 100 万字的小说自动转换为符合影视行业规范的结构化剧本，输出标准 YAML 格式。

### 1.2 核心功能

| 功能 | 说明 |
|------|------|
| 小说上传 | 支持 .txt / .md / .docx 格式，流式解析 |
| 人物抽取 | AI 自动识别角色、别名、关系 |
| 剧情分析 | 提取关键事件、冲突、时间线 |
| 场景切分 | 按地点/时间/人物/冲突自动切分场景 |
| 对白生成 | 保持人物性格一致性的影视对白 |
| YAML 导出 | 符合行业规范的结构化剧本输出 |
| 在线编辑 | Web 端剧本编辑器，所见即所得 |

### 1.3 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| **前端** | Next.js + TypeScript + TailwindCSS + shadcn/ui | 15+ |
| **后端** | Java + Spring Boot | 21 / 3.5+ |
| **AI** | Spring AI + DeepSeek/OpenAI Compatible API | 1.0+ |
| **数据库** | **MySQL** | 8.0+ |
| **向量存储** | **Milvus Lite** (嵌入式) / **MySQL VECTOR** (9.0+) | — |
| **缓存** | Redis | 7.x |
| **消息队列** | RabbitMQ (可选，异步任务) | 3.x |
| **YAML** | Jackson YAML / SnakeYAML | 2.x |
| **部署** | Docker Compose | — |

---

## 2. 核心架构决策

### 2.1 决策矩阵

| 决策点 | 选择 | 替代方案 | 理由 |
|--------|------|----------|------|
| 数据库 | **MySQL 8.0** | PostgreSQL | 比赛环境兼容性更好，运维成本低 |
| 向量存储 | **Milvus Lite** | PGVector / Weaviate | 嵌入式部署，无需额外服务 |
| ORM | **MyBatis-Plus** | JPA / Hibernate | MySQL 生态最佳适配，灵活性强 |
| AI 框架 | **Spring AI** | LangChain4j | Spring 生态原生集成 |
| 消息队列 | **RabbitMQ** | Kafka | 轻量级，适合工作流场景 |
| 前端 | **Next.js 15** | Vite + React | SSR 友好，生态完善 |

### 2.2 架构全景图

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (Next.js)                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │ 上传页面 │ │ 剧本编辑 │ │ 人物管理 │ │ YAML 预览/导出   │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘  │
└──────────────────────────┬──────────────────────────────────────┘
                           │ REST API / WebSocket
┌──────────────────────────▼──────────────────────────────────────┐
│                     Spring Boot 3.5 (Java 21)                    │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    API 层 (Controller)                     │   │
│  │  NovelController │ ScriptController │ ExportController    │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│                             │                                    │
│  ┌──────────────────────────▼───────────────────────────────┐   │
│  │                   应用层 (Service)                         │   │
│  │  NovelService │ ScriptService │ ExportService             │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│                             │                                    │
│  ┌──────────────────────────▼───────────────────────────────┐   │
│  │                 Agent 工作流引擎                           │   │
│  │  WorkflowEngine → [ChapterParser → CharacterAgent → ...]  │   │
│  │  StateManager │ RetryHandler │ AuditLogger                │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│                             │                                    │
│  ┌──────────────────────────▼───────────────────────────────┐   │
│  │                   领域层 (Domain)                          │   │
│  │  Novel │ Chapter │ Character │ Scene │ Dialogue │ Script  │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│                             │                                    │
│  ┌──────────────────────────▼───────────────────────────────┐   │
│  │                 基础设施层 (Infrastructure)                │   │
│  │  MyBatis-Plus │ Milvus Client │ Spring AI │ Redis         │   │
│  └──────────────────────────────────────────────────────────┘   │
└──────────────────────────┬──────────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
   ┌─────────┐      ┌──────────┐      ┌──────────┐
   │  MySQL  │      │  Milvus  │      │  Redis   │
   │  8.0+   │      │   Lite   │      │   7.x    │
   └─────────┘      └──────────┘      └──────────┘
```



## 3. 项目目录结构

```
novel2script/
│
├── README.md
├── plan.md                          # 本文档
├── docker-compose.yml               # 一键部署
├── .env.example                     # 环境变量模板
├── Makefile                         # 常用命令
│
├── backend/                         # Java 后端
│   ├── pom.xml                      # 父 POM
│   │
│   ├── novel2script-common/         # 公共模块
│   │   └── src/main/java/com/novel2script/common/
│   │       ├── enums/               # 枚举（角色类型、情绪等）
│   │       ├── exception/           # 业务异常
│   │       ├── util/                # 工具类
│   │       └── constant/            # 常量
│   │
│   ├── novel2script-domain/         # 领域模型
│   │   └── src/main/java/com/novel2script/domain/
│   │       ├── model/               # 领域实体
│   │       │   ├── Novel.java
│   │       │   ├── Chapter.java
│   │       │   ├── Character.java
│   │       │   ├── Scene.java
│   │       │   ├── Dialogue.java
│   │       │   ├── Action.java
│   │       │   ├── PlotEvent.java
│   │       │   └── Script.java
│   │       ├── vo/                  # 值对象
│   │       ├── dto/                 # 数据传输对象
│   │       └── event/               # 领域事件
│   │
│   ├── novel2script-infrastructure/ # 基础设施
│   │   └── src/main/java/com/novel2script/infrastructure/
│   │       ├── config/              # 配置类
│   │       │   ├── MyBatisPlusConfig.java
│   │       │   ├── MilvusConfig.java
│   │       │   ├── RedisConfig.java
│   │       │   ├── SpringAiConfig.java
│   │       │   └── WebMvcConfig.java
│   │       ├── persistence/         # 持久化
│   │       │   ├── mapper/          # MyBatis Mapper
│   │       │   ├── repository/      # Repository 实现
│   │       │   └── converter/       # PO ↔ Domain 转换
│   │       ├── vector/              # 向量存储
│   │       │   ├── MilvusVectorStore.java
│   │       │   ├── EmbeddingService.java
│   │       │   └── SimilaritySearchService.java
│   │       ├── ai/                  # AI 基础设施
│   │       │   ├── client/          # AI 客户端封装
│   │       │   ├── retry/           # 重试策略
│   │       │   └── monitor/         # 调用监控
│   │       └── mq/                  # 消息队列（可选）
│   │           ├── producer/
│   │           └── consumer/
│   │
│   ├── novel2script-application/    # 应用服务层
│   │   └── src/main/java/com/novel2script/application/
│   │       ├── service/             # 应用服务
│   │       │   ├── NovelService.java
│   │       │   ├── ScriptService.java
│   │       │   └── ExportService.java
│   │       ├── agent/               # AI Agent
│   │       │   ├── ChapterParserAgent.java
│   │       │   ├── CharacterAgent.java
│   │       │   ├── CharacterResolverAgent.java
│   │       │   ├── PlotExtractionAgent.java
│   │       │   ├── SceneAgent.java
│   │       │   ├── DialogueAgent.java
│   │       │   ├── ActionAgent.java
│   │       │   ├── StoryboardAgent.java
│   │       │   └── ScriptComposerAgent.java
│   │       ├── workflow/            # 工作流引擎
│   │       │   ├── WorkflowEngine.java
│   │       │   ├── WorkflowState.java
│   │       │   ├── WorkflowStep.java
│   │       │   ├── RetryPolicy.java
│   │       │   └── WorkflowVisualizer.java
│   │       ├── prompt/              # Prompt 管理
│   │       │   ├── PromptTemplate.java
│   │       │   ├── PromptRegistry.java
│   │       │   ├── PromptVersion.java
│   │       │   ├── FewShotManager.java
│   │       │   ├── PromptCache.java
│   │       │   └── PromptAudit.java
│   │       ├── parser/              # 小说解析
│   │       │   ├── ChapterParser.java
│   │       │   ├── StreamParser.java
│   │       │   └── NovelReader.java
│   │       └── exporter/            # 导出
│   │           ├── YamlExporter.java
│   │           └── SchemaValidator.java
│   │
│   └── novel2script-api/            # REST API (启动模块)
│       └── src/main/java/com/novel2script/api/
│           ├── Novel2ScriptApplication.java
│           ├── controller/
│           │   ├── NovelController.java
│           │   ├── ScriptController.java
│           │   └── ExportController.java
│           ├── advice/
│           │   └── GlobalExceptionHandler.java
│           └── config/
│               └── SwaggerConfig.java
│       └── src/main/resources/
│           ├── application.yml
│           ├── application-dev.yml
│           ├── application-prod.yml
│           ├── db/
│           │   └── migration/       # Flyway 迁移脚本
│           │       ├── V1__init_schema.sql
│           │       ├── V2__seed_prompts.sql
│           │       └── V3__add_indexes.sql
│           └── prompt/              # Prompt 模板文件
│               ├── character_extraction.yml
│               ├── plot_extraction.yml
│               ├── scene_segmentation.yml
│               ├── dialogue_generation.yml
│               └── action_generation.yml
│
├── frontend/                        # Next.js 前端
│   ├── package.json
│   ├── next.config.js
│   ├── tailwind.config.ts
│   ├── tsconfig.json
│   ├── components.json              # shadcn/ui 配置
│   ├── src/
│   │   ├── app/                     # App Router
│   │   │   ├── layout.tsx
│   │   │   ├── page.tsx             # 首页（上传）
│   │   │   ├── script/
│   │   │   │   ├── [id]/
│   │   │   │   │   ├── page.tsx     # 剧本详情/编辑
│   │   │   │   │   └── yaml/
│   │   │   │   │       └── page.tsx # YAML 预览
│   │   │   │   └── new/
│   │   │   │       └── page.tsx     # 新建剧本
│   │   │   └── characters/
│   │   │       └── page.tsx         # 人物管理
│   │   ├── components/
│   │   │   ├── ui/                  # shadcn/ui 组件
│   │   │   ├── novel/               # 小说相关组件
│   │   │   │   ├── UploadZone.tsx
│   │   │   │   └── ChapterList.tsx
│   │   │   ├── script/              # 剧本相关组件
│   │   │   │   ├── ScriptEditor.tsx
│   │   │   │   ├── SceneCard.tsx
│   │   │   │   ├── DialogueEditor.tsx
│   │   │   │   └── YamlPreview.tsx
│   │   │   ├── character/           # 人物组件
│   │   │   │   ├── CharacterCard.tsx
│   │   │   │   └── CharacterEditor.tsx
│   │   │   └── workflow/            # 工作流可视化
│   │   │       └── WorkflowChart.tsx
│   │   ├── hooks/                   # 自定义 Hooks
│   │   ├── lib/                     # 工具函数
│   │   │   ├── api.ts               # API 客户端
│   │   │   └── utils.ts
│   │   └── types/                   # TypeScript 类型
│   │       ├── novel.ts
│   │       ├── script.ts
│   │       └── api.ts
│   └── public/
│
├── docs/                            # 文档
│   ├── architecture.md              # 架构文档
│   ├── api-spec.yaml                # OpenAPI 规范
│   ├── yaml-schema.md               # YAML Schema 文档
│   ├── prompt-engineering.md        # Prompt 工程设计
│   └── defense-presentation.md      # 答辩材料
│
└── scripts/                         # 运维脚本
    ├── init-db.sql                  # 数据库初始化
    ├── seed-data.sql                # 测试数据
    └── deploy.sh                    # 部署脚本
```

---

## 4. Maven 模块划分

### 4.1 父 POM 配置

```xml
<!-- backend/pom.xml -->
<groupId>com.novel2script</groupId>
<artifactId>novel2script</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>pom</packaging>

<modules>
    <module>novel2script-common</module>
    <module>novel2script-domain</module>
    <module>novel2script-infrastructure</module>
    <module>novel2script-application</module>
    <module>novel2script-api</module>
</modules>

<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.5.0</spring-boot.version>
    <spring-ai.version>1.0.0-M6</spring-ai.version>
    <mybatis-plus.version>3.5.9</mybatis-plus.version>
    <milvus-sdk.version>2.5.0</milvus-sdk.version>
    <redisson.version>3.36.0</redisson.version>
    <snakeyaml.version>2.3</snakeyaml.version>
</properties>
```

### 4.2 模块依赖关系

```
novel2script-api (启动模块)
    ├── novel2script-application
    │       ├── novel2script-domain
    │       ├── novel2script-infrastructure
    │       └── novel2script-common
    ├── novel2script-infrastructure
    │       ├── novel2script-domain
    │       └── novel2script-common
    └── novel2script-common (无依赖)
```

### 4.3 模块职责

| 模块 | 职责 | 关键依赖 |
|------|------|----------|
| `common` | 枚举、异常、工具类、常量 | 无 |
| `domain` | 领域实体、VO、DTO、领域事件 | common |
| `infrastructure` | 持久化、向量存储、AI 客户端、缓存 | domain, MyBatis-Plus, Milvus SDK, Spring AI |
| `application` | 应用服务、Agent、工作流、Prompt 管理 | infrastructure |
| `api` | REST Controller、全局异常处理、Swagger | application, Spring Boot Web |

---

## 5. DDD 分层设计

### 5.1 分层架构

```
┌────────────────────────────────────────┐
│          Interface (API)               │  ← Controller / DTO
├────────────────────────────────────────┤
│          Application                   │  ← Service / Agent / Workflow
├────────────────────────────────────────┤
│          Domain                        │  ← Entity / VO / Domain Event
├────────────────────────────────────────┤
│          Infrastructure                │  ← Repository / Mapper / Config
└────────────────────────────────────────┘
```

### 5.2 聚合根设计

```
Novel (聚合根)
  └── Chapter[] (实体)
        └── Paragraph[] (值对象)

Script (聚合根)
  └── Character[] (实体)
  └── Scene[] (实体)
        └── Dialogue[] (实体)
        └── Action[] (实体)
        └── Shot[] (实体, 可选)
  └── PlotEvent[] (实体)
```

### 5.3 领域事件流

```
NovelUploaded
  → NovelParsed
    → ChaptersExtracted
      → CharactersExtracted
        → CharactersResolved
          → PlotExtracted
            → ScenesSegmented
              → DialoguesGenerated
                → ActionsGenerated
                  → ScriptComposed
                    → YamlExported
```

### 5.4 仓储接口（面向接口编程）

```java
// 定义在 domain 层，实现在 infrastructure 层
public interface NovelRepository {
    Novel save(Novel novel);
    Optional<Novel> findById(Long id);
    List<Novel> findByUserId(Long userId);
    void deleteById(Long id);
}

public interface ScriptRepository {
    Script save(Script script);
    Optional<Script> findById(Long id);
    List<Script> findByNovelId(Long novelId);
}

public interface CharacterVectorRepository {
    void insert(CharacterEmbedding embedding);
    List<CharacterSimilarity> findSimilar(float[] embedding, int topK, float threshold);
    void deleteByNovelId(Long novelId);
}
```

---

## 6. MySQL 数据库设计

### 6.1 ER 图（文字版）

```
novels ──1:N──> chapters
novels ──1:1──> scripts
scripts ──1:N──> characters
scripts ──1:N──> plot_events
scripts ──1:N──> scenes
scenes ──1:N──> dialogues
scenes ──1:N──> actions
scenes ──1:N──> shots (分镜)
characters ──N:N──> scenes (scene_characters)
```

### 6.2 完整 DDL

```sql
-- ============================================================
-- Novel2Script MySQL Schema v2.0
-- 引擎: InnoDB | 字符集: utf8mb4 | 排序: utf8mb4_unicode_ci
-- ============================================================

CREATE DATABASE IF NOT EXISTS novel2script
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE novel2script;

-- ----------------------------
-- 1. 小说表
-- ----------------------------
CREATE TABLE novels (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(500) NOT NULL COMMENT '小说标题',
    author          VARCHAR(200) COMMENT '作者',
    file_name       VARCHAR(500) NOT NULL COMMENT '上传文件名',
    file_size       BIGINT NOT NULL COMMENT '文件大小(字节)',
    total_chars     INT NOT NULL DEFAULT 0 COMMENT '总字数',
    chapter_count   INT NOT NULL DEFAULT 0 COMMENT '章节数',
    status          VARCHAR(20) NOT NULL DEFAULT 'UPLOADED'
                        COMMENT '状态: UPLOADED/PARSING/PARSED/PROCESSING/COMPLETED/FAILED',
    metadata        JSON COMMENT '元数据(JSON)',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_novels_status (status),
    INDEX idx_novels_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说表';

-- ----------------------------
-- 2. 章节表
-- ----------------------------
CREATE TABLE chapters (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id        BIGINT NOT NULL,
    chapter_number  INT NOT NULL COMMENT '章节序号',
    title           VARCHAR(500) COMMENT '章节标题',
    content         MEDIUMTEXT NOT NULL COMMENT '章节内容',
    char_count      INT NOT NULL DEFAULT 0 COMMENT '字数',
    start_offset    BIGINT NOT NULL DEFAULT 0 COMMENT '在原文中的起始偏移',
    end_offset      BIGINT NOT NULL DEFAULT 0 COMMENT '在原文中的结束偏移',
    embedding_id   VARCHAR(200) COMMENT 'Milvus 中的嵌入 ID',
    status          VARCHAR(20) NOT NULL DEFAULT 'RAW'
                        COMMENT '状态: RAW/PARSED/EMBEDDED',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_chapters_novel (novel_id),
    INDEX idx_chapters_embedding (embedding_id),
    CONSTRAINT fk_chapters_novel FOREIGN KEY (novel_id)
        REFERENCES novels(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节表';

-- ----------------------------
-- 3. 剧本表
-- ----------------------------
CREATE TABLE scripts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id        BIGINT NOT NULL UNIQUE,
    title           VARCHAR(500) NOT NULL COMMENT '剧本标题',
    version         INT NOT NULL DEFAULT 1 COMMENT '版本号',
    scene_count     INT NOT NULL DEFAULT 0 COMMENT '场景数',
    character_count INT NOT NULL DEFAULT 0 COMMENT '角色数',
    dialogue_count  INT NOT NULL DEFAULT 0 COMMENT '对白数',
    yaml_content    MEDIUMTEXT COMMENT '导出的 YAML 内容',
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                        COMMENT '状态: DRAFT/GENERATING/COMPLETED/FAILED',
    progress        DECIMAL(5,2) NOT NULL DEFAULT 0.00
                        COMMENT '生成进度 0.00-100.00',
    workflow_state  JSON COMMENT '工作流状态快照',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_scripts_novel (novel_id),
    INDEX idx_scripts_status (status),
    CONSTRAINT fk_scripts_novel FOREIGN KEY (novel_id)
        REFERENCES novels(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='剧本表';

-- ----------------------------
-- 4. 角色表
-- ----------------------------
CREATE TABLE characters (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id       BIGINT NOT NULL,
    canonical_name  VARCHAR(200) NOT NULL COMMENT '标准名称',
    aliases         JSON COMMENT '别名列表 ["川哥","小川","林师兄"]',
    role_type       VARCHAR(20) NOT NULL DEFAULT 'SUPPORTING'
                        COMMENT '角色类型: PROTAGONIST/ANTAGONIST/SUPPORTING/MINOR',
    gender          VARCHAR(10) COMMENT '性别: MALE/FEMALE/UNKNOWN',
    age_range       VARCHAR(50) COMMENT '年龄段描述',
    description     TEXT COMMENT '角色描述',
    personality     JSON COMMENT '性格特征 ["冷静","果断","腹黑"]',
    relationships   JSON COMMENT '角色关系 [{"target":"李雪","relation":"恋人"}]',
    appearance_count INT NOT NULL DEFAULT 0 COMMENT '出场次数',
    first_appearance BIGINT COMMENT '首次出场章节 ID',
    embedding_id    VARCHAR(200) COMMENT 'Milvus 中的嵌入 ID (用于去重)',
    is_resolved     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已去重合并',
    merged_from     JSON COMMENT '合并来源角色 ID 列表',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_characters_script (script_id),
    INDEX idx_characters_embedding (embedding_id),
    CONSTRAINT fk_characters_script FOREIGN KEY (script_id)
        REFERENCES scripts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- ----------------------------
-- 5. 剧情事件表
-- ----------------------------
CREATE TABLE plot_events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id       BIGINT NOT NULL,
    event_order     INT NOT NULL COMMENT '事件序号',
    title           VARCHAR(500) NOT NULL COMMENT '事件标题',
    description     TEXT COMMENT '事件描述',
    location        VARCHAR(500) COMMENT '发生地点',
    time_point      VARCHAR(200) COMMENT '时间点描述',
    conflict_type   VARCHAR(50) COMMENT '冲突类型: PERSON_VS_PERSON/PERSON_VS_SELF/...',
    chapter_ids     JSON COMMENT '关联章节 ID [1,2,3]',
    character_ids   JSON COMMENT '参与角色 ID [1,5,8]',
    importance      INT NOT NULL DEFAULT 3 COMMENT '重要程度 1-5',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_plot_script (script_id),
    CONSTRAINT fk_plot_script FOREIGN KEY (script_id)
        REFERENCES scripts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='剧情事件表';

-- ----------------------------
-- 6. 场景表
-- ----------------------------
CREATE TABLE scenes (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id       BIGINT NOT NULL,
    scene_number    INT NOT NULL COMMENT '场景序号',
    title           VARCHAR(500) COMMENT '场景标题',
    location        VARCHAR(500) NOT NULL COMMENT '地点',
    time_of_day     VARCHAR(50) COMMENT '时间: MORNING/AFTERNOON/EVENING/NIGHT/DAWN/DUSK',
    is_interior     TINYINT(1) NOT NULL DEFAULT 1 COMMENT '室内/室外: 1=INT, 0=EXT',
    summary         TEXT COMMENT '场景摘要',
    mood            VARCHAR(100) COMMENT '氛围',
    chapter_ids     JSON COMMENT '来源章节 ID',
    source_reason   VARCHAR(20) COMMENT '切分原因: LOCATION/TIME/CHARACTER/CONFLICT',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_scenes_script (script_id),
    CONSTRAINT fk_scenes_script FOREIGN KEY (script_id)
        REFERENCES scripts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场景表';

-- ----------------------------
-- 7. 场景-角色关联表
-- ----------------------------
CREATE TABLE scene_characters (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    scene_id        BIGINT NOT NULL,
    character_id    BIGINT NOT NULL,
    role_in_scene   VARCHAR(100) COMMENT '此场景中的角色描述',

    UNIQUE KEY uk_scene_character (scene_id, character_id),
    INDEX idx_sc_char_scene (scene_id),
    INDEX idx_sc_char_character (character_id),
    CONSTRAINT fk_sc_scene FOREIGN KEY (scene_id)
        REFERENCES scenes(id) ON DELETE CASCADE,
    CONSTRAINT fk_sc_character FOREIGN KEY (character_id)
        REFERENCES characters(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场景角色关联表';

-- ----------------------------
-- 8. 对白表
-- ----------------------------
CREATE TABLE dialogues (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    scene_id        BIGINT NOT NULL,
    character_id    BIGINT NOT NULL,
    sequence        INT NOT NULL COMMENT '对白序号',
    speaker         VARCHAR(200) NOT NULL COMMENT '说话人名称',
    emotion         VARCHAR(50) COMMENT '情绪: ANGRY/HAPPY/SAD/CALM/FEARFUL/SURPRISED/...',
    content         TEXT NOT NULL COMMENT '对白内容',
    parenthetical   VARCHAR(200) COMMENT '括号说明: (低声)/(冷笑)',
    reply_to        BIGINT COMMENT '回复的对白 ID',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_dialogues_scene (scene_id),
    INDEX idx_dialogues_character (character_id),
    CONSTRAINT fk_dialogues_scene FOREIGN KEY (scene_id)
        REFERENCES scenes(id) ON DELETE CASCADE,
    CONSTRAINT fk_dialogues_character FOREIGN KEY (character_id)
        REFERENCES characters(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对白表';

-- ----------------------------
-- 9. 动作表
-- ----------------------------
CREATE TABLE actions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    scene_id        BIGINT NOT NULL,
    character_id    BIGINT COMMENT '执行动作的角色，NULL 表示环境描述',
    sequence        INT NOT NULL COMMENT '动作序号',
    action_type     VARCHAR(50) NOT NULL DEFAULT 'ACTION'
                        COMMENT '类型: ACTION/REACTION/BEAT/BUSINESS',
    description     TEXT NOT NULL COMMENT '动作描述',
    duration_ms     INT COMMENT '预估持续时间(毫秒)',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_actions_scene (scene_id),
    INDEX idx_actions_character (character_id),
    CONSTRAINT fk_actions_scene FOREIGN KEY (scene_id)
        REFERENCES scenes(id) ON DELETE CASCADE,
    CONSTRAINT fk_actions_character FOREIGN KEY (character_id)
        REFERENCES characters(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作表';

-- ----------------------------
-- 10. 分镜表 (加分项)
-- ----------------------------
CREATE TABLE shots (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    scene_id        BIGINT NOT NULL,
    shot_number     INT NOT NULL COMMENT '分镜序号',
    camera          VARCHAR(50) COMMENT '机位: WIDE/MEDIUM/CLOSE_UP/EXTREME_CU/POV/...',
    angle           VARCHAR(50) COMMENT '角度: EYE_LEVEL/HIGH_ANGLE/LOW_ANGLE/DUTCH/...',
    duration_sec    DECIMAL(5,1) COMMENT '持续时间(秒)',
    description     TEXT COMMENT '画面描述',
    movement        VARCHAR(50) COMMENT '运镜: STATIC/PAN/TILT/DOLLY/ZOOM/...',
    transition      VARCHAR(50) COMMENT '转场: CUT/DISSOLVE/FADE/WIPE',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_shots_scene (scene_id),
    CONSTRAINT fk_shots_scene FOREIGN KEY (scene_id)
        REFERENCES scenes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分镜表';

-- ----------------------------
-- 11. Prompt 审计表
-- ----------------------------
CREATE TABLE prompt_audits (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    prompt_name     VARCHAR(200) NOT NULL COMMENT 'Prompt 模板名称',
    prompt_version  VARCHAR(50) NOT NULL COMMENT '版本号',
    model_name      VARCHAR(100) NOT NULL COMMENT '使用的模型',
    input_tokens    INT NOT NULL DEFAULT 0 COMMENT '输入 Token 数',
    output_tokens   INT NOT NULL DEFAULT 0 COMMENT '输出 Token 数',
    latency_ms      INT NOT NULL DEFAULT 0 COMMENT '响应延迟(毫秒)',
    retry_count     INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    is_success      TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否成功',
    error_message   TEXT COMMENT '错误信息',
    full_prompt     MEDIUMTEXT COMMENT '完整 Prompt (用于调试)',
    full_response   MEDIUMTEXT COMMENT '完整响应 (用于调试)',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_audit_name (prompt_name),
    INDEX idx_audit_created (created_at),
    INDEX idx_audit_success (is_success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Prompt 审计表';

-- ----------------------------
-- 12. 工作流执行记录表
-- ----------------------------
CREATE TABLE workflow_executions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id       BIGINT NOT NULL,
    workflow_type   VARCHAR(100) NOT NULL COMMENT '工作流类型: FULL_GENERATION/PARTIAL',
    current_step    VARCHAR(100) COMMENT '当前步骤名称',
    state           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        COMMENT '状态: PENDING/RUNNING/COMPLETED/FAILED/PAUSED',
    state_snapshot  JSON COMMENT '状态快照 (可序列化恢复)',
    error_detail    TEXT COMMENT '错误详情',
    started_at      DATETIME COMMENT '开始时间',
    completed_at    DATETIME COMMENT '完成时间',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_workflow_script (script_id),
    INDEX idx_workflow_state (state),
    INDEX idx_workflow_type (workflow_type),
    CONSTRAINT fk_workflow_script FOREIGN KEY (script_id)
        REFERENCES scripts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流执行记录表';
```

---

## 7. 向量存储方案

### 7.1 Milvus Lite 集成

```java
// MilvusVectorStore.java - 核心接口
public interface MilvusVectorStore {
    /**
     * 插入文本嵌入向量
     */
    void insert(String collectionName, String id, float[] embedding, Map<String, Object> metadata);

    /**
     * 相似度搜索 - 用于人物去重
     */
    List<SearchResult> search(String collectionName, float[] queryEmbedding,
                              int topK, float scoreThreshold);

    /**
     * 按 ID 删除
     */
    void deleteByIds(String collectionName, List<String> ids);

    /**
     * 创建集合
     */
    void createCollection(String collectionName, int dimension, IndexType indexType);
}
```

### 7.2 向量集合设计

| 集合名 | 维度 | 索引 | 用途 |
|--------|------|------|------|
| `character_embeddings` | 1536 | IVF_FLAT | 人物去重 |
| `chapter_embeddings` | 1536 | IVF_FLAT | 长文本检索 |
| `scene_embeddings` | 1536 | HNSW | 场景相似度 |

### 7.3 降级策略

```yaml
# 当 Milvus 不可用时的降级方案
fallback_strategy:
  level_1:
    name: "MySQL JSON 存储 + 应用层余弦相似度"
    condition: "数据集 < 1000 条向量"
    performance: "可接受 (< 100ms)"
  
  level_2:
    name: "MySQL 9.0 VECTOR 类型"
    condition: "MySQL 版本 >= 9.0"
    note: "原生向量索引支持"
```

---

## 8. API 设计

### 8.1 REST API 端点

```
POST   /api/v1/novels/upload          # 上传小说
GET    /api/v1/novels                  # 小说列表
GET    /api/v1/novels/{id}             # 小说详情
DELETE /api/v1/novels/{id}             # 删除小说

POST   /api/v1/scripts/generate        # 生成剧本
GET    /api/v1/scripts                 # 剧本列表
GET    /api/v1/scripts/{id}            # 剧本详情
GET    /api/v1/scripts/{id}/yaml       # 导出 YAML
GET    /api/v1/scripts/{id}/progress   # 生成进度 (SSE)
PUT    /api/v1/scripts/{id}/scenes/{sceneId}      # 编辑场景
PUT    /api/v1/scripts/{id}/dialogues/{dialogueId} # 编辑对白
PUT    /api/v1/scripts/{id}/characters/{charId}    # 编辑角色

GET    /api/v1/scripts/{id}/storyboard  # 分镜数据
GET    /api/v1/scripts/{id}/workflow    # 工作流状态
```

### 8.2 核心 DTO

```java
// 上传响应
public record NovelUploadResponse(
    Long novelId,
    String title,
    int chapterCount,
    int totalChars,
    String status
) {}

// 生成请求
public record ScriptGenerateRequest(
    @NotNull Long novelId,
    @Min(1) @Max(50) int maxScenes,
    String style,                    // 剧本风格
    List<String> focusCharacters     // 重点关注角色
) {}

// 生成进度 (SSE)
public record GenerationProgress(
    Long scriptId,
    String currentStep,
    double progress,                 // 0.00 - 100.00
    String message,
    Instant timestamp
) {}
```

---

## 9. Agent 工作流引擎

### 9.1 工作流状态机

```
                    ┌──────────┐
                    │  PENDING  │
                    └─────┬─────┘
                          │ start()
                    ┌─────▼─────┐
              ┌─────│  RUNNING  │─────┐
              │     └─────┬─────┘     │
              │ fail()    │           │ stepComplete()
              │     ┌─────▼─────┐     │
              │     │  RETRYING │     │
              │     └─────┬─────┘     │
              │           │ retry()   │
              │           ▼           │
              │     (back to RUNNING) │
              │                       │
              │ maxRetries exceeded   │
              │           ▼           │
        ┌─────┴───┐            ┌──────▼──────┐
        │  FAILED  │            │  COMPLETED  │
        └──────────┘            └─────────────┘
```

### 9.2 工作流步骤定义

```java
public enum WorkflowStep {
    CHAPTER_PARSE       ("ChapterParserAgent",       1,  false),
    CHARACTER_EXTRACT   ("CharacterAgent",           2,  true),   // 可重试
    CHARACTER_RESOLVE   ("CharacterResolverAgent",   3,  true),
    PLOT_EXTRACT        ("PlotExtractionAgent",      4,  true),
    SCENE_SEGMENT       ("SceneAgent",               5,  true),
    DIALOGUE_GENERATE   ("DialogueAgent",            6,  true),
    ACTION_GENERATE     ("ActionAgent",              7,  true),
    SCRIPT_COMPOSE      ("ScriptComposerAgent",      8,  false),
    YAML_EXPORT         ("YamlExporter",             9,  false),
    STORYBOARD_GENERATE ("StoryboardAgent",          10, true);   // 可选
}
```

### 9.3 重试策略

```java
public record RetryPolicy(
    int maxRetries,           // 最大重试次数 (默认3)
    long initialDelayMs,      // 初始延迟 (默认1000ms)
    double backoffMultiplier, // 退避倍数 (默认2.0)
    long maxDelayMs,          // 最大延迟 (默认30000ms)
    List<Class<? extends Throwable>> retryableExceptions
) {}
```

---

## 10. 前端架构

### 10.1 页面路由

```
/                          # 首页 - 上传小说
/novels                    # 小说列表
/novels/[id]               # 小说详情
/scripts                   # 剧本列表
/scripts/[id]              # 剧本编辑 (核心页面)
/scripts/[id]/yaml         # YAML 预览/导出
/scripts/[id]/storyboard   # 分镜预览
/characters                # 人物库管理
```

### 10.2 核心组件树

```
ScriptEditor (核心编辑页面)
├── WorkflowProgress       # 生成进度条 + 步骤可视化
├── CharacterPanel         # 人物侧边栏
│   ├── CharacterCard[]    # 人物卡片列表
│   └── CharacterEditor    # 人物编辑抽屉
├── SceneList              # 场景列表
│   ├── SceneCard[]        # 场景卡片
│   │   ├── SceneHeader    # 场景头 (地点/时间/内外景)
│   │   ├── ActionBlock[]  # 动作描述块
│   │   └── DialogueBlock[]# 对白块
│   │       ├── SpeakerLabel
│   │       ├── EmotionTag
│   │       └── ContentEditor
│   └── AddSceneButton
└── Toolbar
    ├── ExportYamlButton
    ├── UndoRedoButtons
    └── ViewToggle
```

### 10.3 状态管理

```typescript
// 使用 Zustand 进行轻量级状态管理
interface ScriptStore {
  script: Script | null;
  characters: Character[];
  scenes: Scene[];
  currentStep: WorkflowStep;
  progress: number;

  // Actions
  setScript: (script: Script) => void;
  updateScene: (sceneId: number, scene: Partial<Scene>) => void;
  updateDialogue: (dialogueId: number, dialogue: Partial<Dialogue>) => void;
  setProgress: (step: WorkflowStep, progress: number) => void;
}
```

---

---

## 10. 实施路线图

### 阶段划分（4 周）

```
Week 1: 基础设施 + 核心领域
  Day 1-2: 项目骨架搭建 (Maven 多模块, DDD 分层)
  Day 3-4: MySQL Schema + Flyway 迁移 + MyBatis-Plus 配置
  Day 5-6: 领域模型 (Java Record) + 基础 CRUD
  Day 7:   Docker Compose 环境搭建 + CI 验证

Week 2: AI 基础设施
  Day 1-2: Spring AI 多模型配置 (DeepSeek/OpenAI/Claude)
  Day 3-4: Prompt 管理框架 (模板/版本/缓存/审计)
  Day 5:   ChapterParser + 流式解析
  Day 6-7: Milvus 集成 + EmbeddingService

Week 3: Agent 流水线
  Day 1:   CharacterAgent + CharacterResolver
  Day 2:   PlotExtractionAgent
  Day 3:   SceneAgent + DialogueAgent
  Day 4:   ActionAgent + ScriptComposer
  Day 5:   YamlExporter + Schema 校验
  Day 6-7: WorkflowEngine + 状态管理 + 失败恢复

Week 4: API + 前端 + 交付
  Day 1:   REST API (所有端点) + Swagger
  Day 2:   全局异常处理 + 参数校验
  Day 3-4: 前端页面开发 (上传/编辑/YAML预览)
  Day 5:   StoryboardAgent (分镜加分项)
  Day 6:   端到端测试 + 性能优化
  Day 7:   文档 + 答辩材料 + 录制 Demo
```

---

## 10. 风险与对策

| 风险 | 等级 | 对策 |
|------|------|------|
| MySQL 向量检索性能不足 | 🟡 中 | 优先使用 Milvus Lite；备选 MySQL 9.0 VECTOR |
| AI API 调用不稳定 | 🟡 中 | 指数退避重试 + 本地缓存 + 降级策略 |
| 长小说 Token 超限 | 🔴 高 | PGVector → Milvus 分块检索 + Context Window 管理 |
| 人物去重准确率低 | 🟡 中 | 多策略融合 (嵌入 + 规则 + 人工确认) |
| 对白人物性格不一致 | 🟡 中 | Few-shot + 角色设定注入 + 一致性校验 |
| Milvus 部署复杂度 | 🟢 低 | 提供 Milvus Lite 嵌入式模式 + MySQL 降级方案 |
| 前端编辑状态丢失 | 🟢 低 | Zustand persist + 自动保存到后端 |

---

## 

## 附录 B：关键技术选型理由

1. **MyBatis-Plus vs JPA**: MySQL 场景下 MyBatis-Plus 的手写 SQL 能力更强，复杂查询（如 JSON 字段查询、联表统计）更直观，且避免了 JPA 的 N+1 陷阱。

2. **Milvus Lite vs PGVector**: Milvus 是专业向量数据库，索引算法（HNSW/IVF）更成熟，支持十亿级向量检索。Lite 模式可嵌入 Java 进程，无需独立部署。

3. **Zustand vs Redux**: 轻量级状态管理，API 简洁，TypeScript 支持好，适合中小规模前端应用。

