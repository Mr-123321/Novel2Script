-- ============================================================
-- V5: Schema corrections for MyBatis-Plus integration
-- ============================================================

-- 1. Drop UNIQUE constraint on scripts.novel_id
--    A novel can generate multiple scripts (different configs/re-generations)
DROP INDEX novel_id ON scripts;

-- 2. Add emotional_arc column to plot_events (present in Java model but missing in DB)
ALTER TABLE plot_events
    ADD COLUMN emotional_arc VARCHAR(50) COMMENT '情感弧线: ↑(上升)/↓(下降)/→(平)/↗(缓升)/↘(缓降)';

-- 3. Add scene_heading column to scenes (present in Java model)
ALTER TABLE scenes
    ADD COLUMN scene_heading VARCHAR(500) COMMENT '生成的标准场景标题: INT. 地点 - TIME';

-- 4. Add character_ids column to scenes (JSON array of character IDs)
ALTER TABLE scenes
    ADD COLUMN character_ids JSON COMMENT '场景中的角色 ID 列表';

-- 5. Fix foreign key for actions.character_id: use ON DELETE SET NULL
ALTER TABLE actions
    DROP FOREIGN KEY fk_actions_character;
ALTER TABLE actions
    ADD CONSTRAINT fk_actions_character
        FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE SET NULL;

-- 6. Fix foreign key for dialogues.character_id: use ON DELETE SET NULL
ALTER TABLE dialogues
    DROP FOREIGN KEY fk_dialogues_character;
ALTER TABLE dialogues
    ADD CONSTRAINT fk_dialogues_character
        FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE SET NULL;
