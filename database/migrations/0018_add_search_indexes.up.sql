-- Migration: 0018_add_search_indexes
-- Description: Adds expression indexes on content.title and venues.city to
--              support the catalog search endpoint (GET /api/catalog/search).
--              The search query uses LOWER(column) LIKE '%term%' for
--              case-insensitive text matching. A btree index on LOWER(column)
--              does not help a leading-wildcard LIKE, but a pg_trgm GIN index
--              does — it accelerates both '%term%' and 'term%' patterns.
-- Depends on: 0004_create_content_table, 0005_create_venues_table.

-- pg_trgm ships with PostgreSQL and is commonly available; CREATE EXTENSION
-- is idempotent with IF NOT EXISTS.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_content_title_trgm
    ON content USING gin (LOWER(title) gin_trgm_ops);

CREATE INDEX idx_content_description_trgm
    ON content USING gin (LOWER(description) gin_trgm_ops);

CREATE INDEX idx_venues_city_lower
    ON venues (LOWER(city));
