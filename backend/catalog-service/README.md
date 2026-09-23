# catalog-service

Owns bookable content, venues, physical seat layouts, and scheduled shows —
the `content`, `venues`, `seats`, and `shows` tables
(`database/migrations/0004`–`0007`). Per `docs/architecture.md` §8,
intentionally generic so the platform isn't restricted to movies (MOVIE,
SPORTS_MATCH, CONCERT, THEATRE, EVENT all share this one model).

Note: `show_seats` (show-specific seat availability and pricing,
`database/migrations/0008`) is owned by **booking-service**, not this
service — see `docs/architecture.md` §17. It references `seats` and
`shows`, both owned here, but availability/pricing-per-show is part of the
booking domain.

## Eventual responsibilities

- Content management (FR-04) and show scheduling (FR-05).
- Venue (FR-06) and physical seat-layout (FR-07) management.
- Browse (FR-08), search (FR-09), and filtering (FR-10).

## Current status

Skeleton only. No controllers, services, repositories, or entities exist
yet — see the `package-info.java` in each package under
`src/main/java/com/eventtick/catalog/` for what belongs where.

## Tech

Java 17, Spring Boot 3.3.4, Spring Web, Spring Data JPA, PostgreSQL driver.

`spring.jpa.hibernate.ddl-auto=none` is set deliberately — this service
must never auto-generate or alter schema. `database/migrations/` is the
single source of truth for its table structures.

## Running (once built)

Requires PostgreSQL reachable at the URL in
`src/main/resources/application.yml` (defaults to `eventtick_db` on
`localhost:5432`), with `DB_USERNAME`/`DB_PASSWORD` set as needed:

```powershell
mvn spring-boot:run
```

Listens on port `8082`. `mvn test`/`mvn package` do **not** require a live
PostgreSQL connection — tests run against an in-memory H2 database (see
`src/test/resources/application.yml`).
