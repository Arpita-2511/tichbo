-- Rollback: 0018_add_search_indexes

DROP INDEX IF EXISTS idx_venues_city_lower;
DROP INDEX IF EXISTS idx_content_description_trgm;
DROP INDEX IF EXISTS idx_content_title_trgm;

-- pg_trgm extension is left in place — dropping it would break any other
-- index or query that depends on it.
