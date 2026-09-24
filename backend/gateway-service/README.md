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
- **Rate limiting** — Redis-backed. **Static policy (Phase 11), then
  dynamic per-category/plan/role policy selection (Phase 12), both
  implemented** — see "Current status". Runtime/admin-configurable policy
  management (part of FR-31) remains not implemented.
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

Phase 11's *policy* — a single shared numeric limit for every request,
regardless of who or what — is superseded by Phase 12 below. Everything
above this paragraph (the mechanism: gateway-side, Redis-backed, fail-open,
same 429 shape) is unchanged and still exactly how Phase 12 works too.

**Phase 12 — dynamic policy selection.** The one static policy is replaced
by a policy chosen per request from two independent inputs — what kind of
request it is, and who is making it:

```
Client -> Gateway -> JWT authentication -> Dynamic policy resolution -> Redis token bucket -> Backend service
```

- **Request category** (`com.eventtick.gateway.ratelimit.RequestCategoryClassifier`,
  a pure function of the path, independent of Spring Cloud Gateway route
  ids so it can't drift from them silently):

  | Category | Path prefix |
  |---|---|
  | `AUTH` | `/api/auth/**` |
  | `CATALOG` | `/api/catalog/**` |
  | `BOOKING` | `/api/bookings/**` |
  | `USER` | `/api/users/**` |
  | `UNKNOWN` | anything else |

- **User tier** (`com.eventtick.gateway.ratelimit.UserTierResolver`), from
  the **validated** JWT's claims only — never a client-supplied header,
  query parameter, or body field (an `X-Plan: PRO` header is simply never
  read):
  - no authenticated JWT on the request → `PUBLIC`;
  - `role=ADMIN` → `ADMIN`, **regardless of the account's plan** — role and
    plan are two independent columns in the User Service's data model (a
    `UserRole` enum and a `plans` foreign key), so an admin account's plan
    claim is deliberately not consulted once the role claim says `ADMIN`;
  - otherwise, the `plan` claim, matched case-insensitively against the
    plans that actually exist today (`database/migrations/0003_seed_plans.up.sql`:
    **Free, Pro, Premium** — not the frontend's own unrelated mock data in
    `eventtick/src/types/index.ts`, which also lists a `VIP` plan that has
    no backend counterpart) → `FREE`, `PRO`, or `PREMIUM`;
  - a missing or unrecognized `plan` claim → `FREE`. The gateway's JWT
    decoder does not structurally validate this claim's shape (only `role`
    and `sub` are validated), so an unrecognized value is treated as
    untrusted-in-shape and never silently upgraded to a higher tier.
- **Policy = `(category, tier)`**, looked up in the matrix below
  (`com.eventtick.gateway.ratelimit.RateLimitPolicyResolver`). If a specific
  pair is not configured — most importantly `UNKNOWN` category, but also a
  defensive catch-all for any gap — the resolver falls back to one
  explicit, conservative, always-defined fallback policy. **An unrecognized
  endpoint is never left unlimited.**
- **Configuration** (`eventtick.rate-limit.*` in `application.yml` — a
  dedicated namespace, not `spring.cloud.gateway.redis-rate-limiter.*`,
  which only has room for one flat policy and is retired):

  ```yaml
  eventtick:
    rate-limit:
      policies:
        <CATEGORY>:
          <TIER>: { replenish-rate: N, burst-capacity: N }
      fallback:
        replenish-rate: N
        burst-capacity: N
  ```

  The shipped matrix (illustrative development numbers reasoned from the
  Eventtick use case, **not production-certified**):

  | Category | FREE | PRO | PREMIUM | ADMIN |
  |---|---|---|---|---|
  | AUTH *(public only — see below)* | 2 / 5 | — | — | — |
  | CATALOG | 8 / 16 | 20 / 40 | 40 / 80 | 100 / 200 |
  | BOOKING | 4 / 8 | 10 / 20 | 20 / 40 | 50 / 100 |
  | USER | 5 / 10 | 10 / 20 | 15 / 30 | 50 / 100 |

  (`replenish-rate` requests/sec / `burst-capacity`; `requested-tokens`
  defaults to 1, a plain per-request cost.) `AUTH` only has a `PUBLIC` entry
  (`2 / 5`, the strictest policy of all — the public register/login
  endpoints are a brute-force target, and are IP-keyed, a coarser identity
  than per-user): `GatewaySecurityConfig`'s public chain never reads the
  `Authorization` header at all, so an authenticated request can never
  reach the `AUTH` category in practice. Reasoning behind the shape:
  `CATALOG` (read-heavy, cheap) is the most generous category at every
  tier; `BOOKING` (write-heavy, seat-lock contention) is the strictest
  authenticated category; `USER` sits in between; across tiers, `PRO` is
  roughly 2–2.5× `FREE`, `PREMIUM` roughly 2× `PRO`, and `ADMIN` is
  generously high everywhere — for operational/support work, not as a
  bypass. The `fallback` policy (`2 / 5`) is as strict as `AUTH:PUBLIC`.
  Changing any of this needs a restart (read once at startup); an admin
  API/UI for runtime policy changes is explicitly out of scope for this
  phase (Phase 13+).
- **Bucket key design** (`com.eventtick.gateway.ratelimit.RateLimitingGlobalFilter`):
  a concrete technical finding shaped this. Reading Spring Cloud Gateway
  4.1.5's own source shows `RedisRateLimiter.isAllowed(routeId, id)` uses
  `routeId` *only* to pick which `Config` (the numbers) to charge against —
  the actual Redis key is built from `id` alone. Passing just `user:<uuid>`
  for every policy would therefore let two different policies applied to
  the same caller (say, a `CATALOG` request and a `BOOKING` request from
  the same user) silently share one bucket. To prevent that, the key
  actually used is **`<policyId>:<identity>`** — e.g. `CATALOG:PRO:user:<uuid>`
  — so `FREE`-catalog, `PRO`-catalog, and `FREE`-booking for the same user
  are three separate buckets, never one. The identity half
  (`user:<uuid>`/`ip:<address>`) is unchanged from Phase 11
  (`RateLimitKeyResolver`).
- **Registering many policies with the native `RedisRateLimiter`:** the
  same technique Phase 11 used for its one policy — every policy in the
  matrix (plus the fallback) is registered directly on the limiter's own
  config map at startup, since `isAllowed` requires an entry to already
  exist for whatever id it is given.
- **Why a `WebFilter`, not a `GlobalFilter` (a Phase 11 bug, found and fixed
  while testing the fallback):** a Spring Cloud Gateway `GlobalFilter` only
  runs once a route has matched — for a genuinely unrecognized path,
  `RoutePredicateHandlerMapping` finds no handler at all, so the filter
  chain that runs `GlobalFilter`s never executes, and Phase 11's
  `GlobalFilter`-based limiter silently never rate-limited unrouted paths.
  It is now a plain `WebFilter` (the same fix Phase 7.4's
  `RequestIdWebFilter` already made for the analogous 401/404 problem),
  ordered to run after Spring Security's chain (`order -50`, Security
  itself is `-100`) so the security context is populated, but for every
  request regardless of routing outcome.

Not implemented in this phase: admin-configurable or database-backed
runtime policy management, adaptive/system-load-based limiting, and
machine-learning-based rate limiting. All are explicitly future work, not
Phase 12.

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
