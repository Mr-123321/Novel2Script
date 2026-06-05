-- ============================================================
-- V2: 审计表 (2 张表)
-- ============================================================

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
-- 12. 工作流执行记录表 (FK -> scripts)
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
