-- Migration: 0002_create_users_table
-- Description: Creates the `users` table. Each user belongs to exactly one plan.
-- Depends on: 0001_create_plans_table (plans.id, set_updated_at()).

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(150) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash TEXT         NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',
    -- No DEFAULT: the User Service must explicitly assign a plan when
    -- creating an account.
    plan_id       UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER', 'ADMIN')),
    CONSTRAINT fk_users_plan
        FOREIGN KEY (plan_id) REFERENCES plans (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE users IS 'Platform users (customers and administrators).';
COMMENT ON COLUMN users.email IS
    'Unique account email. The application must normalize (lowercase) an email before insert/update; the database does not enforce casing.';
COMMENT ON COLUMN users.password_hash IS
    'Salted password hash (e.g. bcrypt/argon2id) computed by the application. Never store plaintext passwords here.';
COMMENT ON COLUMN users.role IS 'Authorization role: CUSTOMER or ADMIN.';
COMMENT ON COLUMN users.plan_id IS
    'FK to plans.id. Every user belongs to exactly one subscription plan, assigned explicitly by the application (User Service) at account creation — no database default.';

-- Postgres does not auto-index FK columns; add one to keep joins/filters on
-- plan_id fast (e.g. "list all users on the Premium plan").
CREATE INDEX idx_users_plan_id ON users (plan_id);

CREATE TRIGGER trg_users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
