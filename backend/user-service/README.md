# user-service

Owns user accounts, roles, and subscription plans — the `users` and
`plans` tables (`database/migrations/0001`–`0002`). Per
`docs/architecture.md` §7: other services must not query this service's
data directly; they go through its API.

## Eventual responsibilities

- User registration (FR-01) — validated input, hashed passwords, never
  plaintext.
- Login/authentication (FR-02) — issue an access token on success;
  refresh-token support.
- Authorization data — expose role (`CUSTOMER`/`ADMIN`) for the Gateway
  and other services to use.
- Profile management, subscription-plan assignment and changes.

## Current status

Skeleton only. No controllers, services, repositories, or entities exist
yet — see the `package-info.java` in each package under
`src/main/java/com/eventtick/user/` for what belongs where. No
authentication is implemented.

## Tech

Java 17, Spring Boot 3.3.4, Spring Web, Spring Data JPA, PostgreSQL driver.

`spring.jpa.hibernate.ddl-auto=none` is set deliberately — this service
must never auto-generate or alter schema. `database/migrations/` is the
single source of truth for the `users`/`plans` table structure.

## Running (once built)

Requires PostgreSQL reachable at the URL in
`src/main/resources/application.yml` (defaults to `eventtick_db` on
`localhost:5432`), with `DB_USERNAME`/`DB_PASSWORD` set as needed:

```powershell
mvn spring-boot:run
```

Listens on port `8081`. `mvn test`/`mvn package` do **not** require a live
PostgreSQL connection — tests run against an in-memory H2 database (see
`src/test/resources/application.yml`).
