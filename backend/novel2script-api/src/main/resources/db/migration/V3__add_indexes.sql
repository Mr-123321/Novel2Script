-- ============================================================
-- V3: 性能索引 — 为高频查询添加复合索引
-- ============================================================

-- novels: 按状态过滤并按创建时间排序
CREATE INDEX IF NOT EXISTS idx_novels_status_created
    ON novels(status, created_at);

-- chapters: 按小说查询章节并按序号排序
CREATE INDEX IF NOT EXISTS idx_chapters_novel_chapter
    ON chapters(novel_id, chapter_number);

-- scripts: 按小说查询剧本
CREATE INDEX IF NOT EXISTS idx_scripts_novel_id
    ON scripts(novel_id);

-- scripts: 按状态过滤剧本
CREATE INDEX IF NOT EXISTS idx_scripts_status_val
    ON scripts(status);

-- scenes: 按剧本查询场景并按序号排序
CREATE INDEX IF NOT EXISTS idx_scenes_script_scene
    ON scenes(script_id, scene_number);

-- dialogues: 按场景查询对白并按序号排序
CREATE INDEX IF NOT EXISTS idx_dialogues_scene_seq
    ON dialogues(scene_id, sequence);

-- prompt_audits: 按 Prompt 名称过滤并按时间排序
CREATE INDEX IF NOT EXISTS idx_audit_name_created
    ON prompt_audits(prompt_name, created_at);
