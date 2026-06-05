/*
 Navicat Premium Dump SQL

 Source Server         : MySQL
 Source Server Type    : MySQL
 Source Server Version : 90200 (9.2.0)
 Source Host           : localhost:3306
 Source Schema         : novel2script

 Target Server Type    : MySQL
 Target Server Version : 90200 (9.2.0)
 File Encoding         : 65001

 Date: 05/06/2026 13:17:51
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for actions
-- ----------------------------
DROP TABLE IF EXISTS `actions`;
CREATE TABLE `actions`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scene_id` bigint NOT NULL,
  `character_id` bigint NULL DEFAULT NULL COMMENT '执行动作的角色，NULL 表示环境描述',
  `sequence` int NOT NULL COMMENT '动作序号',
  `action_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTION' COMMENT '类型: ACTION/REACTION/BEAT/BUSINESS',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作描述',
  `duration_ms` int NULL DEFAULT NULL COMMENT '预估持续时间(毫秒)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_actions_scene`(`scene_id` ASC) USING BTREE,
  INDEX `idx_actions_character`(`character_id` ASC) USING BTREE,
  CONSTRAINT `fk_actions_character` FOREIGN KEY (`character_id`) REFERENCES `characters` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_actions_scene` FOREIGN KEY (`scene_id`) REFERENCES `scenes` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '动作表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of actions
-- ----------------------------

-- ----------------------------
-- Table structure for chapters
-- ----------------------------
DROP TABLE IF EXISTS `chapters`;
CREATE TABLE `chapters`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `novel_id` bigint NOT NULL,
  `chapter_number` int NOT NULL COMMENT '章节序号',
  `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '章节标题',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '章节内容',
  `char_count` int NOT NULL DEFAULT 0 COMMENT '字数',
  `start_offset` bigint NOT NULL DEFAULT 0 COMMENT '在原文中的起始偏移',
  `end_offset` bigint NOT NULL DEFAULT 0 COMMENT '在原文中的结束偏移',
  `embedding_id` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'Milvus 中的嵌入 ID',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RAW' COMMENT '状态: RAW/PARSED/EMBEDDED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_chapters_novel`(`novel_id` ASC) USING BTREE,
  INDEX `idx_chapters_embedding`(`embedding_id` ASC) USING BTREE,
  CONSTRAINT `fk_chapters_novel` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '章节表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of chapters
-- ----------------------------

-- ----------------------------
-- Table structure for characters
-- ----------------------------
DROP TABLE IF EXISTS `characters`;
CREATE TABLE `characters`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `script_id` bigint NOT NULL,
  `canonical_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标准名称',
  `aliases` json NULL COMMENT '别名列表 [\"川哥\",\"小川\",\"林师兄\"]',
  `role_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SUPPORTING' COMMENT '角色类型: PROTAGONIST/ANTAGONIST/SUPPORTING/MINOR',
  `gender` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '性别: MALE/FEMALE/UNKNOWN',
  `age_range` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '年龄段描述',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '角色描述',
  `personality` json NULL COMMENT '性格特征 [\"冷静\",\"果断\",\"腹黑\"]',
  `relationships` json NULL COMMENT '角色关系 [{\"target\":\"李雪\",\"relation\":\"恋人\"}]',
  `appearance_count` int NOT NULL DEFAULT 0 COMMENT '出场次数',
  `first_appearance` bigint NULL DEFAULT NULL COMMENT '首次出场章节 ID',
  `embedding_id` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'Milvus 中的嵌入 ID (用于去重)',
  `is_resolved` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已去重合并',
  `merged_from` json NULL COMMENT '合并来源角色 ID 列表',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_characters_script`(`script_id` ASC) USING BTREE,
  INDEX `idx_characters_embedding`(`embedding_id` ASC) USING BTREE,
  CONSTRAINT `fk_characters_script` FOREIGN KEY (`script_id`) REFERENCES `scripts` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of characters
-- ----------------------------

-- ----------------------------
-- Table structure for dialogues
-- ----------------------------
DROP TABLE IF EXISTS `dialogues`;
CREATE TABLE `dialogues`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scene_id` bigint NOT NULL,
  `character_id` bigint NOT NULL,
  `sequence` int NOT NULL COMMENT '对白序号',
  `speaker` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '说话人名称',
  `emotion` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '情绪: ANGRY/HAPPY/SAD/CALM/FEARFUL/SURPRISED/...',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对白内容',
  `parenthetical` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '括号说明: (低声)/(冷笑)',
  `reply_to` bigint NULL DEFAULT NULL COMMENT '回复的对白 ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_dialogues_scene`(`scene_id` ASC) USING BTREE,
  INDEX `idx_dialogues_character`(`character_id` ASC) USING BTREE,
  CONSTRAINT `fk_dialogues_character` FOREIGN KEY (`character_id`) REFERENCES `characters` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_dialogues_scene` FOREIGN KEY (`scene_id`) REFERENCES `scenes` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '对白表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of dialogues
-- ----------------------------

-- ----------------------------
-- Table structure for novels
-- ----------------------------
DROP TABLE IF EXISTS `novels`;
CREATE TABLE `novels`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '小说标题',
  `author` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '作者',
  `file_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上传文件名',
  `file_size` bigint NOT NULL COMMENT '文件大小(字节)',
  `total_chars` int NOT NULL DEFAULT 0 COMMENT '总字数',
  `chapter_count` int NOT NULL DEFAULT 0 COMMENT '章节数',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UPLOADED' COMMENT '状态: UPLOADED/PARSING/PARSED/PROCESSING/COMPLETED/FAILED',
  `metadata` json NULL COMMENT '元数据(JSON)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novels_status`(`status` ASC) USING BTREE,
  INDEX `idx_novels_created_at`(`created_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '小说表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of novels
-- ----------------------------

-- ----------------------------
-- Table structure for plot_events
-- ----------------------------
DROP TABLE IF EXISTS `plot_events`;
CREATE TABLE `plot_events`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `script_id` bigint NOT NULL,
  `event_order` int NOT NULL COMMENT '事件序号',
  `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件标题',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '事件描述',
  `location` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '发生地点',
  `time_point` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '时间点描述',
  `conflict_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '冲突类型: PERSON_VS_PERSON/PERSON_VS_SELF/...',
  `chapter_ids` json NULL COMMENT '关联章节 ID [1,2,3]',
  `character_ids` json NULL COMMENT '参与角色 ID [1,5,8]',
  `importance` int NOT NULL DEFAULT 3 COMMENT '重要程度 1-5',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_plot_script`(`script_id` ASC) USING BTREE,
  CONSTRAINT `fk_plot_script` FOREIGN KEY (`script_id`) REFERENCES `scripts` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '剧情事件表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of plot_events
-- ----------------------------

-- ----------------------------
-- Table structure for prompt_audits
-- ----------------------------
DROP TABLE IF EXISTS `prompt_audits`;
CREATE TABLE `prompt_audits`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `prompt_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Prompt 模板名称',
  `prompt_version` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号',
  `model_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '使用的模型',
  `input_tokens` int NOT NULL DEFAULT 0 COMMENT '输入 Token 数',
  `output_tokens` int NOT NULL DEFAULT 0 COMMENT '输出 Token 数',
  `latency_ms` int NOT NULL DEFAULT 0 COMMENT '响应延迟(毫秒)',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `is_success` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否成功',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '错误信息',
  `full_prompt` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '完整 Prompt (用于调试)',
  `full_response` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '完整响应 (用于调试)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_audit_name`(`prompt_name` ASC) USING BTREE,
  INDEX `idx_audit_created`(`created_at` ASC) USING BTREE,
  INDEX `idx_audit_success`(`is_success` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Prompt 审计表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of prompt_audits
-- ----------------------------

-- ----------------------------
-- Table structure for scene_characters
-- ----------------------------
DROP TABLE IF EXISTS `scene_characters`;
CREATE TABLE `scene_characters`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scene_id` bigint NOT NULL,
  `character_id` bigint NOT NULL,
  `role_in_scene` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '此场景中的角色描述',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_scene_character`(`scene_id` ASC, `character_id` ASC) USING BTREE,
  INDEX `idx_sc_char_scene`(`scene_id` ASC) USING BTREE,
  INDEX `idx_sc_char_character`(`character_id` ASC) USING BTREE,
  CONSTRAINT `fk_sc_character` FOREIGN KEY (`character_id`) REFERENCES `characters` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_sc_scene` FOREIGN KEY (`scene_id`) REFERENCES `scenes` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '场景角色关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of scene_characters
-- ----------------------------

-- ----------------------------
-- Table structure for scenes
-- ----------------------------
DROP TABLE IF EXISTS `scenes`;
CREATE TABLE `scenes`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `script_id` bigint NOT NULL,
  `scene_number` int NOT NULL COMMENT '场景序号',
  `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '场景标题',
  `location` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '地点',
  `time_of_day` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '时间: MORNING/AFTERNOON/EVENING/NIGHT/DAWN/DUSK',
  `is_interior` tinyint(1) NOT NULL DEFAULT 1 COMMENT '室内/室外: 1=INT, 0=EXT',
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '场景摘要',
  `mood` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '氛围',
  `chapter_ids` json NULL COMMENT '来源章节 ID',
  `source_reason` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '切分原因: LOCATION/TIME/CHARACTER/CONFLICT',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_scenes_script`(`script_id` ASC) USING BTREE,
  CONSTRAINT `fk_scenes_script` FOREIGN KEY (`script_id`) REFERENCES `scripts` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '场景表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of scenes
-- ----------------------------

-- ----------------------------
-- Table structure for scripts
-- ----------------------------
DROP TABLE IF EXISTS `scripts`;
CREATE TABLE `scripts`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `novel_id` bigint NOT NULL,
  `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '剧本标题',
  `version` int NOT NULL DEFAULT 1 COMMENT '版本号',
  `scene_count` int NOT NULL DEFAULT 0 COMMENT '场景数',
  `character_count` int NOT NULL DEFAULT 0 COMMENT '角色数',
  `dialogue_count` int NOT NULL DEFAULT 0 COMMENT '对白数',
  `yaml_content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '导出的 YAML 内容',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/GENERATING/COMPLETED/FAILED',
  `progress` decimal(5, 2) NOT NULL DEFAULT 0.00 COMMENT '生成进度 0.00-100.00',
  `workflow_state` json NULL COMMENT '工作流状态快照',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `novel_id`(`novel_id` ASC) USING BTREE,
  INDEX `idx_scripts_novel`(`novel_id` ASC) USING BTREE,
  INDEX `idx_scripts_status`(`status` ASC) USING BTREE,
  CONSTRAINT `fk_scripts_novel` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '剧本表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of scripts
-- ----------------------------

-- ----------------------------
-- Table structure for shots
-- ----------------------------
DROP TABLE IF EXISTS `shots`;
CREATE TABLE `shots`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scene_id` bigint NOT NULL,
  `shot_number` int NOT NULL COMMENT '分镜序号',
  `camera` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '机位: WIDE/MEDIUM/CLOSE_UP/EXTREME_CU/POV/...',
  `angle` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '角度: EYE_LEVEL/HIGH_ANGLE/LOW_ANGLE/DUTCH/...',
  `duration_sec` decimal(5, 1) NULL DEFAULT NULL COMMENT '持续时间(秒)',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '画面描述',
  `movement` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '运镜: STATIC/PAN/TILT/DOLLY/ZOOM/...',
  `transition` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '转场: CUT/DISSOLVE/FADE/WIPE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_shots_scene`(`scene_id` ASC) USING BTREE,
  CONSTRAINT `fk_shots_scene` FOREIGN KEY (`scene_id`) REFERENCES `scenes` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '分镜表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of shots
-- ----------------------------

-- ----------------------------
-- Table structure for workflow_executions
-- ----------------------------
DROP TABLE IF EXISTS `workflow_executions`;
CREATE TABLE `workflow_executions`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `script_id` bigint NOT NULL,
  `workflow_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作流类型: FULL_GENERATION/PARTIAL',
  `current_step` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '当前步骤名称',
  `state` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/COMPLETED/FAILED/PAUSED',
  `state_snapshot` json NULL COMMENT '状态快照 (可序列化恢复)',
  `error_detail` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '错误详情',
  `started_at` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `completed_at` datetime NULL DEFAULT NULL COMMENT '完成时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_workflow_script`(`script_id` ASC) USING BTREE,
  INDEX `idx_workflow_state`(`state` ASC) USING BTREE,
  INDEX `idx_workflow_type`(`workflow_type` ASC) USING BTREE,
  CONSTRAINT `fk_workflow_script` FOREIGN KEY (`script_id`) REFERENCES `scripts` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流执行记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of workflow_executions
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;
