-- Migration: 0001_create_plans_table
-- Description: Creates the `plans` table (subscription plans, e.g. Free, Pro, Premium)
--              and a reusable trigger function that keeps `updated_at` current.
-- Requires: PostgreSQL 13+ (gen_random_uuid() is built into core as of PG13).

-- Reusable trigger function. Defined here (first migration) so later tables
-- can attach the same trigger instead of redefining it.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE plans (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50)   NOT NULL,
    price       NUMERIC(10,2) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_plans_name             UNIQUE (name),
    CONSTRAINT chk_plans_price_nonneg    CHECK (price >= 0)
);

COMMENT ON TABLE plans IS
    'Subscription plans available to users (e.g. Free, Pro, Premium). '
    'Plan names are plain data, not a DB enum, so new tiers can be added '
    'with an INSERT instead of a migration.';
COMMENT ON COLUMN plans.price IS
    'Recurring price in the platform''s base currency; 0 for free tiers.';

CREATE TRIGGER trg_plans_set_updated_at
    BEFORE UPDATE ON plans
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
