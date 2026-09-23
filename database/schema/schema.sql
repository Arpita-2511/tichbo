-- Eventtick database schema — consolidated snapshot
-- Modules: Plan + User, Catalog (Content, Venue, Seat, Show)
--
-- This file is the equivalent of running all migrations in
-- database/migrations/ in order (0001 -> 0007) against an empty database.
-- Use it for a quick local bootstrap; use migrations/ as the versioned
-- source of truth going forward.
--
-- Requires: PostgreSQL 13+ (gen_random_uuid() is built into core as of PG13).

-- ─────────────────────────────────────────────────────────────────────────
-- Shared trigger function: keeps `updated_at` current on every UPDATE.
-- ─────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ─────────────────────────────────────────────────────────────────────────
-- plans
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE plans (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50)   NOT NULL,
    price       NUMERIC(10,2) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_plans_name          UNIQUE (name),
    CONSTRAINT chk_plans_price_nonneg CHECK (price >= 0)
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

-- ─────────────────────────────────────────────────────────────────────────
-- users
-- ─────────────────────────────────────────────────────────────────────────
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

CREATE INDEX idx_users_plan_id ON users (plan_id);

CREATE TRIGGER trg_users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ─────────────────────────────────────────────────────────────────────────
-- Seed data: subscription plans only (no fake users)
-- ─────────────────────────────────────────────────────────────────────────
INSERT INTO plans (id, name, price, description) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Free',    0.00,   'Free tier with standard booking access.'),
    ('22222222-2222-2222-2222-222222222222', 'Pro',     199.00, 'Pro tier with priority booking access and higher rate limits.'),
    ('33333333-3333-3333-3333-333333333333', 'Premium', 499.00, 'Premium tier with the highest rate limits and full feature access.')
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────
-- content
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE content (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    type                  VARCHAR(20)  NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    description           TEXT,
    language              VARCHAR(50),
    duration              INTEGER,
    genre                 VARCHAR(100),
    release_or_event_date DATE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_content_type CHECK (
        type IN ('MOVIE', 'SPORTS_MATCH', 'CONCERT', 'THEATRE', 'EVENT')
    ),
    CONSTRAINT chk_content_duration_positive CHECK (duration IS NULL OR duration > 0)
);

COMMENT ON TABLE content IS
    'Generic bookable entity (movie, sports match, concert, theatre, or '
    'other event). `type` is restricted with a CHECK constraint rather '
    'than a DB enum, so the supported content types can be extended with '
    'a plain migration instead of an ALTER TYPE.';
COMMENT ON COLUMN content.type IS 'One of: MOVIE, SPORTS_MATCH, CONCERT, THEATRE, EVENT.';
COMMENT ON COLUMN content.duration IS
    'Runtime in minutes, where applicable (e.g. a movie). Nullable: not every content type has a fixed duration.';
COMMENT ON COLUMN content.release_or_event_date IS
    'Coarse date associated with the content (e.g. a movie''s release date). '
    'Nullable: not every content type has one at the Content level — precise '
    'per-occurrence scheduling lives on shows.start_time / shows.end_time.';

CREATE TRIGGER trg_content_set_updated_at
    BEFORE UPDATE ON content
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ─────────────────────────────────────────────────────────────────────────
-- venues
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE venues (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    address     VARCHAR(255) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE venues IS
    'Physical location where a Show takes place (e.g. a cinema, stadium, or theatre).';

CREATE TRIGGER trg_venues_set_updated_at
    BEFORE UPDATE ON venues
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ─────────────────────────────────────────────────────────────────────────
-- seats
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE seats (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    venue_id    UUID        NOT NULL,
    section     VARCHAR(50) NOT NULL,
    "row"       VARCHAR(10) NOT NULL,
    seat_number INTEGER     NOT NULL,
    seat_type   VARCHAR(20) NOT NULL,

    CONSTRAINT chk_seats_seat_number_positive CHECK (seat_number > 0),
    CONSTRAINT chk_seats_seat_type CHECK (seat_type IN ('STANDARD', 'PREMIUM', 'VIP')),
    CONSTRAINT uq_seats_physical_seat UNIQUE (venue_id, section, "row", seat_number),
    CONSTRAINT fk_seats_venue
        FOREIGN KEY (venue_id) REFERENCES venues (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE seats IS
    'Permanent physical seats belonging to a venue''s seating layout. '
    'Deliberately has no availability/booked/held state — that is '
    'per-Show data owned by the future Booking domain, not a property of '
    'the physical seat itself.';
COMMENT ON COLUMN seats."row" IS
    'Row label within the section (e.g. "A", "12"). Quoted in DDL/queries because ROW is a reserved SQL keyword.';
COMMENT ON COLUMN seats.seat_type IS
    'Physical seat category: STANDARD, PREMIUM, or VIP. This is unrelated '
    'to the Eventtick user''s subscription plan (plans.name) — do not '
    'conflate the two.';

CREATE INDEX idx_seats_venue_id ON seats (venue_id);

-- ─────────────────────────────────────────────────────────────────────────
-- shows
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE shows (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id  UUID        NOT NULL,
    venue_id    UUID        NOT NULL,
    start_time  TIMESTAMPTZ NOT NULL,
    end_time    TIMESTAMPTZ NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_shows_status CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT chk_shows_end_after_start CHECK (end_time > start_time),
    CONSTRAINT fk_shows_content
        FOREIGN KEY (content_id) REFERENCES content (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_shows_venue
        FOREIGN KEY (venue_id) REFERENCES venues (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE shows IS
    'A specific scheduled occurrence of Content at a Venue. Booking and '
    'seat-availability logic is intentionally NOT modeled here — that '
    'belongs to the future Booking domain, which will reference shows.id.';
COMMENT ON COLUMN shows.status IS
    'One of: SCHEDULED, CANCELLED, COMPLETED. Defaults to SCHEDULED for a newly created show.';

CREATE INDEX idx_shows_content_id ON shows (content_id);
CREATE INDEX idx_shows_venue_id   ON shows (venue_id);
CREATE INDEX idx_shows_start_time ON shows (start_time);

CREATE TRIGGER trg_shows_set_updated_at
    BEFORE UPDATE ON shows
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
