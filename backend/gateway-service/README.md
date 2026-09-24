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
- **Rate limiting** — Redis-backed. **Static policy implemented (Phase 11)**
  — see "Current status". Per user/plan/route-group **dynamic** limits
  (FR-26–FR-32) are Phase 12, not yet implemented.
- **Request ID generation/propagation** and **structured request logging**.
  **Implemented (Phase 7.4)** — see "Current status".
- **Upstream timeouts** so a slow/unavailable service can't hang requests
  indefinitely. **Implemented (Phase 7.5)** — see "Current status".

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

**Phase 7.4 — request correlation id and controlled error responses.**

- **`X-Request-ID`:** every request gets one. If the request already carries
  the header with an acceptable value (letters, digits, `. _ : -`, 1–128
  characters — enough for a UUID or a typical trace id), that value is kept
  and forwarded upstream unchanged; a missing or unsafe value is replaced by
  a generated random UUID before the request reaches any route or the
  security layer. It never encodes a user, token, or timestamp. The response
  — including a 401 from the security layer or a 404 for an unrouted path —
  always carries the same id, and `spring.cloud.gateway.globalcors` exposes
  it (`Access-Control-Expose-Headers`) so browser JavaScript can read it too.
  Implemented as a plain `WebFilter`
  (`com.eventtick.gateway.filter.RequestIdWebFilter`), not a Spring Cloud
  Gateway `GlobalFilter`, specifically so it also covers the 401s and 404s
  that never reach a route.
- **Controlled error responses:** every error the gateway itself produces —
  the existing Phase 7.3 401, a 403 (nothing triggers one yet — no
  authorization rules exist — but the handler is wired in for when one is
  added), a 404 for a path with no route, and a 502/503/504 when an upstream
  is unreachable, times out, or misbehaves — is the same JSON shape:
  `{"status":..., "error":"...", "message":"...", "requestId":"...",
  "timestamp":"..."}`. The `message` is a fixed, human-written sentence per
  status; the underlying exception (which for a refused connection would
  include the internal host and port) is never put in the response, only
  logged server-side with the request id.
- **Logging:** one line per request —
  `request id=... method=... path=... status=... durationMs=...` — and one
  extra line for a 5xx naming which exception class caused it. Never the
  query string, headers, `Authorization`, JWTs, or passwords.

**Phase 7.5 — upstream timeout protection.** Two timeouts bound how long the
gateway will wait on an upstream service, configured under
`spring.cloud.gateway.httpclient` in `application.yml`:

| Property | Default | Env override | Meaning |
|---|---|---|---|
| `connect-timeout` | `3000` (ms) | `GATEWAY_CONNECT_TIMEOUT_MS` | max time to open the TCP connection to the upstream |
| `response-timeout` | `8s` | `GATEWAY_RESPONSE_TIMEOUT` | max time to wait for the upstream's response once the request has been sent |

These are conservative local-development values, not aggressive production
ones — enough headroom for a real request (a DB write, a seat lock) to
finish during normal testing, while still bounding the worst case. Spring
Cloud Gateway proxies every route through one shared HTTP client built from
this configuration, so it applies uniformly to all three routes
(`user-service`, `catalog-service`, `booking-service`) without repeating it
per route.

A timeout or connection failure is reported through the same
`GatewayErrorHandler` and JSON error shape as Phase 7.4:

- connection refused / upstream unreachable → **503**
- no response within `response-timeout` (or a connect timeout) → **504**
- upstream connection broken mid-response / other I/O failure → **502**

As with every gateway-generated error, the response never includes the
underlying exception, an internal hostname, or a port — only
`status`/`error`/`message`/`requestId`/`timestamp`, with CORS headers and
`X-Request-ID` intact. This is timeout protection only: there is no retry,
no circuit breaker, and no automatic recovery — a timed-out request simply
fails once, cleanly, instead of hanging.

Not implemented yet: retries and circuit breaking. Only `GET`/`POST` are
allowed cross-origin — `PUT`/`DELETE` (needed by the catalog admin APIs)
must be added when the frontend starts calling them.

**Phase 11 — static, Redis-backed rate limiting.** The flow is
`Client -> Gateway -> JWT authentication -> Rate limiter -> Route to
backend`: rate limiting runs after Spring Security has already decided
whether (and who) a request is authenticated as, and before the request is
forwarded anywhere.

- **Why the gateway, not each service:** it is already the single place that
  does authentication, request correlation, and timeout protection for
  every request — adding rate limiting to each of user/catalog/booking
  individually would mean three separate, possibly-inconsistent
  implementations, and would need each service to reimplement the
  authenticated-vs-anonymous identity logic the gateway already has from
  Phase 7.3.
