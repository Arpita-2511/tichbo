# Eventtick Database

Two modules implemented so far:

- **Module 1 — Plan + User:** `plans`, `users`.
- **Module 2 — Catalog:** `content`, `venues`, `seats`, `shows`.

Not yet implemented (separate future stages): bookings, booking seats,
payments, rate-limit policies, authentication tables, Redis, Spring Boot,
the API Gateway.

---

## 1. Requirements

- PostgreSQL **13+** (needed for `gen_random_uuid()`, which is built into core
  as of PG13 — no `pgcrypto`/`uuid-ossp` extension required). Developed and
  verified against PostgreSQL 18.6.

---

## 2. Directory layout

```text
database/
  README.md
  schema/
    schema.sql                          ← full consolidated DDL (one-shot bootstrap)
  migrations/
    0001_create_plans_table.up.sql
    0001_create_plans_table.down.sql
    0002_create_users_table.up.sql
    0002_create_users_table.down.sql
    0003_seed_plans.up.sql
    0003_seed_plans.down.sql
    0004_create_content_table.up.sql
    0004_create_content_table.down.sql
    0005_create_venues_table.up.sql
    0005_create_venues_table.down.sql
    0006_create_seats_table.up.sql
    0006_create_seats_table.down.sql
    0007_create_shows_table.up.sql
    0007_create_shows_table.down.sql
```

- **`migrations/`** is the versioned source of truth. Files follow the
  `NNNN_description.up.sql` / `.down.sql` convention used by tools like
  `golang-migrate`, so this folder can be pointed at such a tool later
  without renaming anything.
- **`schema/schema.sql`** is the same DDL flattened into a single file, for
  quickly standing up a fresh local database in one command.

Future modules should continue the migration sequence from `0008_...`
onward, and update `schema/schema.sql` to match.

---

## 3. How to apply

**Option A — one-shot bootstrap (fresh database only):**

```powershell
createdb eventtick_db
psql -U postgres -d eventtick_db -f database/schema/schema.sql
```

**Option B — apply migrations in order:**

```powershell
psql -U postgres -d eventtick_db -f database/migrations/0001_create_plans_table.up.sql
psql -U postgres -d eventtick_db -f database/migrations/0002_create_users_table.up.sql
psql -U postgres -d eventtick_db -f database/migrations/0003_seed_plans.up.sql
psql -U postgres -d eventtick_db -f database/migrations/0004_create_content_table.up.sql
psql -U postgres -d eventtick_db -f database/migrations/0005_create_venues_table.up.sql
psql -U postgres -d eventtick_db -f database/migrations/0006_create_seats_table.up.sql
psql -U postgres -d eventtick_db -f database/migrations/0007_create_shows_table.up.sql
```

**Rolling back:** apply the `.down.sql` files in **strict reverse** order
(`0007 → 0006 → 0005 → 0004 → 0003 → 0002 → 0001`). Several `.down.sql`
files carry a comment noting exactly which later migration they depend on
being rolled back first (e.g. `plans` can't be dropped while `users` still
references it; `content`/`venues` can't be dropped while `shows`/`seats`
still reference them).

---

## 4. Module 1 tables: `plans`, `users`

### 4.1 `plans`

| Column        | Type            | Constraints                                   |
|---------------|-----------------|------------------------------------------------|
| `id`          | `UUID`          | PK, default `gen_random_uuid()`                |
| `name`        | `VARCHAR(50)`   | `NOT NULL`, `UNIQUE`                            |
| `price`       | `NUMERIC(10,2)` | `NOT NULL`, `CHECK (price >= 0)`                |
| `description` | `TEXT`          | nullable                                        |
| `created_at`  | `TIMESTAMPTZ`   | `NOT NULL`, default `now()`                     |
| `updated_at`  | `TIMESTAMPTZ`   | `NOT NULL`, default `now()`, auto-updated       |

`name` is plain, unique text — **not** a Postgres `ENUM` — so a new tier can
be added with an `INSERT` instead of a schema migration.

### 4.2 `users`

