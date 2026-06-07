-- ============================================================
-- V4: 修复外键约束 + 新增 plot_insertions 表
-- ============================================================

-- 1. 修复 dialogues.character_id 为 NULLABLE
--    原因：批量创建场景+对话+角色时，角色 ID 可能尚未生成，
--    对话可以先保存，后续再关联角色。
ALTER TABLE dialogues
    MODIFY COLUMN character_id BIGINT NULL COMMENT '关联角色 ID，可为空';

-- 2. 创建 plot_insertions 表
--    用户可在剧本中手动插入情节描述段落，出现在场景之间。
CREATE TABLE IF NOT EXISTS plot_insertions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id       BIGINT NOT NULL COMMENT '关联剧本 ID',
    text            MEDIUMTEXT NOT NULL COMMENT '情节描述内容（Markdown 格式）',
    position        INT NOT NULL DEFAULT 0 COMMENT '插入位置：0=第一个场景之前，N=第N个场景之后',
    inserted_by     VARCHAR(100) NOT NULL DEFAULT 'user' COMMENT '创建者标识',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_plot_insertions_script (script_id),
    CONSTRAINT fk_plot_insertions_script FOREIGN KEY (script_id)
        REFERENCES scripts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户情节插入表';
