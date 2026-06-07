-- ============================================================
-- V6: Fix JSON column defaults to prevent NULL → frontend crash
-- ============================================================

-- 1. Fix characters table JSON columns: set defaults + backfill NULLs
UPDATE characters SET aliases = '[]' WHERE aliases IS NULL;
UPDATE characters SET personality = '[]' WHERE personality IS NULL;
UPDATE characters SET relationships = '[]' WHERE relationships IS NULL;
UPDATE characters SET merged_from = '[]' WHERE merged_from IS NULL;

ALTER TABLE characters
    MODIFY COLUMN aliases JSON NOT NULL DEFAULT ('[]'),
    MODIFY COLUMN personality JSON NOT NULL DEFAULT ('[]'),
    MODIFY COLUMN relationships JSON NOT NULL DEFAULT ('[]'),
    MODIFY COLUMN merged_from JSON NOT NULL DEFAULT ('[]');

-- 2. Fix scenes table JSON columns
UPDATE scenes SET chapter_ids = '[]' WHERE chapter_ids IS NULL;
UPDATE scenes SET character_ids = '[]' WHERE character_ids IS NULL;

ALTER TABLE scenes
    MODIFY COLUMN chapter_ids JSON NOT NULL DEFAULT ('[]'),
    MODIFY COLUMN character_ids JSON NOT NULL DEFAULT ('[]');

-- 3. Fix plot_events table JSON columns
UPDATE plot_events SET chapter_ids = '[]' WHERE chapter_ids IS NULL;
UPDATE plot_events SET character_ids = '[]' WHERE character_ids IS NULL;

ALTER TABLE plot_events
    MODIFY COLUMN chapter_ids JSON NOT NULL DEFAULT ('[]'),
    MODIFY COLUMN character_ids JSON NOT NULL DEFAULT ('[]');

-- 4. Fix novels metadata
UPDATE novels SET metadata = '{}' WHERE metadata IS NULL;
ALTER TABLE novels
    MODIFY COLUMN metadata JSON NOT NULL DEFAULT ('{}');

-- 5. Fix scripts workflow_state
UPDATE scripts SET workflow_state = '{}' WHERE workflow_state IS NULL;
ALTER TABLE scripts
    MODIFY COLUMN workflow_state JSON NOT NULL DEFAULT ('{}');