| Column          | Type            | Constraints                                                        |
|-----------------|-----------------|------------------------------------------------------------------------|
| `id`            | `UUID`          | PK, default `gen_random_uuid()`                                     |
| `name`          | `VARCHAR(150)`  | `NOT NULL`                                                            |
| `email`         | `VARCHAR(255)`  | `NOT NULL`, `UNIQUE`                                                  |
| `password_hash` | `TEXT`          | `NOT NULL` — a salted hash (bcrypt/argon2id), never plaintext         |
| `role`          | `VARCHAR(20)`   | `NOT NULL`, default `'CUSTOMER'`, `CHECK (role IN ('CUSTOMER','ADMIN'))` |
| `plan_id`       | `UUID`          | `NOT NULL`, FK → `plans.id`, no default — assigned explicitly by the application |
| `created_at`    | `TIMESTAMPTZ`   | `NOT NULL`, default `now()`                                            |
| `updated_at`    | `TIMESTAMPTZ`   | `NOT NULL`, default `now()`, auto-updated                             |

An index (`idx_users_plan_id`) is added on `plan_id`, since Postgres does not
automatically index foreign-key columns and this column is expected to be
joined/filtered on often (e.g. "all users on the Premium plan").

---

## 5. Module 1 relationship

**One Plan → many Users. Each User belongs to exactly one Plan.**

```text
plans (1) ──────────< (many) users
  id                    plan_id  (FK → plans.id)
```

- `users.plan_id` is `NOT NULL`: every user must have a plan. It has **no
  database default** — the User Service must explicitly assign a plan when
  creating an account.
- `FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE RESTRICT`: a plan
  cannot be deleted while users still reference it. This protects
  referential integrity — reassign or migrate users off a plan first.
- `ON UPDATE CASCADE`: if a plan's `id` ever changed, dependent rows would
  follow automatically (in practice `id` is never updated, this is just a
  safety net).

---

## 6. Roles

```text
CUSTOMER
ADMIN
```

Enforced with a `CHECK` constraint rather than a DB `ENUM` type, since the
requirements docs note additional roles may be introduced later — a `CHECK`
is simpler to alter than an `ENUM` type if that happens.

---

## 7. Plans (seed data)

`plans.name` is plain unique text rather than a hardcoded enum, so the
schema doesn't commit to any particular tier list — renaming/adding a tier
is a plain `INSERT`/`UPDATE`, not a migration.

The seed data in `0003_seed_plans.up.sql` / `schema.sql` is:

| Name    | Price  | Fixed UUID                            |
|---------|--------|----------------------------------------|
| Free    | 0.00   | `11111111-1111-1111-1111-111111111111` |
| Pro     | 199.00 | `22222222-2222-2222-2222-222222222222` |
| Premium | 499.00 | `33333333-3333-3333-3333-333333333333` |

These names, prices, and IDs are configured deliberately and should not be
changed without updating `0003_seed_plans.up.sql`, its `.down.sql`, and
`schema/schema.sql` together, then updating this table to match.

These UUIDs are intentionally fixed and well-known (not randomly
generated), so application code can reference a specific plan by constant
instead of a lookup query.

No user rows are seeded — only plans. `users.plan_id` has no database
default; the User Service must assign a plan explicitly when it creates an
account (see §5).

---

## 8. Module 1 security notes

- Passwords are **never** stored. Only `password_hash` exists, and it holds
  a salted hash (bcrypt or argon2id recommended) computed by the application
  — hashing is not the database's job.
- `email` is `UNIQUE`, but the database does **not** enforce lowercase
  storage. The application/backend is responsible for normalizing an email
  to lowercase before insert/update; if it doesn't, `A@x.com` and
  `a@x.com` could both be stored as distinct "unique" rows.

---

## 9. Module 2 tables: `content`, `venues`, `seats`, `shows`

### 9.1 `content`

The generic entity representing anything bookable — a movie, sports match,
concert, theatre production, or other event. Category-specific booking
structures are avoided; all content types share this one table.

| Column                   | Type            | Constraints                                                          |
|--------------------------|-----------------|-------------------------------------------------------------------------|
| `id`                     | `UUID`          | PK, default `gen_random_uuid()`                                       |
| `type`                   | `VARCHAR(20)`   | `NOT NULL`, `CHECK` restricted to the 5 types below                    |
| `title`                  | `VARCHAR(255)`  | `NOT NULL`                                                              |
| `description`            | `TEXT`          | nullable                                                                |
| `language`               | `VARCHAR(50)`   | nullable                                                                |
| `duration`               | `INTEGER`       | nullable, `CHECK (duration IS NULL OR duration > 0)` — minutes          |
| `genre`                  | `VARCHAR(100)`  | nullable                                                                |
| `release_or_event_date`  | `DATE`          | nullable                                                                |
| `created_at`             | `TIMESTAMPTZ`   | `NOT NULL`, default `now()`                                            |
| `updated_at`             | `TIMESTAMPTZ`   | `NOT NULL`, default `now()`, auto-updated                             |

