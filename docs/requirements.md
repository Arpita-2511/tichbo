# Tichboo — System Requirements

## 1. Document Purpose

This document defines the functional and non-functional requirements for Tichboo, a scalable multi-category ticket-booking platform.

Tichboo supports multiple types of bookable experiences, including:

* Movies
* Sports matches
* Concerts
* Theatre
* Other events

The primary technical objective is to build a distributed ticket-booking platform protected by a production-style API Gateway.

The system demonstrates:

* Microservices architecture
* API Gateway
* Authentication and authorization
* Dynamic API rate limiting
* Redis
* Concurrent seat booking
* Database transactions
* Asynchronous communication
* Monitoring and observability
* Data analytics and machine learning as a future extension

This document defines **what the system must do**.

The architecture document defines **how the system will do it**.

---

# 2. Problem Statement

A ticket-booking platform can experience a large number of simultaneous users when a popular movie, sports match, concert, or event becomes available.

For example, assume a show contains 100 seats.

Thousands of users may simultaneously attempt to:

* View the show
* Check seat availability
* Select seats
* Hold seats
* Book tickets
* Complete payment

The system must continue operating correctly while preventing:

* Duplicate bookings
* Invalid seat states
* Excessive API traffic
* Unauthorized access
* Inconsistent booking information
* Uncontrolled backend overload

Therefore, Tichboo must provide a controlled and scalable architecture capable of handling high request volumes and concurrent booking operations.

---

# 3. Project Objectives

## 3.1 Primary Objectives

The system shall:

1. Provide a web-based ticket-booking platform.
2. Support multiple categories of bookable content.
3. Authenticate users securely.
4. Authorize users based on roles and permissions.
5. Provide a centralized API Gateway.
6. Route requests to appropriate backend services.
7. Implement dynamic API rate limiting.
8. Use Redis for rate-limit state and temporary state.
9. Prevent double booking of seats.
10. Maintain persistent booking information.
11. Provide administrative functionality.
12. Provide system monitoring and structured logging.
13. Support future horizontal scaling.
14. Provide a foundation for analytics and machine learning.
15. Maintain clear boundaries between business logic and infrastructure logic.

---

# 4. Users and Roles

The system initially supports two primary roles.

## 4.1 Customer

A customer can:

* Register
* Log in
* Manage their profile
* Browse bookable content
* Search for content and shows
* View content details
* View show information
* View seat availability
* Select seats
* Temporarily hold seats
* Book tickets
* View booking history
* Cancel eligible bookings
* View subscription information

## 4.2 Administrator

An administrator can:

* Manage users
* Manage bookable content
* Manage shows
* Manage venues
* Monitor bookings
* Configure rate-limit policies
* Monitor system traffic
* View system statistics

Additional roles may be introduced later if required.

---

# 5. Content and Booking Domain

Tichboo is intentionally not restricted to movies.

The system uses a generic concept called **Content**.

Content represents something that can have one or more scheduled shows.

Initial content types include:

```text
MOVIE
SPORTS_MATCH
CONCERT
THEATRE
EVENT
```

The same booking infrastructure must be capable of handling all supported content types.

Conceptually:

```text
Content
   |
   └── Show
         |
         └── Venue
               |
               └── Seats
```

This prevents the booking system from becoming dependent on one particular category.

---

# 6. Functional Requirements

## FR-01: User Registration

The system shall allow a new customer to create an account using required information such as:

* Name
* Email
* Password

The system shall validate the submitted information before creating an account.

Passwords shall not be stored as plain text.

---

## FR-02: User Authentication

The system shall allow registered users to log in.

After successful authentication, the system shall issue an access token containing the required authorization information.

Protected APIs shall require valid authentication credentials.

Refresh-token functionality may be implemented for maintaining sessions securely.

---

## FR-03: Authorization

The system shall distinguish between users based on roles and permissions.

For example:

```text
CUSTOMER

    → Browse
    → Search
    → View shows
    → Book
    → View own bookings


ADMIN

    → Manage content
    → Manage users
    → Manage shows
    → Manage venues
    → Manage rate-limit policies
```

Authentication and authorization shall be treated as separate concerns.

Business services shall enforce resource-level authorization where necessary.

---

# 7. Subscription Plans

The platform shall support configurable subscription plans.

Initial conceptual plans:

```text
FREE
PREMIUM
VIP
```

The subscription plan may influence the API rate limit assigned to a user.

For example:

```text
FREE      → Policy A
PREMIUM   → Policy B
VIP       → Policy C
```

The exact numerical limits will be defined during implementation and testing.

Subscription plans shall not automatically determine whether a user is authorized to perform a business operation.

---

# 8. Catalog Requirements

## FR-04: Content Management

The system shall support bookable content such as:

* Movies
* Sports matches
* Concerts
* Theatre
* Other events

Each content item may contain information such as:

* Title/name
* Description
* Category/type
* Language
* Duration where applicable
* Genre where applicable
* Date
* Status

Category-specific attributes may be added without changing the overall booking workflow.

---

## FR-05: Show Management

A Show represents a scheduled occurrence of Content.

A Show shall be associated with:

* Content
* Venue
* Date
* Start time
* End time
* Availability/status

Conceptually:

```text
Content
   |
   └── Multiple Shows
```

The same content may have multiple shows at different times or venues.

---

## FR-06: Venue Management

The system shall maintain venue information.

A venue may contain:

* Name
* Address
* City
* Capacity
* Seat layout

---

## FR-07: Seat Management

The system shall maintain the physical seat layout of supported venues.

Seats may contain:

* Section
* Row
* Seat number
* Seat type

The physical seat definition belongs to the venue.

The availability of that seat for a particular show belongs to the booking domain.

---

# 9. Search and Discovery

## FR-08: Browse Content

Users shall be able to browse available content.

---

## FR-09: Search

Users shall be able to search for relevant content or shows.

Search may include:

* Movies
* Sports matches
* Concerts
* Theatre
* Events
* Venues

---

## FR-10: Filtering

The system should support filters such as:

* Category
* Date
* Location
* Venue
* Availability

Additional filters may be introduced later.

---

# 10. Seat Management

## FR-11: Seat Availability

The system shall display the current availability of seats for a selected show.

Initial seat states are:

```text
AVAILABLE
HELD
BOOKED
```

---

## FR-12: Temporary Seat Hold

When a user selects an available seat, the system may temporarily hold that seat.

Example:

```text
AVAILABLE
    ↓
HELD
    ↓
BOOKED
```

If the booking process does not complete within the configured hold period:

```text
HELD
  ↓
AVAILABLE
```

Redis may be used for temporary seat-hold state.

The final booking confirmation must be persisted in PostgreSQL.

**Implementation status.** The `AVAILABLE → HELD → BOOKED` transition
(`POST /api/bookings/shows/{showId}/seats/hold`, booking-service) is
implemented using a PostgreSQL `SELECT ... FOR UPDATE` row lock on
`show_seats` (`ShowSeatRepository.lockAllByIdIn`), not Redis — this
satisfies FR-14/FR-15 (no two callers can claim the same seat) without
needing Redis at all. What is **not implemented**: Redis (or any other)
backing for the hold itself, hold *ownership* (`show_seats` has no column
recording who holds a seat — `holdSeats`'s `userId` parameter is accepted
but currently unused/unchecked), and hold *expiry* (no TTL anywhere — a
`HELD` seat stays `HELD` indefinitely unless explicitly released or the
booking is cancelled; this is not "temporary" in the current build). See
`docs/architecture.md` §16 and `BookingService`'s own documented
concurrency/ownership notes.

