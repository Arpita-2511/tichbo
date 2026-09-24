# gateway-service

The single entry point for all client requests. Client applications
(the Eventtick frontend, Postman, etc.) will talk only to this service —
never directly to `user-service`, `catalog-service`, or `booking-service`
(see `docs/requirements.md`, FR-19).

## Eventual responsibilities (per `docs/architecture.md` §5–§6)

- **Request routing** — `/api/auth/**` and `/api/users/**` → user-service;
  `/api/catalog/**` → catalog-service; `/api/bookings/**` →
  booking-service. **Implemented (Phase 7.1)** — see "Current status".
- **Authentication** — validate JWTs on protected routes; reject
  invalid/expired credentials. **Implemented (Phase 7.3)** — see
  "Current status".
- **Coarse-grained authorization** — role checks at the edge; fine-grained
  resource authorization stays in each business service.
- **Dynamic rate limiting** — Redis-backed, per user/plan/route-group
  token buckets (FR-26–FR-32).
- **Request ID generation/propagation** and **structured request logging**.
- **Upstream timeouts** so a slow/unavailable service can't hang requests
  indefinitely.

## Current status

**Phase 7.1 — basic routing only.** Three routes are configured in
`src/main/resources/application.yml`, forwarding the original path
unchanged (no `StripPrefix`/`RewritePath` — the backends already serve
these exact `/api/...` paths):

| Route id | Path predicate | Upstream |
|---|---|---|
| `user-service` | `/api/auth/**`, `/api/users/**` | `http://localhost:8081` |
| `catalog-service` | `/api/catalog/**` | `http://localhost:8082` |
| `booking-service` | `/api/bookings/**` | `http://localhost:8083` |

Anything else returns `404`. Upstream URLs are fixed to localhost for now.

**Phase 7.2 — the frontend goes through the gateway.** The Eventtick
frontend (`http://localhost:5173`) sends its authentication requests to
this gateway (`http://localhost:8080`), not to `user-service`.

CORS is configured here (`spring.cloud.gateway.globalcors`, path
`/api/**`): allowed origins `http://localhost:5173` and
`http://127.0.0.1:5173` (override with `CORS_ALLOWED_ORIGINS`, never `*`),
methods `GET`/`POST`/`OPTIONS`, headers `Authorization`/`Content-Type`,
credentials off (auth is a Bearer header, not a cookie). The gateway
answers preflight requests itself.

`user-service` keeps its own CORS config as defense-in-depth. That means a
proxied response would carry `Access-Control-Allow-Origin` twice — once
from each — and browsers reject duplicates, so a `DedupeResponseHeader`
default filter keeps the first copy. It only touches those response
headers, never the request path.

**Phase 7.3 — JWT authentication at the gateway.** `user-service` still
issues the tokens; the gateway validates them before forwarding and answers
`401` itself (the backend is never contacted) for a missing, malformed,
wrongly-signed, expired, wrong-issuer or wrong-audience token.

- **Public** (no token needed): exactly `POST /api/auth/register` and
  `POST /api/auth/login`. A stale token sent with these is ignored.
- **Protected** (valid JWT required): everything else, deny-by-default —
  `/api/users/me`, `/api/catalog/**`, `/api/bookings/**`, and any path not
  routed at all. CORS preflight (`OPTIONS`) needs no token.
- **Configuration:** `jwt.secret` / `jwt.issuer` / `jwt.audience`, read from
  the env vars `JWT_SECRET` / `JWT_ISSUER` / `JWT_AUDIENCE` — the same names
  `user-service` uses, and the values **must match** or every token is
  rejected. Set `JWT_SECRET` (at least 32 bytes) for anything beyond local
  development; the built-in fallback is a dev-only placeholder. The secret is
  used as raw text, not base64, exactly as `user-service` uses it.
- **Authentication only.** No role or ownership rules yet — any valid token
  (`CUSTOMER` or `ADMIN`) passes for catalog and booking too. The `role`
  claim is exposed as a `ROLE_*` authority and `plan` stays readable from the
  token, ready for later phases. The `Authorization` header is forwarded
  unchanged (`user-service` re-validates it for `/api/users/me`).
- The 401 body is a generic `UNAUTHENTICATED` JSON error with a bare
  `WWW-Authenticate: Bearer`; it never says why the token failed. It carries
  the CORS headers so the browser can read it.

Direct access to the service ports (8081–8083) is still unauthenticated at
the network level; only traffic through the gateway is checked here.

Not implemented yet: rate limiting (Redis), request IDs, logging filters,
and timeouts. Only `GET`/`POST` are allowed
cross-origin — `PUT`/`DELETE` (needed by the catalog admin APIs) must be
added when the frontend starts calling them.

## Tech

Java 17, Spring Boot 3.3.4, Spring Cloud Gateway 2023.0.3 (reactive/WebFlux).

## Running (once built)

```powershell
mvn spring-boot:run
```

Listens on port `8080` (see `src/main/resources/application.yml`).