`type` is restricted to exactly:

```text
MOVIE
SPORTS_MATCH
CONCERT
THEATRE
EVENT
```

Enforced with a `CHECK` constraint (`chk_content_type`), not a Postgres
`ENUM`, matching the same rationale as `plans.name`/`users.role`: extending
the domain later is a plain migration (`ALTER TABLE ... DROP CONSTRAINT ...
ADD CONSTRAINT ...`) instead of an `ALTER TYPE`.

`duration` and `release_or_event_date` are nullable because not every
content type needs them at the Content level — e.g. an ongoing theatre
production may not have one canonical "event date" the way a movie has a
release date. Precise per-occurrence scheduling (an actual date/time a
customer can book) always lives on `shows.start_time`/`shows.end_time`,
never here.

### 9.2 `venues`

The physical location where a Show takes place.

| Column       | Type            | Constraints                                |
|--------------|-----------------|-----------------------------------------------|
| `id`         | `UUID`          | PK, default `gen_random_uuid()`             |
| `name`       | `VARCHAR(150)`  | `NOT NULL`                                    |
| `address`    | `VARCHAR(255)`  | `NOT NULL`                                    |
| `city`       | `VARCHAR(100)`  | `NOT NULL`                                    |
| `created_at` | `TIMESTAMPTZ`   | `NOT NULL`, default `now()`                 |
| `updated_at` | `TIMESTAMPTZ`   | `NOT NULL`, default `now()`, auto-updated   |

`updated_at` is kept current by `trg_venues_set_updated_at`, reusing the
same `set_updated_at()` trigger function defined in `0001` — no new
function is defined for this module.

### 9.3 `seats`

The permanent physical seat layout of a venue (e.g. Section A, Row 3, Seat 12).

| Column        | Type            | Constraints                                                              |
|---------------|-----------------|------------------------------------------------------------------------------|
| `id`          | `UUID`          | PK, default `gen_random_uuid()`                                           |
| `venue_id`    | `UUID`          | `NOT NULL`, FK → `venues.id`                                              |
| `section`     | `VARCHAR(50)`   | `NOT NULL`                                                                  |
| `"row"`       | `VARCHAR(10)`   | `NOT NULL` — quoted because `ROW` is a reserved SQL keyword                |
| `seat_number` | `INTEGER`       | `NOT NULL`, `CHECK (seat_number > 0)`                                      |
| `seat_type`   | `VARCHAR(20)`   | `NOT NULL`, `CHECK` restricted to `STANDARD` / `PREMIUM` / `VIP`           |

Note: unlike the other tables in this module, `seats` intentionally has
**no `created_at`/`updated_at`** columns — the requested column set for
this table didn't include them. Physical seat rows are largely static
reference data. If audit timestamps are wanted for seats later, that's a
straightforward additive migration.

A physical seat is uniquely identified within its venue by:

```text
uq_seats_physical_seat UNIQUE (venue_id, section, "row", seat_number)
```