---

# 11. Booking Requirements

## FR-13: Create Booking

Authenticated users shall be able to create a booking for available seats.

A booking shall contain information such as:

* Booking ID
* User ID
* Show ID
* Selected seats
* Booking status
* Amount
* Timestamp

---

## FR-14: Prevent Double Booking

The system shall ensure that a seat cannot be successfully booked by multiple users for the same show.

For example:

```text
User A ──┐
         ├── Seat A10
User B ──┘

Result:

Only one booking succeeds.
```

This requirement must be enforced at the Booking Service/database level rather than relying only on frontend validation.

---

## FR-15: Concurrent Booking Control

The system shall safely handle simultaneous booking attempts for the same seat.

The implementation shall use appropriate transaction and concurrency-control mechanisms.

Possible mechanisms include:

* Database transactions
* Row-level locking
* Unique constraints
* Atomic state transitions
* Temporary Redis holds

The final mechanism will be determined during Booking Service implementation.

---

## FR-16: Booking Cancellation

Users shall be able to cancel bookings when cancellation is permitted according to configured business rules.

When a booking is cancelled, the associated seats may become available again.

---

## FR-17: Booking History

Users shall be able to view their previous bookings.

A customer shall only be able to access their own booking history unless they have appropriate administrative permissions.

---

# 12. Payment Requirements

## FR-18: Payment Processing

The system shall support a payment-processing workflow.

During initial development, payment may be simulated rather than integrated with a real payment provider.

Possible states include:

```text
PENDING
SUCCESS
FAILED
```

Payment functionality may later be replaced by a real payment provider.

Payment is not required for the initial Gateway/rate-limiting implementation.

---

# 13. API Gateway Requirements

## FR-19: Centralized Entry Point

Client applications shall communicate with backend services through the API Gateway.

The frontend should not directly communicate with individual internal microservices.

---

## FR-20: Request Routing

The Gateway shall route requests to appropriate services.

Conceptually:

```text
/api/auth/**        → User Service

/api/users/**       → User Service

/api/content/**     → Catalog Service

/api/venues/**      → Catalog Service

/api/shows/**       → Catalog Service

/api/bookings/**    → Booking Service

/api/payments/**    → Payment Service
```

The exact API paths will be defined during the API-contract phase.

---

## FR-21: Request Authentication

The Gateway shall validate authentication information for protected routes.

Invalid or expired authentication credentials shall be rejected.

---

## FR-22: Request Authorization

The Gateway may perform coarse-grained authorization for protected routes.

Business services shall perform resource-specific authorization where required.

---

## FR-23: Request Logging

The Gateway shall record appropriate request information for monitoring and debugging.

---

## FR-24: Request Identification

The Gateway shall generate or propagate a request ID.

The request ID should be propagated to downstream services where practical.

---

## FR-25: Timeout Handling

The Gateway shall enforce appropriate upstream request timeouts.

The system should prevent indefinitely waiting for an unavailable or slow downstream service.

---

# 14. Rate-Limiting Requirements

**Implementation status (Phase 11, then Phase 12):** Phase 11 built one
fixed policy; Phase 12 replaced *what policy applies* with a per-category/
per-tier selection while keeping the same underlying mechanism. Both live
in `backend/gateway-service` (`com.eventtick.gateway.ratelimit`):

* **FR-26** (API rate limiting) — implemented. As of Phase 12, "specified
  API route groups" and "configured policies" (plural) are real: four
  request categories (`AUTH`/`CATALOG`/`BOOKING`/`USER`, path-based — see
  `RequestCategoryClassifier`), each with its own policy per tier.
* **FR-27** (dynamic rate limiting) — **implemented.** The policy resolved
  for a request depends on its category (route group) and the caller's
  plan/role (from the validated JWT) — see `RateLimitPolicyResolver`.
  Phase 19 added runtime administrative configuration: policies are stored
  in Redis, served from an in-memory cache, and updated via the admin CRUD
  API without restarting the Gateway (hot reload).
* **FR-28** (shared rate-limit state) — implemented: the state is Redis, so
  multiple Gateway instances correctly share one allowance per key. Phase
  12 additionally had to fold the policy id into that key
  (`<policyId>:<identity>`), since `RedisRateLimiter` itself only uses the
  policy id to pick a numeric config, not as part of the actual Redis key —
  otherwise two different policies for the same caller would collide.
