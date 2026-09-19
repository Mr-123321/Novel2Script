-- ============================================================
-- V7: W04 — support ScriptStatus.COMPLETED_WITH_WARNINGS (部分成功)
-- ============================================================
-- 背景: W01/W02 拆除编造层后，对白/动作生成失败的场景会被留空并标记"待补全"。
-- 此时剧本既不能谎报 COMPLETED，也不应整体 FAILED 丢掉全部成果，因此新增终态
-- COMPLETED_WITH_WARNINGS。

-- 1. 加宽 status 列。
--    'COMPLETED_WITH_WARNINGS' 共 23 个字符，超过原 VARCHAR(20)：
--    不加宽时 MySQL 严格模式会直接拒绝写入
--    ("Data too long for column 'status'")，导致生成结果无法落库。
ALTER TABLE scripts
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
        COMMENT '状态: DRAFT/GENERATING/COMPLETED/COMPLETED_WITH_WARNINGS/FAILED';

-- 2. 承接过渡期旧值。
--    枚举名由临时的 PARTIAL 统一为 COMPLETED_WITH_WARNINGS，
--    已写入 PARTIAL 的历史行需要迁移，否则无法映射回 Java 枚举。
UPDATE scripts
   SET status = 'COMPLETED_WITH_WARNINGS'
 WHERE status = 'PARTIAL';