- **Algorithm and shared state:** Spring Cloud Gateway's own
  `RedisRateLimiter` — a token bucket run as one atomic Lua script inside
  Redis. Because the bucket lives in Redis, not in this process's memory,
  multiple gateway instances pointed at the same Redis correctly share one
  limit per key; an in-memory counter would let each instance grant its own
  separate allowance, which is explicitly not what's implemented.
- **Key strategy** (`com.eventtick.gateway.ratelimit.RateLimitKeyResolver`):
  a request with a JWT that Phase 7.3 validated is keyed by the token's
  `sub` claim (`user:<uuid>`) — one bucket per user regardless of device or
  IP. Everything else — the public `register`/`login` endpoints, or any
  other unauthenticated request — is keyed by the caller's TCP source
  address (`ip:<address>`), **not** a client-supplied header such as
  `X-Forwarded-For`: there is no trusted reverse proxy in front of the
  gateway in this local-development setup, so a client-supplied header
  would let an attacker pick a fresh bucket for every request just by
  changing it.
- **Initial static policy** (`spring.cloud.gateway.redis-rate-limiter.*`,
  read by `RateLimitingGlobalFilter`, one shared numeric policy for every
  route — separated only by the key above, not by route or plan):

  | Property | Default | Env override | Meaning |
  |---|---|---|---|
  | `replenish-rate` | `5` | `RATE_LIMIT_REPLENISH_RATE` | tokens refilled per second (steady-state rate) |
  | `burst-capacity` | `10` | `RATE_LIMIT_BURST_CAPACITY` | bucket size — the most a caller can do in one burst |
  | `requested-tokens` | `1` | `RATE_LIMIT_REQUESTED_TOKENS` | cost of a single request |

  Deliberately generous for local development and deliberately simple —
  everyone gets the same numeric policy regardless of plan or role. Phase 12
  (dynamic rate limiting) is expected to replace this with per-plan/adaptive
  policies; nothing here should be read as tuned for production traffic.
- **If Redis is unreachable or errors,** `RedisRateLimiter` **fails open**:
  the request is treated as allowed, not blocked, and the failure is only
  logged (at Spring Cloud Gateway's own debug level) — a rate-limiter outage
  must not become a full API outage. `spring.data.redis.timeout` and
  `.connect-timeout` (`300ms` each by default, env-overridable via
  `REDIS_TIMEOUT`/`REDIS_CONNECT_TIMEOUT`) keep that discovery fast rather
  than a hang, in the same spirit as Phase 7.5's upstream timeouts.
- **429 response:** the same JSON shape as every other gateway-generated
  error — `{"status":429,"error":"TOO_MANY_REQUESTS","message":"...",
  "requestId":"...","timestamp":"..."}` — with `X-Request-ID` and CORS
  headers intact, so the browser can read it. Every response, allowed or
  not, also carries the conventional (not IETF-standardized)
  `X-RateLimit-Remaining` / `X-RateLimit-Replenish-Rate` /
  `X-RateLimit-Burst-Capacity` headers `RedisRateLimiter` itself builds, so
  a well-behaved client can back off before it ever gets a 429.
- **Requires Redis.** `spring.data.redis.host`/`.port` (env `REDIS_HOST`/
  `REDIS_PORT`, default `localhost:6379`) must point at a running Redis for
  rate limiting to actually take effect; see "Local Redis for development"
  below. Nothing else in the gateway depends on Redis, and the gateway
  starts and serves traffic normally (fail-open, as above) without one.

Not implemented yet: dynamic/per-plan/adaptive policies, admin-configurable
limits, and anything load-based — all Phase 12. This is authentication +
static rate limiting only.

### Local Redis for development

Rate limiting needs a Redis reachable at `spring.data.redis.host`/`.port`
(default `localhost:6379`). This repository does not run one for you (no
Docker/deployment changes were made in this phase) — start one yourself,
for example `redis-server` if installed locally, a Redis Windows/WSL
package, or a manually-started `docker run -p 6379:6379 redis` if Docker is
available on your machine. Without one, the gateway still starts and serves
traffic normally — it just never rate-limits (fail-open, documented above).

## Tech

Java 17, Spring Boot 3.3.4, Spring Cloud Gateway 2023.0.3 (reactive/WebFlux).

## Running (once built)

```powershell
mvn spring-boot:run
```

Listens on port `8080` (see `src/main/resources/application.yml`).
