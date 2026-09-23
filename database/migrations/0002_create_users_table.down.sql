-- Rollback: 0002_create_users_table
-- Apply this BEFORE 0001's down migration (users depends on plans).

DROP TRIGGER IF EXISTS trg_users_set_updated_at ON users;
DROP INDEX IF EXISTS idx_users_plan_id;
DROP TABLE IF EXISTS users;
