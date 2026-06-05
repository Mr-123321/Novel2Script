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
CREATE TABLE IF NOT EXISTS novels (
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
CREATE TABLE IF NOT EXISTS chapters (
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
CREATE TABLE IF NOT EXISTS scripts (
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
CREATE TABLE IF NOT EXISTS characters (
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
CREATE TABLE IF NOT EXISTS plot_events (
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
CREATE TABLE IF NOT EXISTS scenes (
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
CREATE TABLE IF NOT EXISTS scene_characters (
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
CREATE TABLE IF NOT EXISTS dialogues (
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
CREATE TABLE IF NOT EXISTS actions (
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
CREATE TABLE IF NOT EXISTS shots (
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
CREATE TABLE IF NOT EXISTS prompt_audits (
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
CREATE TABLE IF NOT EXISTS workflow_executions (
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