`idx_seats_venue_id` indexes `venue_id` for fast per-venue seat-layout
lookups (Postgres doesn't auto-index FK columns).

**Why `seats` has no availability/booked/held state:** a seat's physical
existence (which venue, which section/row/number, what category) is
permanent reference data, independent of any particular show. Whether that
seat is `AVAILABLE`, `HELD`, or `BOOKED` is meaningful only *in the context
of one specific show* — the same physical seat is simultaneously
"available" for next Tuesday's show and "booked" for tonight's. That
per-show state belongs to the future Booking domain (which will associate
a seat with a specific `show_id`), not to this table.

`seat_type` (`STANDARD`/`PREMIUM`/`VIP`) is a physical seating category —
unrelated to `plans.name` (the user's subscription tier), which also
happens to use `VIP`. Don't conflate the two.

### 9.4 `shows`

A specific scheduled occurrence of Content at a Venue.

| Column       | Type            | Constraints                                                          |
|--------------|-----------------|--------------------------------------------------------------------------|
| `id`         | `UUID`          | PK, default `gen_random_uuid()`                                       |
| `content_id` | `UUID`          | `NOT NULL`, FK → `content.id`                                          |
| `venue_id`   | `UUID`          | `NOT NULL`, FK → `venues.id`                                           |
| `start_time` | `TIMESTAMPTZ`   | `NOT NULL`                                                              |
| `end_time`   | `TIMESTAMPTZ`   | `NOT NULL`, `CHECK (end_time > start_time)`                             |
| `status`     | `VARCHAR(20)`   | `NOT NULL`, default `'SCHEDULED'`, `CHECK` restricted to the 3 values below |
| `created_at` | `TIMESTAMPTZ`   | `NOT NULL`, default `now()`                                            |
| `updated_at` | `TIMESTAMPTZ`   | `NOT NULL`, default `now()`, auto-updated                             |

`status` is restricted to:

```text
SCHEDULED
CANCELLED
COMPLETED
```

`status` defaults to `'SCHEDULED'` — unlike `users.plan_id` (which has no
default because picking a plan is a real business decision the User
Service must make), a newly created show is, definitionally, scheduled;
there's no meaningful choice being hidden by the default. This mirrors
`users.role DEFAULT 'CUSTOMER'`.

Indexes: `idx_shows_content_id`, `idx_shows_venue_id` (FK columns, not
auto-indexed), and `idx_shows_start_time` (for "shows starting soon" /
date-range browsing queries).

---

## 10. Module 2 relationships

```text
content (1) ──────────< (many) shows >────────── (1) venues
  id                      content_id    venue_id                id
                                              │
                                              │ (1)
                                              ▼
                                          (many) seats
                                            venue_id (FK → venues.id)
```

- **Content 1 → many Shows** (`shows.content_id → content.id`, `ON DELETE
  RESTRICT`): a content item can have many scheduled occurrences.
- **Venue 1 → many Shows** (`shows.venue_id → venues.id`, `ON DELETE
  RESTRICT`): a venue can host many shows.
- **Venue 1 → many Seats** (`seats.venue_id → venues.id`, `ON DELETE
  RESTRICT`): a venue has a fixed seat layout.
- All new FKs use `ON DELETE RESTRICT` / `ON UPDATE CASCADE`, matching the
  style already used by `users.plan_id → plans.id` — a referenced row can't
  be deleted while dependents exist, and `id` updates (which never happen
  in practice) would cascade as a safety net.

**Why there is no direct `content → venues` foreign key:** Content and
Venue are independent concepts — the same movie plays at many cinemas, and
the same stadium hosts many different matches/concerts. `shows` is the
join point where a specific Content is paired with a specific Venue for a
specific time window. Modeling the Content↔Venue relationship directly
would either force a content item to belong to one fixed venue (wrong —
"Avengers" isn't tied to one cinema) or require a separate many-to-many
table that `shows` already effectively is, once `start_time`/`end_time`
are added. `shows` is that table.

---

## 11. Module 2 design notes / assumptions

- **No `metadata` column on `content`:** `docs/architecture.md` mentions a
  conceptual `metadata` field for category-specific attributes. It was left
  out here because the explicit column list for this task didn't include
  it. Adding a `JSONB metadata` column later is a straightforward additive
  migration when a concrete category-specific need shows up.
- **`"row"` is quoted:** `ROW` is a reserved word in PostgreSQL (used in the
  `ROW(...)` constructor). The column is declared and referenced as
  `"row"` throughout the DDL to guarantee valid SQL regardless of context;
  raw SQL written against this table should quote it the same way
  (`SELECT "row" FROM seats`).
- **No uniqueness constraint on `shows`** (e.g. preventing two identical
  `content_id` + `venue_id` + `start_time` rows, or overlapping time
  ranges at the same venue): not requested for this pass, and a real
  overlap check would need an exclusion constraint (`btree_gist`), which
  felt like more than this table-structure-only stage called for. Flagged
  here as a candidate for a future migration if double-booking a venue's
  timeslot turns out to be a real risk.
- **`idx_seats_venue_id` overlaps with `uq_seats_physical_seat`:** the
  unique constraint `(venue_id, section, "row", seat_number)` already
  creates a composite index whose leading column is `venue_id`, which
  Postgres can already use for plain `venue_id` lookups. The standalone
  `idx_seats_venue_id` index was added anyway because it was explicitly
  requested; it's harmless but technically redundant, so it's a candidate
  to drop later if index bloat ever matters.

---

## 12. Not yet implemented

- `bookings`, `booking_seats`, `payments`, `rate_limit_policies`.
- Authentication logic (hashing, tokens, sessions) — application code, not schema.
- Spring Boot / backend service code.
- Redis.
- API Gateway configuration.
- No fake/sample data of any kind — plans are the only seeded rows.
- No changes to the existing frontend.