* **FR-29** (Redis-based rate limiting) — implemented, using a token-bucket
  algorithm (Spring Cloud Gateway's `RedisRateLimiter`), matching the
  conceptual flow this section describes below, with
  `<category>:<tier>` as the "Route Group" input and the caller's identity
  (authenticated user, or IP for everyone else) as the "User" input.
* **FR-30** (rate-limit response) — implemented: `HTTP 429`. No retry
  guidance is included in the body yet, beyond the standard
  `X-RateLimit-*` headers.
* **FR-31** (administrative rate-limit configuration) — **implemented
  (Phase 19).** Admin CRUD API at `/api/admin/rate-limits/policies`
  (GET/POST/PUT/DELETE), requiring `ROLE_ADMIN`. Policies are persisted in
  Redis Hashes, cached in a `ConcurrentHashMap`, and hot-reloaded onto the
  `RedisRateLimiter` config map on every write — no Gateway restart needed.
  The frontend Admin Dashboard provides a full CRUD UI for policy
  management, replacing the previous mock data.
* **FR-32** (rate-limit failure handling) — implemented, with an
  explicit, documented policy: if Redis is unreachable, the limiter fails
  open (requests are allowed, not blocked) rather than silently bypassing
  the *concept* of a failure policy — see `docs/architecture.md`'s Phase 12
  section for the reasoning. Unchanged since Phase 11.
* **Classification coverage (Phase 19: closed).**
  `RequestCategoryClassifier` now recognizes six categories:
  `AUTH`/`CATALOG`/`BOOKING`/`USER`/`PAYMENT`/`ADMIN` by path prefix.
  `PAYMENT` covers `/api/payments/**`, `ADMIN` covers `/api/admin/**`.
  Each has its own tier-aware policy matrix in `application.yml` and in the
  dynamic policy store. Unrecognized paths still fall back to the
  conservative fallback policy.

## FR-26: API Rate Limiting

The system shall limit the number of requests that a user or client can make to specified API route groups according to configured policies.

---

## FR-27: Dynamic Rate Limiting

Rate limits shall not be permanently hard-coded into the Gateway.

The system shall support configurable policies.

Policies may depend on:

* User plan
* Route group
* Request type
* Administrative configuration

---

## FR-28: Shared Rate-Limit State

Rate-limit state shall be shared across Gateway instances.

This ensures that running multiple Gateway instances does not unintentionally provide each instance with a separate allowance for the same user.

---

## FR-29: Redis-Based Rate Limiting

Redis shall be used for storing rapidly changing rate-limit state.

Conceptually:

```text
User + Route Group
        ↓
      Redis
        ↓
Token Bucket State
        ↓
Allow / Reject
```

The initial implementation may use a token-bucket algorithm.

---

## FR-30: Rate-Limit Response

When a client exceeds its configured request allowance, the Gateway shall reject the request with:

```text
HTTP 429 — Too Many Requests
```

Where appropriate, the response may include retry guidance.

---

## FR-31: Administrative Rate-Limit Configuration

Administrators shall be able to modify applicable rate-limit policies without modifying application source code or rebuilding the Gateway.

Policy changes should become effective without requiring a Gateway restart.

---

## FR-32: Rate-Limit Failure Handling

The system shall define behavior when Redis or another rate-limiting dependency becomes unavailable.

The initial implementation shall use an explicitly defined failure policy rather than silently bypassing rate limiting.

---

# 15. Admin Requirements

The administrator shall have access to an administrative interface.

The initial dashboard should support:

* User management
* Content management
* Show management
* Venue management
* Booking monitoring
* Rate-limit configuration
* Traffic monitoring
* System statistics

Additional administrative functionality may be introduced later.

**Implementation status (Phase 13):** the requirements below give this
section concrete, numbered requirements for the first Admin Dashboard
increment. **All six are complete** — see the table below. "Rate-limit
configuration" above refers to the Phase 12 policy matrix, which stays
configuration-driven (`application.yml`/env vars) — Phase 13 adds
read-only *visibility* into it, not runtime editing; see FR-40.

| Requirement | Status | Notes |
|---|---|---|
| FR-35 Admin Dashboard Access Control | **Complete** | Enforced at the API Gateway (`/api/admin/** -> hasAuthority("ROLE_ADMIN")`). |
| FR-36 Admin Overview and Statistics | **Complete** | `GET /api/admin/users/stats`, `GET /api/admin/content/stats`, `GET /api/admin/bookings/stats` — one per owning service, combined client-side; frontend "Platform Overview" section implemented. |
| FR-37 Admin User Management | **Complete** | `GET /api/admin/users` (list), `PATCH /api/admin/users/{userId}/plan`, `PATCH /api/admin/users/{userId}/role` — including the self-role-modification restriction. Backend only; no frontend UI yet. |
| FR-38 Admin Event/Show Management | **Complete** | Full create/update/remove for Content, Shows (plus the dedicated `cancel`), and Venues. Backend only; no frontend UI yet. |
| FR-39 Admin Booking Management | **Complete** | `GET /api/admin/bookings` and `GET /api/admin/shows/{showId}/seat-activity`; the existing `GET /api/bookings/{bookingId}` provides a booking's details/status. Read-only, as specified — no admin cancel/confirm capability was added. Backend only; no frontend UI yet. |
| FR-40 Admin Rate-Limit Visibility | **Complete** | `GET /api/admin/rate-limits/stats` — a local Gateway endpoint (not proxied); policy matrix from `RateLimitPolicyProperties`, activity from a dedicated Redis namespace, since-Gateway-startup counters. Frontend "Traffic & Rate Limiting" section implemented. |

Frontend coverage exists only for FR-36 and FR-40 (the `/admin` page's
"Platform Overview" and "Traffic & Rate Limiting" sections) — FR-37/
FR-38/FR-39 remain backend API surface only, reached the same way as any
other endpoint (`docs/api-contracts.md`); no frontend UI was built for
them in Phase 13.

## FR-35: Admin Dashboard Access Control

The Admin Dashboard shall be an operational interface available only to
authenticated users whose JWT `role` claim is `ADMIN`.

Access control shall be enforced through the existing JWT
authentication/authorization architecture (§6.2–§6.3, §23–§24) — the same
gateway-level JWT validation and role claim every other protected endpoint
already relies on, not a separate mechanism.

A `CUSTOMER` user shall receive `403 Forbidden` for any admin-only
operation. An unauthenticated request shall receive `401 Unauthorized`,
consistent with FR-21.

---

## FR-36: Admin Overview and Statistics

The Admin Dashboard shall present summary operational statistics: at
minimum, counts of users, content/shows, and bookings, and a view of
current traffic/rate-limit activity (FR-40).

Each figure shall be sourced from the service that owns the underlying
data (see the ownership boundary in `docs/architecture.md` §45.3) — the
dashboard itself shall not maintain its own copy of business data.

---

## FR-37: Admin User Management

The Admin Dashboard shall allow an administrator to view and manage user
accounts: at minimum, list users, view a user's details, and change a
user's plan or role.

This capability is owned by User Service (users, plans — §7, §17).

---

## FR-38: Admin Event/Show Management

The Admin Dashboard shall allow an administrator to manage bookable
content: at minimum, create/update/remove Content, Shows, and Venues.

This capability is owned by Catalog Service (content, venues, seats,
shows — §8–§12, §17).

**Authorization (Phase 15 Step 4 follow-up).** Creating, updating and
deleting content, venues, seats and shows is administrator-only, including
through the non-admin `/api/catalog/**` paths (previously any authenticated
customer could). Enforced at the Gateway; reads remain open to any
authenticated user. See `docs/architecture.md` §25.5.

---

## FR-39: Admin Booking Management

The Admin Dashboard shall allow an administrator to view and monitor
bookings: at minimum, list bookings, view a booking's details and status,
and view seat-hold/booking activity for a show.

This capability is owned by Booking Service (bookings, booking_seats,
show_seats — §13–§16, §17).

---

## FR-40: Admin Rate-Limit Visibility

The Admin Dashboard shall allow an administrator to *view* the active
rate-limit policy matrix and, where available, recent rate-limit activity
(e.g. requests rejected with `429`) — sourced from Gateway Service
(§6.4, §19–§22, and the Phase 11/12 implementation).

The Admin Dashboard shall **not** allow editing rate-limit policies at
runtime in this phase. Policies remain configuration-driven, exactly as
Phase 12 implemented them (`application.yml`/environment variables, a
restart to change); FR-31 (administrative rate-limit configuration)
remains explicitly unimplemented and out of scope for Phase 13.

---

# 16. Monitoring Requirements

## FR-33: Application Metrics

The system shall expose metrics related to application behavior.

Potential metrics include:

* Total requests
* Requests per second
* Request latency
* HTTP errors
* Rate-limit allowed requests
* Rate-limit rejected requests
* Booking attempts
* Successful bookings
* Failed bookings
* Redis errors
* Database connection usage
* Service health
* Policy refresh status

---

## FR-34: Monitoring Dashboard

The system should provide dashboards for monitoring service behavior.

The planned monitoring stack is:

```text
Services
    ↓
Prometheus
    ↓
Grafana
```

**Implementation status (Phase 14).** FR-33/FR-34 are implemented as a
*local, manually-run* setup, not an operational/production deployment —
see `docs/architecture.md` §46 for the full account. Concretely: all six
backend services expose `GET /actuator/prometheus`
(`spring-boot-starter-actuator` + `micrometer-registry-prometheus`);
`monitoring/prometheus.yml` statically scrapes five of them
(`gateway-service`/`user-service`/`catalog-service`/`booking-service`/
`payment-service`); a provisioned Grafana dashboard
(`eventtick-service-overview.json`) reads from that Prometheus. Both
Prometheus and Grafana were installed and live-verified locally in Phase
14 Step 5 (real scrape targets, real panel queries) — but neither runs
continuously, neither is containerized, and this is not "monitoring is
operational" in a production sense: it is a local toolchain someone must
start by hand. **Gap, not yet closed:** `audit-service` (port 8085, added
Phase 16 Step 3) has the same Actuator/Micrometer setup as the other five
services but is **not** listed in `monitoring/prometheus.yml`'s static
targets — its metrics are real and reachable, just not scraped. No custom
business metric (bookings/payments/rate-limit counters as Prometheus
series) exists; the dashboard uses only Micrometer's built-in HTTP/JVM/
process metrics.

---

# 17. Logging Requirements

Services shall generate structured logs useful for debugging and operational monitoring.

Logs should contain relevant information such as:

* Timestamp
* Service name
* Request ID
* Route
* HTTP method
* HTTP status
* Processing duration
* Error information
* Rate-limit decision where applicable

Sensitive information must not be logged.

This includes:

* Passwords
* Access tokens
* Refresh tokens
* Payment credentials
* Secret keys

---

# 18. Asynchronous Communication Requirements

The system should support asynchronous communication for suitable workflows.

A message broker such as Apache Kafka may be introduced.

Potential events include:

```text
BookingCreated
BookingCancelled
PaymentCompleted
PaymentFailed
SeatHeld
SeatReleased
```

The exact event model will be defined during implementation.

Asynchronous messaging is not required for the initial Gateway implementation.

**The event model above is now defined — see §27 (Phase 16) below and
`docs/architecture.md` §47** — written once the domain model these events
describe actually existed, rather than at Phase 1 when it did not.

---

# 19. Data Science Requirements

A separate data-science/ML component may be introduced after the core transactional platform is functional.

The analytics layer may analyze:

* Booking trends
* Content popularity
* Traffic patterns
* Peak booking periods
* User behavior
* Demand patterns

Potential ML functionality:

* Demand forecasting
* Event demand prediction
* Recommendation systems
* Anomaly detection
* Traffic analysis

The ML service will be separated from the transactional backend.

ML functionality is an extension of the platform and is not required for the core booking or API Gateway functionality.

---

# 20. Non-Functional Requirements

## NFR-01: Scalability

The system should allow individual services to be scaled independently.

The Gateway and backend services should support multiple instances where required.

---

## NFR-02: Availability

Failure of one service should not unnecessarily bring down unrelated services.

Dependencies should have defined timeout and failure behavior.

---

## NFR-03: Performance

Frequently accessed operations such as rate-limit checks should have low latency.

Redis will be used for operations requiring fast in-memory access.

---

## NFR-04: Consistency

Booking operations must maintain consistent seat and booking states.

A seat must not be simultaneously confirmed for multiple users for the same show.

---

## NFR-05: Concurrency

The system must safely handle simultaneous requests involving:

* The same seat
* The same booking
* The same user
* High request volumes

---

## NFR-06: Security

The system shall:

* Hash passwords
* Validate JWTs
* Enforce authorization
* Validate user input
* Protect sensitive configuration
* Avoid exposing secrets
* Apply rate limiting
* Prevent direct unauthorized access to internal services

---

## NFR-07: Maintainability

Services should have clearly defined responsibilities and interfaces.

Business logic should remain separate from infrastructure concerns.

---

## NFR-08: Observability

The system should provide:

* Structured logs
* Metrics
* Health checks
* Request IDs
* Distributed request correlation

Distributed tracing may be introduced later.

---

## NFR-09: Extensibility

The architecture should allow additional content types, services, and features to be introduced without redesigning the entire platform.

Adding a new content category should not require a separate booking architecture.

---

## NFR-10: Fault Isolation

Failures in individual dependencies should have controlled effects.

The system should use appropriate:

* Timeouts
* Circuit breakers
* Controlled retries
* Failure responses

as the project evolves.

---

# 21. Business Rules

## BR-01

Only authenticated users can create bookings.

## BR-02

A seat can only be successfully booked once for a particular show.

## BR-03

Temporary seat holds expire after a configured period.

## BR-04

Expired holds return seats to the available state.

## BR-05

Rate limits are determined by configurable policies.

## BR-06

Administrators can modify rate-limit policies.

## BR-07

A user can access only their own booking information unless authorized as an administrator.

**Implemented (Phase 15 Step 4 follow-up).** Previously not enforced: any
authenticated customer could read, list and cancel another customer's
bookings. Now enforced in Booking Service from the caller's validated JWT:
a booking is readable by its owner or an administrator (another customer →
`403`; nonexistent → `404`), a customer's booking list contains only their
own (an administrator may list all), and only the owner may cancel (an
administrator may not — FR-39 is read-only). Payment-service reaches
Booking Service through a separate `/internal/**` surface that the Gateway
does not route. See `docs/architecture.md` §25.5.

## BR-08

A booking must pass the required payment state before being finalized when payment is enabled.

## BR-09

A rate-limit decision must use trusted identity information rather than an identity supplied directly by the client.

## BR-10

The API Gateway must not contain business rules such as seat ownership or booking confirmation logic.

## BR-11

Internal services should not rely on client-provided headers to establish user identity when trusted authentication information is already available.

---

# 22. Technology Requirements

The planned technology stack is:

| Component        | Technology            |
| ---------------- | --------------------- |
| Frontend         | React / Next.js       |
| Backend          | Java + Spring Boot    |
| API Gateway      | Spring Cloud Gateway  |
| Authentication   | Spring Security + JWT |
| Database         | PostgreSQL            |
| Fast state/cache | Redis                 |
| Messaging        | Kafka                 |
| Monitoring       | Prometheus + Grafana  |
| ML               | Python + Scikit-learn |
| ML API           | FastAPI               |
| Containerization | Docker                |
| Version Control  | Git + GitHub          |
| API Testing      | Postman               |

Kafka and the ML stack are optional extensions and will be introduced only after the core system is functional.

---

# 23. Project Constraints

The project will initially be developed as a learning and portfolio project.

Therefore:

* Payment can initially be simulated.
* Deployment can initially be local.
* Services will be introduced progressively.
* Production-grade infrastructure will be added incrementally.
* ML functionality will be introduced after transactional functionality is stable.
* Kafka will be introduced only when an asynchronous workflow requires it.
* Exact rate-limit values will be determined through testing rather than assumed production capacity.
* The initial deployment will not be considered production-ready merely because it uses Docker or microservices.

---

# 24. Development Phases

The project will follow this sequence:

```text
1. Requirements & Architecture
              ↓
2. Database Design + API Contracts
              ↓
3. Backend Project Structure
              ↓
4. Basic Frontend UI
              ↓
5. Authentication Backend
              ↓
6. Frontend ↔ Authentication
              ↓
7. API Gateway
              ↓
8. Catalog / Content / Show APIs
              ↓
9. Frontend ↔ APIs
              ↓
10. Booking + Seat Concurrency
              ↓
11. Redis Rate Limiting
              ↓
12. Dynamic Rate Limiting
              ↓
13. Admin Dashboard
              ↓
14. Monitoring
              ↓
15. Optional Payment Integration
              ↓
16. Optional Kafka / Events
              ↓
17. Data Science / ML
              ↓
18. Docker + Deployment
```

Each phase should be functional and understandable before introducing the next major architectural component.

---

# 25. Requirement Status

Completed, beyond the original Phase 1 scope below:

* Full PostgreSQL schema (`database/migrations/0001`–`0010`) — plans,
  users, content, venues, seats, shows, show-specific seat availability,
  bookings, and booking line items — with service ownership boundaries
  documented in `docs/architecture.md` §17.
* Backend project structure for all four services (`backend/`).
* Catalog Service — entities, repositories, services, DTOs, and REST
  controllers for Content/Venue/Seat/Show, all implemented and manually
  tested.
* Booking Service — entities, repositories, a concurrency-safe service
  layer (row-level locking on `show_seats`), DTOs, and REST controllers
  for seat holds and the booking lifecycle, all implemented and manually
  tested.
* User Service authentication backend — registration, login, JWT
  issuance/validation, Spring Security, password hashing (BCrypt),
  role-based authority (`CUSTOMER`/`ADMIN`), and an authenticated
  `/api/users/me`. See `docs/api-contracts.md` for the endpoint contract.
  Refresh tokens are explicitly deferred (see that doc for why).
* Eventtick frontend prototype. Authentication (signup, login, session
  restore, logout) is now wired to the real User Service; everything else
  in the frontend still uses mock data. *(Stale as of Phase 17 — retained
  as this list's original snapshot; by Phase 17 the core catalog/show/
  seat/booking/payment path is also real. See §28.)*

This happened in a different order than the roadmap in §24 originally
laid out (Catalog/Booking were built before Authentication, not after) —
noted here rather than silently rewriting §24 to look like it was planned
that way.

Original Phase 1 — Requirements & Architecture — completed:

* Project scope
* User roles
* Functional requirements
* Non-functional requirements
* Business rules
* Technology direction
* Gateway responsibilities
* Dynamic rate-limiting requirements
* Booking concurrency requirements
* Development roadmap
* GitHub repository setup
* Initial domain entities

The requirements have now been generalized from a movie-focused platform to a multi-category ticket-booking platform.

## Historical Note: Phase 1's "Next Phase"

At the time this section was written (end of Phase 1), the plan for what
came next was: build the API Gateway (Frontend ↔ Authentication
integration was already done — see above). That work is long since
complete, along with every phase after it through Phase 14 (Monitoring) —
see `docs/architecture.md` §45 for current Admin Dashboard status, §46 for
Monitoring, and §15 above for FR-35–FR-40. Phase 15 (Payment Integration,
§26), Phase 16 (Event-Driven Architecture, §27), and Phase 17 (Frontend
Integration, §28) are also complete to the extent each section states —
see those sections for exactly which requirements are covered and which
remain deferred.

---

# 26. Payment Requirements — Phase 15 (Step 1: Designed; Step 2: Implemented)

**Status: FR-41–FR-50 are implemented** (a real
`payment-service`, `database/migrations/0011_create_payments_table` and
`0012_add_booking_sync_status_to_payments`, the three API endpoints, and the
Gateway route all exist and are tested). Step 3 added FR-45 (expiration),
FR-45a (reconciliation), and FR-45b (state machine). Still deferred: a
customer-facing abandon-checkout action, a real provider/webhooks, and a
frontend payment UI.
This section adds detailed requirements on top of the original §12 (FR-18)
and §21 (BR-08), which are left exactly as originally written — this
section doesn't replace them, it fills in the detail they always deferred
("payment may be simulated... possible states include...").

Full architectural reasoning (service boundary, state machine, consistency
strategy, idempotency, security, data model) is in `docs/architecture.md`
§25.1 (design) and §25.2 (implementation); the API contract is in
`docs/api-contracts.md`'s Payment API section. This section states the
requirements those documents satisfy.

## FR-41: Payment Creation — IMPLEMENTED

The system shall let a customer initiate a payment for exactly one booking
they own. The charged amount shall be computed server-side from that
booking's own `total_amount` (already stored, per FR-13) — never accepted
as a client-supplied value, so a client cannot alter what it is charged.

`POST /api/payments` (`PaymentController`/`PaymentService`): `amount` is not
even a field `CreatePaymentRequest` accepts; the amount always comes from
calling `booking-service`'s own `GET /api/bookings/{id}` (`BookingServiceClient`).

## FR-42: Payment Status — IMPLEMENTED

The system shall expose a payment's current lifecycle status, retrievable
independently of the original creation request (so a client that lost the
original response — timeout, refresh, different device — can still learn
the outcome).

`GET /api/payments/{paymentId}` and `GET /api/payments?bookingId=`.

## FR-43: Payment Success Handling — IMPLEMENTED

On a successful payment, the system shall transition the associated
booking out of `PENDING` (FR-13/FR-18) into `CONFIRMED`, using the booking
lifecycle's existing confirmation mechanism (§14) rather than a new,
parallel one.

`BookingServiceClient.confirmBooking` calls booking-service's internal
`POST /internal/bookings/{id}/confirm` (Gateway-unroutable). Confirmation is
**payment-driven only**: a customer cannot confirm a booking, and neither can
an administrator (FR-39 is read-only) — the former public
`POST /api/bookings/{id}/confirm`, which let any signed-in customer confirm an
unpaid booking, was removed (Phase 15 Step 4). It now answers `403` for any
valid token and `401` otherwise. A payment reaching `SUCCESS` is therefore
required for a booking to become `CONFIRMED`. See `docs/architecture.md`
§25.5.

## FR-44: Payment Failure Handling — IMPLEMENTED (interim mechanism)

On a failed, cancelled, or expired payment, the system shall release the
booking's held seats and move the booking to a terminal non-`CONFIRMED`
state, consistent with BR-08 ("a booking must pass the required payment
state before being finalized when payment is enabled").

Implemented for the `FAILED` and (Step 3) `EXPIRED` transitions (`CANCELLED`
has no customer-facing trigger yet). `BookingServiceClient.releaseBooking`
reuses booking-service's existing `POST /api/bookings/{id}/cancel` as the
Step 1 design's documented **interim** option — booking-service has no
dedicated "fail" transition yet (`BookingStatus.FAILED` remains reachable
in the data model but unused by any code path), so a payment failure
currently lands the booking in `CANCELLED`, not `FAILED`. See
`docs/architecture.md` §25.2 for why this was kept as the smallest
explicit change rather than modifying booking-service in this step.

## FR-45: Payment Expiration — IMPLEMENTED (Phase 15 Step 3)

A payment left in an in-flight state for longer than a configured timeout
shall be automatically treated as expired, independent of whether the
underlying booking itself has any seat-hold expiry mechanism (which does
not exist yet — see `docs/architecture.md` §16).

Implemented: a scheduled sweep moves payments `PENDING` longer than
`payment.expiration-minutes` (default 15) to `EXPIRED` via a conditional
database update, then releases the booking. Idempotent and safe across
repeated runs and multiple instances. See `docs/architecture.md` §25.3.

## FR-45a: Payment/Booking Reconciliation — IMPLEMENTED (Phase 15 Step 3)

If a payment reaches `SUCCESS`, `FAILED`, or `EXPIRED` but booking-service
does not acknowledge the corresponding confirm/cancel call, the payment
status shall remain unchanged and the call shall be retried automatically
until acknowledged, without creating duplicate bookings or payments and
without re-invoking the payment provider. Backed by
`payments.booking_sync_status` and a scheduled reconciliation sweep; retries
are safe because booking-service's confirm/cancel are idempotent on an
already-`CONFIRMED`/`CANCELLED` booking. Internal only — no public endpoint.

## FR-45b: Payment State Machine — IMPLEMENTED (Phase 15 Step 3)

Payment status transitions shall be validated in one place. Allowed:
`CREATED -> PENDING | CANCELLED`; `PENDING -> SUCCESS | FAILED | EXPIRED |
CANCELLED`. `SUCCESS`, `FAILED`, `EXPIRED`, `CANCELLED` are terminal.
`CREATED -> CANCELLED` is valid in the state machine, but no customer-facing
action triggers it yet (deferred).

## FR-46: Idempotent Payment Creation — IMPLEMENTED

The system shall accept a client-supplied idempotency key on payment
creation. A repeated request with the same key and the same booking shall
return the existing payment record rather than creating a duplicate or
re-charging. A repeated request with the same key but different booking
data shall be rejected as a conflict, not silently processed.

Phase 15 Step 4 (live test): this also holds for requests submitted
*concurrently* with the same key — they all return the one payment (the
first `201`, the rest `200` showing whatever status it has reached, possibly
`CREATED`/`PENDING`); an earlier version returned `409` to the losers.

Application-level pre-check (`PaymentService.createPayment`) plus a real
database `UNIQUE` constraint (`uq_payments_idempotency_key`) as the
race-condition-safe backstop, with graceful `DataIntegrityViolationException`
handling — proven by a real-H2 test (`PaymentRepositoryConstraintTest`),
not just mocked.

## FR-47: Payment/Booking Association — IMPLEMENTED

Every payment shall reference exactly one booking. At most one payment for
a given booking may be in a non-terminal state at any time — a booking
must not have two simultaneously "live" payment attempts.

`payments.booking_id` (real FK to `bookings.id`) plus
`uq_payments_one_active_per_booking`, a partial `UNIQUE INDEX` on
`(booking_id) WHERE status IN ('CREATED','PENDING','SUCCESS')` — the real,
database-level enforcement. **Known test gap:** H2 2.2.224 (this project's
test database) does not support partial/filtered unique indexes (confirmed
by direct experimentation) — only the real PostgreSQL migration creates
this specific constraint; H2-backed tests instead prove the equivalent
application-level guard (`PaymentServiceTest`'s duplicate-payment tests).

## FR-48: Payment Authorization — IMPLEMENTED

Payment endpoints shall require the same JWT authentication already
required by every other non-public Eventtick endpoint (FR-21/FR-22). A
customer shall be able to create a payment only for a booking they own, and
shall be able to view only their own payment records, mirroring the
existing booking-ownership rule (BR-07). An administrator shall be able to
view (read-only) any payment record.

`payment-service` independently validates the same JWTs user-service
issues (its own `SecurityConfig`/`JwtAuthenticationFilter`/`JwtService`,
mirroring user-service's own defense-in-depth pattern) and derives the
caller's identity from the validated token — never a client-supplied
field, unlike booking-service's own pre-existing, documented gap (see
`docs/architecture.md` §25.1).

## FR-49: Amount and Currency Representation — IMPLEMENTED

Payment amounts shall never be represented using a binary floating-point
type. Currency shall be an explicit, stored property of a payment (the
existing schema has no currency column anywhere, since every existing
monetary field assumes a single implicit currency).

`payments.amount NUMERIC(10,2)` (`BigDecimal` at the JPA level, matching
`bookings.total_amount` exactly) and `payments.currency CHAR(3)`
(configurable via `payment.default-currency`, defaulting to `INR` as a
placeholder, not a stated business decision — see §25.2's open question).

## FR-50: Provider Abstraction — IMPLEMENTED

The system shall not couple the payment domain to one specific payment
provider's API shape. An initial mock provider shall satisfy every
requirement above without any real payment-provider account, credentials,
or network dependency.

`PaymentProvider` interface + `MockPaymentProvider` (no network call, no
credentials, deterministic). No real provider (Stripe/Razorpay/etc.) was
integrated.

---

# 27. Event-Driven Architecture Requirements — Phase 16

**Status: Step 1 design complete; Step 2 implements Kafka infrastructure,
the transactional outbox, and one real producer (`BookingCreated`); Step 3
adds the first real consumer (`audit-service`/`BookingCreatedConsumer`);
Step 4 adds a second producer (`PaymentSucceeded`, payment-service's own
outbox) and a second consumer (`PaymentSucceededConsumer`, still inside
audit-service).** Full reasoning is in `docs/architecture.md` §47 (design),
§48 (Step 2), §49 (Step 3), and §50 (Step 4); this section states the
requirements-level status only.

## FR-51: Domain Event Publication — PARTIALLY IMPLEMENTED

The system may publish domain events for state changes that other
components can usefully react to asynchronously, without any producing
service being coupled to who consumes its events. Each event shall be
published by the service that owns the underlying state (BR-11) — never
a separate event-owning service.

**Implemented:** `BookingCreated`, published by booking-service via its
transactional outbox (`docs/architecture.md` §48.7) on every
`POST /api/bookings`. `PaymentSucceeded` (Phase 16 Step 4), published by
payment-service via its own transactional outbox (`docs/architecture.md`
§50.2–§50.4) on every payment that reaches `SUCCESS`. `PaymentFailed`
and `PaymentExpired` (Phase 16 Step 5), published by payment-service via
the same outbox on every payment that reaches `FAILED` (provider
rejection) or `EXPIRED` (expiration sweep), respectively.

Catalogued and classified as strong candidates, remaining
**NOT IMPLEMENTED** (architecture.md §47.2): `BookingCancelled`,
`BookingConfirmed` (booking-service); `PaymentCreated`
(payment-service); `ShowCreated`, `ShowCancelled`
(catalog-service); `UserRegistered`, `UserRoleChanged` (user-service).
Explicitly deferred, with reasoning, rather than assumed: `SeatHeld`/
`SeatReleased` (holds have no recorded ownership yet — §16), `SeatBooked`
(folded into `BookingConfirmed`'s payload), most catalog CRUD events, and
`UserPlanChanged` (no consumer needs it — the rate limiter already reads
plan from the JWT on every request).

## FR-52: Payment/Booking Synchronous Boundary Preserved — IMPLEMENTED (unaffected)

Introducing event publication shall not weaken FR-43/FR-45a's existing
guarantees. A payment reaching `SUCCESS` shall still be durably committed
before the internal booking-confirmation call, and that call shall remain
the only mechanism that confirms a booking.

Verified by omission for Phase 16 Step 2, and now proven directly for
Phase 16 Step 4: `PaymentService`'s own entry points, `PaymentStatus`,
`BookingSyncStatus`, `PaymentLifecycleScheduler`, and
`BookingServiceClient` were not modified in either step.
`PaymentSucceeded` (added in Step 4) is exactly the independent,
best-effort broadcast this requirement anticipated — published by a
separate collaborator (`PaymentSuccessRecorder`) strictly *after*
`PaymentService.chargeAndResolve` has validated the `SUCCESS` transition,
and strictly *before* (not instead of, not gating) the existing
`bookingServiceClient.confirmBooking` call. Live-proven (docs/
architecture.md §50.8, Step 12): with Kafka entirely unreachable, a
payment still reaches `SUCCESS` and its booking still becomes `CONFIRMED`
via the unchanged synchronous path; the outbox row simply stays `PENDING`
until Kafka recovers. Never a second reconciliation path alongside
`booking_sync_status` — `PaymentSuccessRecorder` has no dependency on
`BookingServiceClient` or `BookingSyncStatus` at all.

## FR-53: Idempotent Event Consumption — IMPLEMENTED

Event delivery shall be treated as at-least-once, not exactly-once (no
distributed-transaction infrastructure exists to claim otherwise). Every
event carries a unique `eventId`, generated by `OutboxService` and reused
as `booking_outbox_events.event_id`'s own primary key — a duplicate publish
(e.g. after a crash between a Kafka ack and the row being marked
`PUBLISHED`) reuses that same id, which is what a consumer dedupes on.

Implemented on the consumer side (Phase 16 Step 3): `audit-service`'s
`BookingCreatedConsumer`/`BookingEventAuditService` enforce this at the
database level — `booking_event_audit.event_id` is the primary key, and a
duplicate delivery is recognized by the insert failing that constraint,
never by an application-level existence check alone (which would race
under concurrent redelivery). Proven live: the same event delivered twice
produces exactly one row. See `docs/architecture.md` §49.5.

## FR-54: First Kafka Consumer — IMPLEMENTED (`BookingCreated` only)

The system shall demonstrate the full event-driven chain — producer,
transactional outbox, Kafka, consumer, consumer-side persistence,
idempotent processing — with at least one real consumer, without making
any existing synchronous flow depend on it.

Implemented: `audit-service` (a new, dedicated service — port 8085, no
Gateway route, no domain state beyond its own `booking_event_audit`
projection) consumes `BookingCreated` from `eventtick.booking` under a
fixed consumer group (`eventtick-booking-audit`) and persists an audit row
per successfully processed event. Not implemented: consumption of any
other event type; any consumer for `eventtick.payment`/`eventtick.catalog`/
`eventtick.user` (none of those topics have a producer yet either). See
`docs/architecture.md` §49.

## FR-55: Payment Outbox — IMPLEMENTED

payment-service shall own its own transactional outbox, structurally
identical in design to booking-service's, but a physically separate table
— each service's persistence boundary stays independent (BR-11).

Implemented: `payment_outbox_events` (migration 0015), `PaymentOutboxEvent`/
`PaymentOutboxEventRepository`/`PaymentOutboxService`/
`PaymentOutboxPublisher` — payment-service's own copies of booking-
service's equivalent classes, not a shared module (same §49.1 reasoning).
The `SUCCESS` status write and the outbox row commit atomically, in one
database transaction, inside `PaymentSuccessRecorder#recordSuccess` — not
inside `PaymentService` itself, which cannot safely carry
`@Transactional` here (see architecture.md §50.4 for why). Extended in
Phase 16 Step 5: `recordFailure` and `recordExpiryIfStillPending` follow
the same pattern — each writes the terminal status and its outbox row
(`PaymentFailed` / `PaymentExpired`) atomically, inside
`PaymentSuccessRecorder` for the same proxy-invocation reason.
Live-proven (Step 16 of the kickoff / §50.8): a forced `NOT NULL`
violation on the payment write rolls back the outbox row in the same
transaction; a payment reaching `SUCCESS` while Kafka is unreachable
leaves the outbox row `PENDING`, never blocks `SUCCESS`, and is published
automatically once Kafka recovers.

## FR-56: Payment Audit Consumer — IMPLEMENTED

audit-service (not a new service) shall gain a second Kafka consumer, for
payment events on `eventtick.payment`, under its own fixed, restart-
stable, distinct consumer group — proving the event-driven chain extends
to a second producer/consumer pair without duplicating infrastructure or
conflating the two topics' independent consumption progress.

Implemented: `PaymentSucceededConsumer`/`PaymentEventAuditService` persist
one row per successfully consumed event into `payment_event_audit`
(migration 0016), idempotent at the database level exactly like
`BookingCreatedConsumer` (FR-53), under consumer group
`eventtick-payment-audit` (distinct from `eventtick-booking-audit`).
Live-proven: a real payment SUCCESS traced end to end through the outbox,
Kafka, and this consumer, with every field verified against the
authoritative `payments` row via direct PostgreSQL queries; the same
event redelivered produces exactly one row; the real audit-service
process was killed and restarted mid-flow with no reprocessing and no
interruption to normal consumption afterward. Extended in Phase 16 Step 5:
the same consumer now handles `PaymentFailed` and `PaymentExpired` in
addition to `PaymentSucceeded` — one `@KafkaListener` dispatching by
event type, not three competing listeners (same topic, same consumer
group, same partition assignment). No schema migration needed:
`payment_event_audit.event_type` already stores the type as a string
column. **Not implemented** (remaining scope boundary):
`BookingConfirmed`, `BookingCancelled`, any catalog/user event, and any
DLQ — see architecture.md §50.9.

## BR-11

The service that owns a piece of domain state is the only service that
may publish events about it. No central "event service" holds or
originates domain state.

Implemented for booking-service's own outbox
(`database/migrations/0013_create_booking_outbox_events_table`) and now
payment-service's own (`database/migrations/0015_create_payment_outbox_events_table`,
Phase 16 Step 4): each table is prefixed by its owning service
(`booking_`/`payment_`), specifically so they never collide under the one
shared physical database this project currently uses — exactly the
collision this rule anticipated, now actually avoided by a second real
outbox table, not just a naming convention nothing yet tested.

## NFR: Transactional Outbox — IMPLEMENTED (booking-service, payment-service)

A service that publishes events shall do so via the Transactional Outbox
Pattern (write the state change and an outbox row in one database
transaction; a separate poller publishes and retries independently) — not
a direct publish call from within the business transaction, which cannot
make the database write and the Kafka publish atomic.

Implemented for booking-service: `OutboxService.record(...)`, called from
inside `BookingService.createBooking`'s own `@Transactional` method,
writes the outbox row via a plain, already-transactional repository
`save(...)` — no explicit transaction coordination code. `OutboxPublisher`
is the separate poller. Proven, not just built: a failed `createBooking`
call leaves neither the booking nor its outbox row; a successful one
leaves both; an unreachable broker never affects booking creation. See
`docs/architecture.md` §48.5–§48.6.

Implemented for payment-service (Phase 16 Step 4, FR-55): the same
pattern, via a dedicated `PaymentSuccessRecorder` bean rather than inside
`PaymentService` directly (architecture.md §50.4 explains the proxy-based
`@Transactional`/self-invocation reason this had to be a separate bean).
Not yet implemented for catalog-service or user-service — neither has an
outbox table of its own.

---

# 28. Frontend Integration Requirements — Phase 17

**Status: FR-57–FR-59 are implemented.** The Eventtick React frontend's
core catalog-browse-to-confirmed-booking path now calls real backend
services through the Gateway end to end, replacing the mock data this
document's earlier sections (§4 Client Layer, requirement status notes
elsewhere) described as the frontend's only state for most of the
project. This section states which parts of the frontend are real, which
remain mock, and the concrete gaps that remain between the catalog domain
(content/venues/shows) and the booking domain (seat inventory). Full
reasoning is in `docs/architecture.md` §51.

## FR-57: Real Catalog/Show/Venue Frontend Integration — IMPLEMENTED

The frontend shall retrieve content, venue, and show information from
Catalog Service (FR-04–FR-06) through the Gateway, rather than from
static mock data.

`eventtick/src/services/api.ts`'s `getEvents`, `getEventById`,
`getVenueById`, `getVenuesByCity`, `getShowsByEvent`, `getShowById` call
the real `GET /api/catalog/content(/{id})`, `GET /api/catalog/venues(/{id})`,
`GET /api/catalog/shows(/{id})` endpoints. Catalog Service's list
endpoints take no query parameters at all (no `type`/`contentId`/`city`
filter, no pagination, no search) — `type`/`contentId`/`city` filtering is
done client-side against the full list, the closest supported behavior
rather than an invented backend capability. `getTrendingEvents`/
`getFeaturedEvents` remain mock: Catalog Service's `Content` entity has no
"trending"/"featured" concept to source them from, and none was added.
Real `Content` also has no image/rating/cast/price/city columns, and real
`Venue` has no state/pincode — the frontend type for `Venue.state`/
`pincode` was changed from required to optional to reflect this; the UI
falls back to a generic placeholder image rather than inventing per-event
artwork.

## FR-58: Real Booking Creation Frontend Integration — IMPLEMENTED

The frontend shall create real bookings (FR-13) through Booking Service,
using show-seats the same user actually holds, rather than simulating a
booking locally.

`eventtick/src/services/api.ts`'s `createBooking`/`getBooking` call the
real `POST /api/bookings`/`GET /api/bookings/{id}`; `getSeatMap`/
`holdSeats` (an earlier integration) call the real
`GET /api/bookings/shows/{showId}/seats`/
`POST /api/bookings/shows/{showId}/seats/hold`. The authenticated user's
id comes from `AppContext`'s real session (`GET /api/users/me`), never
hardcoded. `BookingSummary.tsx` sequences hold → create booking exactly
once per checkout attempt, with the existing loading/disabled-button guard
preventing a duplicate submit.

## FR-59: Real Payment Frontend Integration — IMPLEMENTED

The frontend shall create real payments (FR-41) for a real, already-created
booking, handle the payment-service response honestly, and reflect the
booking's actual post-payment status rather than assuming success.

`createPayment` calls the real `POST /api/payments` with exactly
`{bookingId, idempotencyKey}` — never a client-supplied amount/currency/
userId/provider, matching FR-41/FR-49/FR-50 exactly. The idempotency key
is derived deterministically from the real booking id
(`booking-${bookingId}-payment`), not randomly generated per render, so a
re-render/remount/retry of the same checkout attempt reuses the same key
rather than risking a duplicate charge (FR-46). After a `SUCCESS`
response, the frontend re-fetches the booking (`getBooking`) up to three
times with a bounded 1-second gap — never an unbounded/infinite loop —
to observe the booking's real status, because **payment confirmation is a
synchronous call from payment-service to booking-service that completes
*before* the `POST /api/payments` response returns** (FR-43; not
Kafka-driven, see FR-52), and that synchronous call can rarely still be
settling via the reconciliation sweep (FR-45a) when the HTTP response
arrives. The frontend never marks a booking `CONFIRMED` locally; it only
ever displays the status Booking Service actually returns.

## Frontend Integration — Remaining Mock Areas (explicit, not hidden)

Not covered by FR-57–FR-59, and still mock data as of this phase:
`getTrendingEvents`/`getFeaturedEvents` (§28 FR-57, no backend concept),
`getBookings`/`getBookingById`/`cancelBooking` (booking history/
cancellation UI), `getPlans`/`subscribeToPlan` (FR-37's plan data has a
real backend — user-service — but the frontend Plans page does not call
it), `getAdminStats`/`getRateLimitPolicies` (superseded in part by the
real `getAdminOverviewStats`/`getRateLimitStats` — see §15 FR-36/FR-40 —
but the originals remain for the dashboard sections not yet reached), and
`searchEvents` (Catalog Service has no search endpoint at all — FR-09
remains unimplemented on the backend, so the frontend cannot be wired to
it without inventing one).

## Known Gap: `show_seats` Inventory Creation — Partially Closed (Phase 18, §29)

Booking Service owns `show_seats` (§17); until Phase 18, **no API
anywhere created a `show_seats` row** — `BookingController` only read
(`GET .../shows/{showId}/seats`), held (`POST .../hold`), and released
(`POST .../release`) seats that already existed; `AdminShowSeatActivityController`
was (and remains) read-only. Phase 18 (§29) added
`POST /api/admin/shows/{showId}/seats`, an admin-only endpoint that
creates one or more `show_seats` rows for an existing show. **What this
does not close:** creating a `Show` through Catalog Service's real
`POST /api/catalog/shows` still does not automatically create its
inventory — an administrator must call the new endpoint separately, per
show, so a `Show` reachable through `GET /api/catalog/shows/{id}`
(FR-57) is still not *guaranteed* to be bookable, only *able to be made*
bookable without a manual SQL insert. Automatic projection (an
event-driven consumer reacting to a future `ShowCreated` event, or
equivalent) remains unimplemented, unscheduled, future work.

---

# 29. Show-Seat Inventory Requirements — Phase 18

**Status: FR-60 is implemented.** Full design/architecture reasoning is in
`docs/architecture.md` §52; this section states the requirement it
satisfies.

## FR-60: Show-Seat Inventory Creation — IMPLEMENTED

The system shall let an administrator create `show_seats` inventory
(FR-07/FR-11) for an existing show, so a show is not limited to seats
created by a manual database insert. Inventory creation shall:

* be restricted to an authenticated administrator — a `CUSTOMER` token
  shall receive `403`, no token shall receive `401`;
* reject a request that would create a seat already configured for that
  show, and reject a request that names the same seat twice, in both
  cases without creating any row from that request;
* assign every newly created seat the system's existing initial
  `AVAILABLE` state (FR-11) — never a caller-supplied status.

`POST /api/admin/shows/{showId}/seats` (`ShowSeatInventoryService`/
`AdminShowSeatInventoryController`, booking-service), gated by the
existing `/api/admin/** -> ROLE_ADMIN` rule already enforced at both the
Gateway and booking-service's own `SecurityConfig` (no new security
mechanism was introduced). Duplicate protection is an application-level
pre-check against `show_seats`, backed by the pre-existing
`uq_show_seats_show_seat` database constraint (migration 0008 — no new
migration was needed). Does not validate `showId`/`seatId` against
Catalog Service: see `docs/architecture.md` §52.2 for why that mirrors
this codebase's own existing precedent rather than introducing a new
cross-service dependency.

**Explicitly not covered by FR-60:** automatic inventory creation when a
Catalog Service `Show` is created (the Known Gap note above, and §17's
own note, remain partially open — an administrator must still call this
endpoint separately per show).
