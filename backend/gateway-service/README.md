# gateway-service

The single entry point for all client requests. Client applications
(the Eventtick frontend, Postman, etc.) will talk only to this service —
never directly to `user-service`, `catalog-service`, or `booking-service`
(see `docs/requirements.md`, FR-19).

## Eventual responsibilities (per `docs/architecture.md` §5–§6)

- **Request routing** — `/api/auth/**` and `/api/users/**` → user-service;
  `/api/content/**`, `/api/venues/**`, `/api/shows/**` → catalog-service;
  `/api/bookings/**` → booking-service.
- **Authentication** — validate JWTs on protected routes; reject
  invalid/expired credentials.
- **Coarse-grained authorization** — role checks at the edge; fine-grained
  resource authorization stays in each business service.
- **Dynamic rate limiting** — Redis-backed, per user/plan/route-group
  token buckets (FR-26–FR-32).
- **Request ID generation/propagation** and **structured request logging**.
- **Upstream timeouts** so a slow/unavailable service can't hang requests
  indefinitely.

## Current status

Skeleton only. `spring-cloud-starter-gateway` is on the classpath and the
app boots, but **no routes are configured** — every request currently has
nowhere to go. No authentication, rate limiting, or logging filters exist
yet.

## Tech

Java 17, Spring Boot 3.3.4, Spring Cloud Gateway 2023.0.3 (reactive/WebFlux).

## Running (once built)

```powershell
mvn spring-boot:run
```

Listens on port `8080` (see `src/main/resources/application.yml`).
