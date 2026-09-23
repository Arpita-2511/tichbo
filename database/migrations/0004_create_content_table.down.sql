-- Rollback: 0004_create_content_table
-- Apply this AFTER 0007's down (shows.content_id references content).

DROP TRIGGER IF EXISTS trg_content_set_updated_at ON content;
DROP TABLE IF EXISTS content;
