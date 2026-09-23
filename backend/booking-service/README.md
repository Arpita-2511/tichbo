# booking-service

Owns show-specific seat availability, bookings, and their seat line items —
the `show_seats`, `bookings`, and `booking_seats` tables
(`database/migrations/0008`–`0010`). See `docs/architecture.md` §17:
`show_seats` references a physical seat and a show (both owned by
catalog-service), but whether that seat is available/held/booked for a
given show is booking-domain state, owned here.

## Eventual responsibilities

- Expose show-specific seat availability and pricing (FR-11) — the
  `show_seats` status (`AVAILABLE`/`HELD`/`BOOKED`) and per-show price.
- Create a booking for one user, one show, and one or more seats (FR-13).
- Prevent double booking (FR-14) and safely handle concurrent booking
  attempts on the same seat (FR-15) — using PostgreSQL transactions and
  row-level locking directly on this service's own `show_seats` table, per
  the schema-level design in
  `database/migrations/0008_create_show_seats_table.up.sql`.
- Booking cancellation (FR-16) and booking history (FR-17).
- Later: coordinate with a Redis-backed temporary seat hold (not
  implemented yet) before a booking is confirmed.

## Current status

Skeleton only. No controllers, services, repositories, or entities exist
yet — see the `package-info.java` in each package under
`src/main/java/com/eventtick/booking/` for what belongs where. No
concurrency-control logic, no Redis integration.

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

Listens on port `8083`. `mvn test`/`mvn package` do **not** require a live
PostgreSQL connection — tests run against an in-memory H2 database (see
`src/test/resources/application.yml`).
