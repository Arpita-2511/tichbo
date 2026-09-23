-- Rollback: 0001_create_plans_table
-- Note: run this AFTER 0002's down migration (users depends on plans and
-- reuses set_updated_at()); apply down migrations in reverse order.

DROP TRIGGER IF EXISTS trg_plans_set_updated_at ON plans;
DROP TABLE IF EXISTS plans;
DROP FUNCTION IF EXISTS set_updated_at();
