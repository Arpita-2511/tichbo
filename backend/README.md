# Eventtick Backend

Four independent Spring Boot / Maven services, matching the service
boundaries in `docs/architecture.md`:

```
backend/
├── gateway-service/    → single entry point; routes to the services below
├── user-service/       → owns users + plans
├── catalog-service/    → owns content, venues, seats, shows
└── booking-service/    → owns show_seats, bookings, booking_seats
```

## Status

This is a **structure-only skeleton**. Each service boots (once built) with
no business logic, no authentication, no gateway routing, no Redis, no
Kafka. See each service's own `README.md` for what it will eventually do
and its planned endpoints.

## Requirements to build

- **JDK 17+** — not yet installed in this environment (only JDK 8 was
  found when this skeleton was created). Install a JDK 17+ distribution
  (e.g. Temurin) before building.
- **Maven 3.9+** — not currently installed. Each service is a standalone
  Maven project (no Maven Wrapper included yet); install Maven, or add the
  wrapper later with `mvn wrapper:wrapper` once Maven is available.
- **PostgreSQL** — `user-service`, `catalog-service`, and `booking-service`
  declare a datasource pointing at the existing `eventtick_db` (see
  `database/README.md`). A live database isn't required to *build*
  (`mvn clean package` runs tests against an in-memory H2 database), only
  to actually *run* a service.

## Building a service

Each service is independently buildable:

```powershell
cd backend/user-service
mvn clean package
```

There is no root/parent `pom.xml` on purpose — each service is a complete,
self-contained Maven project extending `spring-boot-starter-parent`
directly. This keeps the services decoupled and matches "independently
buildable."

## What's deliberately not here yet

- Business logic (controllers/services/repositories are empty package
  skeletons — see each package's `package-info.java`).
- Authentication (Spring Security, JWT).
- API Gateway routing (no `RouteLocator`/route config in `gateway-service`).
- Redis (rate limiting, seat holds).
- Kafka (async events).
- Any fake/sample data.

These come in later phases per `docs/architecture.md` §39 (Development Sequence).
