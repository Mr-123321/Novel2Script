-- ============================================================
-- V8: W05 — 内容来源溯源 (provenance) 与场景级失败状态
-- ============================================================
-- 背景: 对白/动作有三条产生路径 —— AI 生成、正则从原文抽取、人工补全。
-- 此前三者落库后完全无法区分，导致：
--   1) 论文实验无法剔除正则兜底样本，准确率统计被稀释；
--   2) 前端无法标注"这条待人工确认"。
-- 本迁移为每条内容记录来源，并为场景记录对白/动作两个维度的生成状态。
--
-- 注意: 编号为 V8 而非 V7 —— V7 已被 V7__widen_script_status.sql 占用。

-- ----------------------------
-- 1. 对白来源
-- ----------------------------
-- 历史行无法回溯其真实来源（此前未记录），统一按 DEFAULT 'AI' 处理；
-- 若后续实验需要严格样本，请以本次迁移之后新生成的数据为准。
ALTER TABLE dialogues
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'AI'
        COMMENT '内容来源: AI(AI生成) / REGEX(正则从原文抽取) / MANUAL(人工补全)';

-- 实验按来源筛样本时使用
ALTER TABLE dialogues
    ADD INDEX idx_dialogues_source (source);

-- ----------------------------
-- 2. 动作来源
-- ----------------------------
ALTER TABLE actions
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'AI'
        COMMENT '内容来源: AI(AI生成) / REGEX(正则从原文抽取) / MANUAL(人工补全)';

ALTER TABLE actions
    ADD INDEX idx_actions_source (source);

-- ----------------------------
-- 3. 场景级生成状态（对白 / 动作两个维度）
-- ----------------------------
-- 默认 COMPLETED；生成失败时由 GenerationOrchestrator 显式置为 FAILED，
-- 与 workflowState 中的 failedDialogueScenes / failedActionScenes 计数对应。
-- 历史场景不做回填推断（无法区分"本就无对白的场景"与"生成失败的场景"，
-- 强行回填会把纯动作场景误判为 FAILED），统一保留默认值。
ALTER TABLE scenes
    ADD COLUMN dialogue_status VARCHAR(24) DEFAULT 'COMPLETED'
        COMMENT '对白生成状态: COMPLETED / FAILED(生成失败, 待人工补全)';

ALTER TABLE scenes
    ADD COLUMN action_status VARCHAR(24) DEFAULT 'COMPLETED'
        COMMENT '动作生成状态: COMPLETED / FAILED(生成失败, 待人工补全)';
