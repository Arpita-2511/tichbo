# Tichboo — System Architecture

## 1. Architecture Overview

Tichboo is a ticket-booking platform protected by a production-style API Gateway.

The platform is designed to support multiple categories of ticketable experiences rather than being restricted to movies.

Examples include:

* Movies
* Sports matches
* Concerts
* Theatre
* Other events

The primary engineering focus of the project is the **API Gateway and its traffic-management capabilities**.

The Gateway provides:

* Request routing
* Authentication
* Authorization
* Dynamic rate limiting
* Request identification
* Traffic control
* Timeout handling
* Observability

The application layer provides realistic traffic and business operations for the infrastructure to protect.

### High-Level Architecture

```text
                              ┌───────────────────┐
                              │      Client       │
                              │   Web Frontend    │
                              └─────────┬─────────┘
                                        │
                                        ▼
                              ┌───────────────────┐
                              │    API Gateway    │
                              │                   │
                              │ Routing           │
                              │ Authentication    │
                              │ Authorization     │
                              │ Rate Limiting     │
                              │ Dynamic Policies  │
                              │ Request IDs        │
                              │ Timeouts           │
                              │ Observability      │
                              └─────────┬─────────┘
                                        │
           ┌───────────┬───────────────┼───────────────┬───────────┐
           │           │               │               │           │
           ▼           ▼               ▼               ▼           ▼
     ┌──────────┐ ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────┐
     │   User   │ │ Catalog  │  │ Booking  │  │ Payment  │  │   Audit   │
     │ Service  │ │ Service  │  │ Service  │  │ Service  │  │  Service  │
     └────┬─────┘ └────┬─────┘  └────┬─────┘  └────┬─────┘  └─────┬─────┘
          │            │             │             │              │
          └────────────┴──────┬──────┴──────┬──────┘              │
                               │             │                     │
                        ┌──────┴──────┐      │          (Kafka, consumer only)
                        │ PostgreSQL  │      │                     │
                        │ (shared     │      │                     │
                        │ instance,   │      │                     │
                        │ per-service │      │                     │
                        │ ownership)  │      │                     │
                        └─────────────┘      │                     │
                                              ▼                     │
                                       ┌─────────────┐              │
                                       │    Redis    │              │
                                       │ Rate Limit  │              │
                                       │ state only  │              │
                                       └─────────────┘              │
                                                                     │
                                       ┌─────────────┐              │
                                       │    Kafka    │◄─────────────┘
                                       │ (booking +  │
                                       │  payment    │
                                       │  outboxes)  │
                                       └─────────────┘
```

**As actually implemented** (see §44, §51): six services, not three —
`gateway-service` (8080), `user-service` (8081), `catalog-service` (8082),
`booking-service` (8083), `payment-service` (8084), and `audit-service`
(8085, Kafka-consumer-only — no Gateway route, no synchronous callers).
Redis is used **only** for Gateway rate-limit state — not for seat holds
(§16) or any other cache; "Seat Holds" in an earlier revision of this
diagram described a design that was never built this way. Kafka carries
two producers (`booking-service`'s `BookingCreated`, `payment-service`'s
`PaymentSucceeded`) into `audit-service`'s two consumers — an audit/
observability side-channel, not on the path of any synchronous request
(§28, §47–§50). Prometheus/Grafana exist as a locally-run, manually-started
monitoring stack (§46) — present and locally verified, not a continuously
running production deployment. No Notification Service and no ML Service
exist in any form.

---

# 2. Architectural Objectives

The architecture is designed to achieve:

* Separation of concerns
* Clear service ownership
* Centralized request management
* Dynamic traffic control
* Secure authentication
* Authorization
* Concurrency-safe booking
* Independent service scalability
* Fault isolation
* Observability
* Maintainable database boundaries
* Future analytics and ML integration

The main engineering objective is:

> Build an API Gateway capable of authenticating, routing, and dynamically controlling traffic to a distributed ticket-booking platform.

---

# 3. Application Domain

Tichboo is not restricted to a single type of ticket.

The platform uses a generic concept called **Content**.

Content represents something that users can attend or book.

Examples:

```text
Content
│
├── Movie
├── Sports Match
├── Concert
├── Theatre Event
└── Other Event
```

This avoids creating separate booking architectures for movies, matches, concerts, and events.

For example:

```text
Movie
   │
   ▼
Show
   │
   ▼
Venue
   │
   ▼
Seats
```

and:

```text
Sports Match
   │
   ▼
Show
   │
   ▼
Venue
   │
   ▼
Seats
```

Both use the same booking infrastructure.

---

# 4. Client Layer

The client is the user-facing web application.

Planned frontend technology:

* React or Next.js
* TypeScript
* Tailwind CSS

The frontend provides:

* Registration
* Login
* Home
* Search
* Browse movies/events/sports
* Content details
* Show selection
* Seat selection
* Booking
* Booking history
* User profile
* Subscription information
* Admin dashboard

The frontend communicates with backend services only through the API Gateway.

It should not directly access internal microservices.

```text
Frontend
    │
    ▼
API Gateway
    │
    ├── User Service
    ├── Catalog Service
    └── Booking Service
```

---

# 5. API Gateway

The API Gateway is the central entry point into the backend.

Planned implementation:

**Spring Cloud Gateway**

The Gateway provides cross-cutting infrastructure functionality.

```text
                         Client
                           │
                           ▼
                  ┌─────────────────┐
                  │   API Gateway   │
                  └────────┬────────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
          ▼                ▼                ▼
    User Service     Catalog Service   Booking Service
```

The Gateway should be the only publicly exposed backend entry point in the initial architecture.

---

# 6. Gateway Responsibilities

## 6.1 Request Routing

The Gateway routes requests to the appropriate service.

Conceptually:

```text
/api/users/**        → User Service

/api/auth/**         → User Service

/api/content/**      → Catalog Service

/api/venues/**       → Catalog Service

/api/shows/**        → Catalog Service

/api/bookings/**     → Booking Service

/api/admin/**        → Appropriate administrative service
```

The exact routes will be finalized during API implementation.

---

## 6.2 Authentication

The Gateway validates authentication credentials before forwarding protected requests.

```text
Request
   │
   ▼
Gateway
   │
   ▼
JWT Validation
   │
   ├── Invalid → Reject
   │
   └── Valid
          │
          ▼
    Forward Request
```

Authentication establishes the identity of the caller.

Business services remain responsible for resource-level authorization where required.

---

## 6.3 Authorization

Authorization determines whether an authenticated user is allowed to perform an operation.

For example:

```text
CUSTOMER
   │
   ├── Browse content
   ├── Search
   ├── View shows
   ├── Book tickets
   └── View own bookings


ADMIN
   │
   ├── Manage content
   ├── Manage shows
   ├── Monitor bookings
   └── Manage rate-limit policies
```

The Gateway can perform coarse-grained authorization, while individual services enforce business-specific authorization.

---

## 6.4 Rate Limiting

The Gateway performs rate-limit checks before forwarding requests.

```text
Request
   │
   ▼
Gateway
   │
   ▼
Rate-Limit Check
   │
   ├── Allowed ──→ Backend Service
   │
   └── Exceeded ─→ HTTP 429
```

The rate limiter protects downstream services from excessive traffic.

---

## 6.5 Dynamic Traffic Policies

Rate limits are not permanently hard-coded into the Gateway.

Policies can be changed at runtime.

Conceptually:

```text
Policy Storage
      │
      ▼
Gateway Policy Snapshot
      │
      ▼
Rate Limiter
      │
      ▼
Redis
```

This allows traffic policies to change without rebuilding or restarting the Gateway.

---

## 6.6 Request ID

Each request receives a unique request identifier.

Example:

```text
Client
   │
   │ Request ID: ABC123
   ▼
Gateway
   │
   │ ABC123
   ▼
Booking Service
```

The request ID is propagated across services where practical.

This allows a request to be correlated across logs.

---

## 6.7 Request Logging

The Gateway records useful operational information such as:

* Request ID
* Route
* HTTP method
* Status code
* Request duration
* Rate-limit decision
* Service response

Sensitive information such as passwords, access tokens, and refresh tokens must not be logged.

---

## 6.8 Timeouts and Failure Handling

The Gateway should enforce reasonable upstream timeouts.

Conceptually:

```text
Client
   │
   ▼
Gateway
   │
   ▼
Backend Service
   │
   ├── Responds → Return response
   │
   └── Too slow → Timeout
```

Circuit breakers and carefully controlled retries can be introduced later.

Retries must be used carefully for booking and payment operations because repeated execution can create duplicate operations.

---

# 7. User Service

The User Service owns user and account functionality.

Responsibilities:

* Registration
* Login
* Authentication
* User profile
* User roles
* Subscription plans
* Account management
* Refresh-token management

Conceptual user data:

```text
User
----------------
id
name
email
password_hash
role
plan_id
created_at
updated_at
```

The User Service owns user data.

Other services should not directly query the User Service database.

---

# 8. Catalog Service

The Catalog Service manages ticketable content and scheduling information.

The service is intentionally generic so that the platform is not restricted to movies.

Supported content types can include:

```text
MOVIE
SPORTS_MATCH
CONCERT
THEATRE
EVENT
```

Responsibilities:

* Content information
* Content categories
* Venue information
* Show schedules
* Seat-layout information
* Search
* Content availability information

Conceptually:

```text
Content
   │
   ▼
Show
   │
   ▼
Venue
   │
   ▼
Seat Layout
```

---

# 9. Content Model

Instead of creating separate booking structures for Movie, Event, and Match, Tichboo uses a generic Content entity.

Conceptually:

```text
Content
--------------------------------
id
type
title
description
language
duration
genre
release_or_event_date
metadata
created_at
updated_at
```

The `type` field identifies the category.

Example:

```text
Content Type

MOVIE
SPORTS_MATCH
CONCERT
THEATRE
EVENT
```

Category-specific information can be represented using appropriate fields or related tables as the system evolves.

This design allows new categories to be added without redesigning the complete booking architecture.

---

# 10. Show

A Show represents a specific scheduled occurrence of Content.

Conceptually:

```text
Content
   │
   │ 1
   ▼
Many Shows
   │
   ▼
Venue
```

Example:

```text
Content:
Avengers

Show:
25 September
7:30 PM
PVR XYZ
```

Another example:

```text
Content:
India vs Australia

Show:
26 September
8:00 PM
Stadium XYZ
```

Conceptual attributes:

```text
Show
----------------
id
content_id
venue_id
start_time
end_time
status
created_at
updated_at
```

A single Content item can have multiple Shows.

---

# 11. Venue

The Venue Service functionality is initially owned by the Catalog Service.

A venue represents the physical location where a Show occurs.

Conceptual attributes:

```text
Venue
----------------
id
name
address
city
```

A venue can contain multiple seats.

```text
Venue
   │
   ├── Seat A1
   ├── Seat A2
   ├── Seat A3
   └── ...
```

---

# 12. Seat

Seats belong to a venue's seating layout.

Conceptually:

```text
Seat
----------------
id
venue_id
section
row
seat_number
seat_type
```

The permanent seat definition belongs to the venue.

The availability of that seat for a particular Show belongs to the booking domain.

This distinction is important:

```text
Venue Seat
    ↓
Permanent physical seat


Show + Seat
    ↓
Availability for a particular show
```

---

# 13. Booking Service

The Booking Service owns the booking lifecycle.

Responsibilities:

* Seat availability
* Seat holds
* Booking creation
* Booking cancellation
* Booking history
* Booking state
* Concurrency control

The Booking Service is responsible for deciding whether a requested seat can actually be reserved.

The Gateway does not contain booking business logic.

```text
Gateway
    ↓
"Is this request allowed to enter?"


Booking Service
    ↓
"Can this seat actually be booked?"
```

---

# 14. Booking State Model

A conceptual booking state is:

```text
PENDING
   │
   ├── CONFIRMED
   │
   ├── CANCELLED
   │
   └── FAILED
```

Seat availability can be represented as:

```text
AVAILABLE
    │
    ▼
  HELD
    │
    ├── Booking succeeds → BOOKED
    │
    └── Hold expires → AVAILABLE
```

---

# 15. Concurrent Booking

Concurrency is a critical requirement of the platform.

Suppose:

```text
Show A

Seat A10
```

Three users request the same seat simultaneously:

```text
User A ──┐
User B ──┼──→ Seat A10
User C ──┘
```

The system must ensure that only one valid booking obtains the seat.

The Booking Service will use database transactions and appropriate concurrency-control mechanisms.

The frontend cannot be trusted to enforce seat uniqueness.

The final implementation will determine the exact locking strategy, such as:

* Database row locking
* Unique constraints
* Transaction isolation
* Atomic state transitions

**Implemented:** database row locking. Every `show_seats` status
transition (`holdSeats`, `releaseHold`, and the seat transitions inside
`confirmBooking`/`cancelBooking`) goes through `BookingService`'s
`lockShowSeats`, which uses `ShowSeatRepository.lockAllByIdIn` — a
`SELECT ... FOR UPDATE` row lock (`LockModeType.PESSIMISTIC_WRITE`) inside
an `@Transactional` method. This is what actually prevents two concurrent
requests from both claiming the same seat: the second request's lock
acquisition blocks until the first transaction commits, so it re-reads
post-commit status rather than racing on stale data. Confirmed directly
from `BookingService`'s own documentation of this mechanism. No unique
constraint or additional atomic-transition mechanism was needed on top of
this.

---

# 16. Seat Hold Architecture

Redis can be used for temporary seat holds.

Conceptually:

```text
User selects seat
       │
       ▼
Booking Service
       │
       ▼
Redis
       │
       ▼
Temporary Hold
       │
       ├── Booking succeeds
       │       ↓
       │     BOOKED
       │
       └── Hold expires
               ↓
           AVAILABLE
```

Redis is responsible for temporary, time-sensitive state.

PostgreSQL remains the durable source of truth for confirmed bookings.

The final booking flow must prevent a Redis hold from being treated as a confirmed booking without durable database confirmation.

**Implementation status: this design was not built.** The real hold
mechanism (`POST /api/bookings/shows/{showId}/seats/hold`) is a direct
`show_seats.status` transition (`AVAILABLE → HELD`) under the §15 row
lock — no Redis involvement at all, and none of the diagram above is
real. Concretely, per `BookingService`'s own documented "what is NOT
safe / NOT implemented yet":

* **No hold ownership.** `show_seats` has no column recording *who* holds
  a seat. `holdSeats` accepts a `userId` parameter, but it is currently
  unused — not stored, not checked. Any authenticated caller who knows a
  `showSeatId` can act on a hold regardless of who created it.
* **No hold expiry.** There is no TTL anywhere. A seat set to `HELD`
  stays `HELD` indefinitely unless something explicitly calls
  `releaseHold` or a booking is cancelled — an abandoned checkout
  permanently locks a seat today. (This is specific to *seat* holds; it is
  unrelated to payment expiry, §25.3/FR-45, which does have a real
  scheduled sweep.) The frontend's own seat-hold countdown timer
  (`eventtick/src/pages/SeatSelection.tsx`) is explicitly documented there
  as a **display-only** countdown with no corresponding backend
  expiration — selecting seats and never completing checkout leaves them
  `HELD` forever.

Closing this gap (a Redis-backed hold with ownership and TTL, or an
equivalent scheduled sweep) remains unimplemented, unscheduled, future
work — not a partially-built Redis integration quietly failing, but code
that was never written.

---

# 17. Database Architecture

The architecture follows the database-per-service principle.

Conceptually:

```text
User Service
      │
      ▼
 User Data

Catalog Service
      │
      ▼
Catalog Data

Booking Service
      │
      ▼
Booking Data
```

A service should not directly query another service's database.

Communication between services should happen through:

* REST APIs
* Events/messages where appropriate

For the initial local implementation, the project may use one PostgreSQL server with logically separated databases or schemas.

The architectural ownership boundary remains:

```text
User Service     → users

Catalog Service  → content, venues, seats, shows

Booking Service  → show_seats, bookings, booking_seats
```

`show_seats` (show-specific seat availability and pricing) belongs to
Booking Service, not Catalog Service: it is the table the booking
concurrency-control mechanism (§15) locks and updates directly, so it is
part of the booking domain even though it references a physical seat
(`seats`, owned by Catalog Service) and a show (`shows`, also owned by
Catalog Service). Catalog Service owns the permanent physical seat layout
(`seats`) and show schedule (`shows`); Booking Service owns whether a given
seat is available/held/booked for a given show.

Physical database separation can be introduced later if required.

**Gap closed (Phase 18): `POST /api/admin/shows/{showId}/seats` creates
`show_seats` rows.** Until this phase, no endpoint anywhere inserted into
the table — `BookingController` only read (`GET .../shows/{showId}/seats`),
held (`POST .../hold`), and released (`POST .../release`) seats that
already existed; the admin seat-activity endpoint was (and remains)
read-only; the only place a `ShowSeat` entity was constructed in the
entire codebase was a JUnit test. Every show used for manual or live
testing before Phase 18 had its `show_seats` rows inserted directly into
PostgreSQL by hand. See §52 for the new endpoint's design and why it
does not call catalog-service to validate `showId`/`seatId`. This still
does not make the gap fully self-closing: creating a `Show` through
Catalog Service's `POST /api/catalog/shows` (§8) still does not
automatically create its inventory — an administrator must call the new
endpoint separately, for every show, after creating it. See §47.2 for why
`ShowCreated`/an automatic inventory-creation consumer was catalogued as
a candidate event but still not built; Phase 18 closed the "no API at
all" gap, not the "not automatic" one.

---

# 18. PostgreSQL

PostgreSQL is the primary persistent database technology.

It stores durable business information such as:

* Users
* Plans
* Content
* Shows
* Venues
* Seats
* Bookings
* Booking seats
* Rate-limit policies

The exact database schema will be defined during the database-design phase.

---

# 19. Redis

Redis is used for fast-changing and temporary state.

Primary use cases:

### Rate Limiting

```text
User + Policy Group
        │
        ▼
      Redis
        │
        ▼
Token Bucket State
```

### Temporary Seat Holds

```text
Show + Seat
     │
     ▼
Temporary Redis Hold
```

**Not implemented** — see §16. Seat holds are a plain PostgreSQL
`show_seats.status` column transition under a row lock; Redis plays no
role in them. Rate limiting (above) remains the one real use of Redis in
this project.

### Future Caching

Frequently requested catalog information can potentially be cached later.

Redis is not intended to replace PostgreSQL as the durable source of business records.

---

# 20. Dynamic Rate Limiting

**Implementation status.** §20–§22 below describe the original Phase 1
design. The actual Phase 11/12 implementation (`backend/gateway-service`,
`com.eventtick.gateway.ratelimit`) realizes the "Resolve Policy → Redis →
Allow/Reject" flow in §20 as designed, but **not** §21's `RateLimitPolicy`
entity or §22's PostgreSQL-backed, admin-editable policy flow — see §20.1.

## 20.1 What was actually built, and the open classification gap

Policies are a static `category × tier` matrix
(`eventtick.rate-limit.policies.*` in `application.yml`), read once at
startup — not rows in PostgreSQL, not editable at runtime, and not
refreshable without a Gateway restart (`docs/requirements.md` FR-31,
explicitly not implemented). `RequestCategoryClassifier` recognizes
exactly four path-based categories: `AUTH` (`/api/auth/**`), `CATALOG`
(`/api/catalog/**`), `BOOKING` (`/api/bookings/**`), `USER`
(`/api/users/**`). **`/api/payments/**` (Phase 15) and `/api/admin/**`
(Phase 13) — both added to the Gateway's routes after this classifier was
written — match none of these prefixes and fall through to `UNKNOWN`**,
which always resolves to the fixed, conservative fallback policy rather
than a dedicated tier-aware one. Both path groups are still rate-limited
(never unlimited — `UNKNOWN` never means "no policy"), just without the
plan/role-aware granularity every other category gets. Closing this means
adding `PAYMENT`/`ADMIN` cases to `RequestCategoryClassifier` and matching
entries to the policy matrix — not yet done.

Dynamic rate limiting is one of the core architectural features of Tichboo.

Instead of using one hard-coded rule:

```text
100 requests/minute
```

the Gateway uses configurable policies.

Conceptually:

```text
Request
   │
   ▼
Authenticate User
   │
   ▼
Identify Plan
   │
   ▼
Identify Route Group
   │
   ▼
Resolve Policy
   │
   ▼
Redis Rate Limiter
   │
   ├── Allowed
   │      ↓
   │   Backend
   │
   └── Exceeded
          ↓
        HTTP 429
```

The limiter can use a token-bucket strategy.

---

# 21. Rate-Limit Policy

A policy can conceptually contain:

```text
RateLimitPolicy
-------------------------
id
plan_id
route_group
refill_rate
capacity
request_cost
enabled
version
updated_at
```

Example plans:

```text
FREE
PREMIUM
VIP
```

Example route groups:

```text
CATALOG_READ
SEARCH
SHOW_READ
BOOKING
```

Different plans and route groups can have different limits.

The actual numeric values will be determined during implementation and testing.

---

# 22. Dynamic Policy Management

**Not implemented** — see §20.1. The flow below is the original Phase 1
design; the real Phase 12 implementation stops at "policy chosen per
request from configuration," with no PostgreSQL-backed policy store, no
admin update path, and no runtime refresh.

The important distinction is:

```text
Static Rate Limiting

Limit exists in application configuration
```

versus:

```text
Dynamic Rate Limiting

Policy stored persistently
        ↓
Gateway loads policy
        ↓
Policy can change at runtime
        ↓
Gateway applies updated policy
```

An administrator can modify policy parameters without rebuilding the Gateway.

A simplified update flow:

```text
Admin
  │
  ▼
User/Admin Service
  │
  ▼
PostgreSQL
  │
  ▼
Updated Policy
  │
  ▼
Gateway Policy Refresh
  │
  ▼
New Rate-Limit Behavior
```

The Gateway can maintain an in-memory policy snapshot to avoid querying PostgreSQL on every request.

Redis remains responsible for rapidly changing limiter state.

---

# 23. Authentication Architecture

Authentication uses Spring Security and JWT.

General flow:

```text
User
  │
  │ Login
  ▼
User Service
  │
  │ Validate credentials
  ▼
Access Token + Refresh Credential
  │
  ▼
Client
  │
  │ Authorization: Bearer <token>
  ▼
API Gateway
  │
  │ Validate JWT
  ▼
Protected Service
```

JWT claims may contain information required for authentication and authorization.

Sensitive information should not be stored inside the token.

JWT validation should verify relevant claims such as:

* Signature
* Issuer
* Audience
* Expiration
* Not-before time where applicable

---

# 24. Authorization Architecture

Authentication determines identity.

Authorization determines permissions.

Example:

```text
CUSTOMER
   ├── Browse content
   ├── Search
   ├── View shows
   ├── Book tickets
   └── View own bookings


ADMIN
   ├── Manage content
   ├── Manage shows
   ├── Monitor bookings
   └── Manage rate-limit policies
```

A user must not be able to access another user's bookings simply by changing an ID in the request.

Resource ownership checks remain the responsibility of the appropriate business service.

---

# 25. Payment Service

Payment is an optional service that will be introduced after the core booking system is functional.

Responsibilities may include:

* Payment initiation
* Payment processing state
* Transaction records
* Payment success/failure

Initially, payment can be simulated.

Later, an external payment provider can be integrated.

The Payment Service should remain separate from the Booking Service.

The initial project does not depend on real payment processing.

## 25.1 Payment Service Design (Phase 15 Step 1 — Designed, Not Implemented)

**Nothing in this subsection is implemented.** No `payment-service` module,
controller, entity, repository, or migration exists. This is the detailed
design that the paragraph above always deferred, produced by inspecting
the actual current implementation (`booking-service`'s entities, service
layer, controller, and the `bookings`/`booking_seats`/`show_seats`
migrations) rather than assuming a textbook payment flow.

### Service boundary decision

**A dedicated `payment-service`**, not a package inside `booking-service`.
This was already the documented decision above ("should remain separate
from the Booking Service") and in `docs/requirements.md` FR-18's gateway
routing table (`/api/payments/** → Payment Service`) since Phase 1 — this
subsection validates that decision against the current codebase rather
than re-deciding it from scratch, and it still holds:

- Matches the one-service-per-bounded-context pattern every other service
  already follows (user/catalog/booking each own a distinct data domain;
  payment records and provider integration are a distinct domain from seat
  concurrency).
- `booking-service`'s seat-locking transactions (`SELECT ... FOR UPDATE`
  inside `@Transactional` methods — see `BookingService`'s own class
  Javadoc) are latency- and correctness-sensitive; a payment provider's
  round-trip (even a mock one, and certainly a real one later) must never
  execute inside that same transactional/locking path.
- Keeps provider credentials/secrets confined to one service's
  configuration, never touching `booking-service`.
- `payment-service` owns **only** a new `payments` table. It explicitly
  does **not** own: `bookings`, `booking_seats`, `show_seats` (all remain
  `booking-service`'s), `users` (user-service), or content/venues/shows
  (catalog-service). It does not perform seat locking and does not decide
  seat availability.

### Integration direction (the actual current seam)

`BookingService.confirmBooking(bookingId)` is, today, the **only** trigger
that moves a booking `PENDING → CONFIRMED` — and its own Javadoc already
says so explicitly: *"There is no payment step yet — this is currently the
only trigger for confirmation, called directly rather than from a
payment-success callback."* `BookingController` exposes this as
`POST /api/bookings/{bookingId}/confirm`, called directly by the frontend
today, with no payment gate.

The least invasive design keeps that endpoint's contract completely
unchanged and simply adds a new caller: once payment is introduced,
`payment-service` — not the frontend — calls
`POST /api/bookings/{bookingId}/confirm` (service-to-service) after a
successful payment, instead of the frontend calling it directly. Zero
changes to `booking-service`'s existing confirm endpoint or its contract.

*(Superseded — Phase 15 Step 4 final security cleanup, §25.5: the public
`POST /api/bookings/{bookingId}/confirm` was a payment bypass and has been
removed. Confirmation is now an internal, payment-driven operation only:
`payment-service` calls `POST /internal/bookings/{id}/confirm`. The
frontend never called the public endpoint.)*

**One real gap this surfaced:** `BookingStatus` already defines `FAILED`
(mirrored by the `chk_bookings_status` DB constraint), but **no code path
in the current `BookingService` ever sets it** — only `PENDING`,
`CONFIRMED`, and `CANCELLED` are ever assigned. `FAILED` is a
reserved-but-unused terminal state, and it is the natural target for "the
booking's payment failed" — distinct from `CANCELLED` (which today means
"the user or an admin explicitly cancelled it"). Today's `cancelBooking`
already does the right mechanical thing on failure (releases held/booked
seats back to `AVAILABLE`), it just lands on the wrong status for a
payment-failure reason. **This means Phase 15 Step 2 (implementation) will
need one small, additive `booking-service` change**: a way to reach
`BookingStatus.FAILED` with the same seat-release behavor `cancelBooking`
already has (either a new endpoint mirroring `cancel`, or an optional
"reason"/target-status parameter on the existing transition) — not
designed in full here, and explicitly **not implemented now**. Until that
exists, reusing the existing `cancel` endpoint is a valid, smaller interim
option with the cost of blurring "user cancelled" and "payment failed" in
the booking's own status column.

### Booking/payment consistency strategy

No distributed transaction is used or claimed anywhere in this design. The
payment record's own status is the single source of truth for "did the
money move"; the booking's status is reconciled from it, not the other way
around, and reconciliation is explicit and eventual, not atomic:

- **A. Booking created → payment succeeds.** Ordinary path: `payment-service`
  records `SUCCESS` (durably committed in its own database) first, then
  calls `booking-service`'s existing `confirm` endpoint.
- **B. Booking created → payment fails.** `payment-service` records
  `FAILED`, then calls `booking-service` to release the seats and move the
  booking to its failure terminal state (see the `FAILED`-status gap
  above).
- **C. Payment request times out** (client never gets a response). The
  payment row is the authoritative record, not the client's guess — a
  client that timed out should call `GET /api/payments/{id}` (or the
  booking-scoped lookup) to learn the *actual* state rather than assuming
  failure and blindly retrying, which is exactly what idempotency (below)
  protects against if it does retry anyway.
- **D. Payment provider reports success after client timeout.** The
  provider-abstraction interface (below) is designed so a later real
  provider's asynchronous confirmation (a webhook, arriving after the
  original request already timed out) and the original request's own
  synchronous path both attempt the *same* transition
  (`PENDING → SUCCESS`), guarded so it only actually applies once: whichever
  arrives first wins, and the second is a no-op that still returns success
  (not an error) — never a double-charge, never two calls to
  `booking-service`'s confirm endpoint racing.
- **E. Client retries the payment request** (e.g. a network blip with no
  response received). The idempotency key (below) makes this return the
  existing payment's current result instead of creating a second payment
  or invoking the provider twice.
- **F. Client submits the same payment request twice** (double-click,
  duplicate tab). Same idempotency mechanism as E; if the *data* differs
  under the same key (different booking, different amount), it is rejected
  as a conflict rather than silently processed under the old key or
  silently ignored.
- **G. Payment succeeds but the booking-update call fails** (the payment
  provider confirmed, but the subsequent call from `payment-service` to
  `booking-service`'s confirm endpoint fails or times out). This is the
  case a distributed transaction would normally be reached for — instead,
  because the payment row was already committed as `SUCCESS` *before* that
  call was attempted, the system is left in a **detectable, not silent**
  inconsistent state: a payment marked `SUCCESS` whose booking is still
  `PENDING`. The design calls for an explicit reconciliation step (a retry
  job, or an admin-triggered retry) that finds exactly this mismatch and
  re-attempts the confirm call — safe to retry, since confirming an
  already-`CONFIRMED` booking is expected to be a no-op/conflict on the
  booking side, not a silent double-effect. This reconciliation mechanism
  is **not implemented in Step 1**; it is named here because designing the
  state model without acknowledging this gap would be incomplete.
- **H. Booking expires while payment is pending.** `booking-service` has no
  seat-hold TTL of any kind yet (`BookingService`'s own Javadoc: *"There is
  no TTL anywhere... until Redis... exists, an abandoned checkout
  permanently locks a seat"* — see §16). Payment design does not invent
  booking-side expiry to compensate (that would be redesigning the booking
  system, explicitly out of scope) — instead, `payments.status` gets its
  own independent `EXPIRED` terminal state driven by a payment-level
  timeout, so a payment that never resolves is still eventually closed out
  even though nothing today would separately expire the booking's seat
  hold. Once Redis-backed seat-hold expiry (§16) exists, its interaction
  with an already-`EXPIRED` payment becomes a genuine future reconciliation
  question, flagged here rather than solved.

### Payment state model

```
CREATED ──────► PENDING ──────► SUCCESS   (→ booking CONFIRMED)
   │               │
   │               ├─────────► FAILED    (→ booking released)
   │               │
   │               └─────────► EXPIRED   (→ booking released)
   │
   └─────────────────────────► CANCELLED (→ booking released)
```

`SUCCESS`, `FAILED`, `CANCELLED`, `EXPIRED` are terminal — no transition
leaves any of them. A transition attempt *into* the state a payment is
already in is treated as a no-op (idempotent replay, not an error — see
scenario D); an attempt to leave a terminal state for a different one is
rejected, mirroring `InvalidBookingStateException`'s existing role for
`Booking`.

Deliberately **not** the original FR-18 draft's bare
`PENDING`/`SUCCESS`/`FAILED` — that omits the pre-provider-call window a
correct idempotency design needs (`CREATED`, before the provider is even
invoked, is what makes "the same request arrived twice before any provider
call happened" representable) and collapses three genuinely different
non-success reasons (the provider actively declined it, a timeout elapsed,
or the user abandoned checkout before any provider call) into one
ambiguous `FAILED` — exactly the ambiguity this design was asked to avoid.
This is richer than `BookingStatus`'s four states deliberately: payment has
a provider round-trip and a client-visible idempotency window that
booking's simpler seat-lifecycle state never needed; it is not a mechanical
copy of `BookingStatus`, nor is it copied from FR-18 unchanged.

### Idempotency design

- **Key source:** client-supplied, sent as a request-body field
  (`idempotencyKey`), not an HTTP header — consistent with this project's
  existing convention of every request identifier living in a validated,
  typed request DTO (e.g. `CreateBookingRequest.userId()`,
  `CancelBookingRequest.requestingUserId()`) rather than a bespoke header,
  even though `Idempotency-Key` as a header is the more common REST
  convention elsewhere.
- **Association:** one idempotency key maps to exactly one payment row
  (`payments.idempotency_key`, globally unique — collision risk is the same
  negligible UUID-collision risk this project already accepts for every
  primary key).
- **Same key, same data submitted again:** return the existing payment's
  current state, `200 OK` (not `201 Created`) — no new provider call.
- **Same key, different data** (different `bookingId` or amount): rejected,
  `409 Conflict`, a distinct error code — never silently reprocessed under
  the old key and never silently ignored.
- **Same booking, a genuinely new key:** allowed only if that booking's
  existing payment (if any) is already terminal and non-`SUCCESS`
  (`FAILED`/`CANCELLED`/`EXPIRED`) — i.e. "retry after a real failure" is
  fine. A new payment attempt for a booking that already has a
  `CREATED`/`PENDING`/`SUCCESS` payment is rejected, enforced at the
  database level (see the data model's partial unique index) rather than
  only in application code.

### Security model

- Payment endpoints sit behind the exact same Gateway JWT layer as every
  other non-public endpoint (`GatewaySecurityConfig`'s
  `.anyExchange().authenticated()` catch-all already covers any new route
  automatically — no Gateway security code changes needed, the same way
  none were needed when catalog-service's or booking-service's routes were
  first added).
- **A customer may create a payment only for a booking they own.**
  `payment-service` verifies this itself by calling `booking-service`
  (`GET /api/bookings/{bookingId}`) and comparing the returned booking's
  `userId` against the caller's own JWT `sub` claim — derived from the
  validated JWT server-side, **not** taken from a client-supplied field.
  This is a deliberate improvement over `booking-service`'s own current,
  documented gap (`cancelBooking` "accepts a `requestingUserId` but does
  not check it against `booking.getUserId()` — there is no authentication
  layer yet to trust that value"): `payment-service` does the check
  correctly from day one, the way `user-service`'s FR-37 self-role check
  already does (`Authentication.getName()`, never a request-body value).
  This does **not** fix `booking-service`'s existing gap — that remains
  out of scope for this phase.
- **A customer may view only their own payment records** (same
  JWT-`sub`-vs-booking-owner check). **An administrator may view any
  payment record, read-only** — mirroring FR-39's existing
  `GET /api/admin/bookings` pattern. A future `GET /api/admin/payments`
  (paginated, read-only) is a natural follow-on FR, not designed in detail
  here since it isn't one of Step 1's three endpoints.
- **Provider callbacks/webhooks need a separate authentication mechanism**
  from Eventtick's own JWTs, since an external provider will never hold an
  Eventtick user token. For a *real* provider this would be a
  provider-specific shared-secret/signature verification
  (`payment-service` would need its own narrow, non-JWT security path for
  exactly that one route — the same shape as Phase 14's narrow Actuator
  allowlist, a different mechanism entirely from a business-JWT check).
  **Not needed for Step 1**: `MockPaymentProvider` has no external caller
  at all — its result is a plain synchronous method return, not a separate
  HTTP callback — so webhook authentication design is deferred until a
  real provider is actually integrated, rather than building
  infrastructure nothing yet calls.
- No payment-provider credential or secret is ever exposed in a payment
  response body.

### Amount and currency representation

- **Amount:** `NUMERIC(10,2)` / `BigDecimal` — matching
  `bookings.total_amount` and `booking_seats.price_at_booking` exactly,
  never a binary floating-point type. This is a considered trade-off, not
  a default: a minor-unit integer (cents) is generally the more robust
  professional choice for money, avoiding decimal-rounding questions
  entirely, and would be the recommended choice if this project's money
  model were being designed from scratch today. But `bookings`/
  `booking_seats` already use `NUMERIC(10,2)` throughout, this phase is
  explicitly barred from redesigning the booking system, and a payment
  amount is constantly compared against a booking's own `total_amount` —
  introducing a second, different monetary representation for payment
  alone would mean constant conversion at every comparison. If this
  project ever moves to minor-unit integers, that should be a coordinated
  migration across `bookings`/`booking_seats`/`payments` together, not
  something payment does unilaterally.
- **Currency:** genuinely new — **no existing table has a currency column
  anywhere** (every existing monetary field assumes one implicit, unnamed
  currency project-wide). `payments.currency` is proposed as an explicit
  `CHAR(3)` (ISO 4217) column. The actual target currency is an open
  question for the team to confirm (not guessed here) — see "Open design
  questions" below.
- **Rounding:** `NUMERIC(10,2)`/`BigDecimal` inherit the same two-decimal,
  no-sub-cent behavior `total_amount`/`price_at_booking` already have;
  round-half-up is recommended for consistency with ordinary currency
  rounding conventions.

### Payment data model (conceptual — no migration created)

A future migration `0011_create_payments_table` (continuing the existing
numbering — `0001`–`0010` are already in use) would define:

```sql
CREATE TABLE payments (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id          UUID          NOT NULL,
    user_id             UUID          NOT NULL,
    amount              NUMERIC(10,2) NOT NULL,
    currency            CHAR(3)       NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'CREATED',
    provider            VARCHAR(30)   NOT NULL,
    provider_reference  VARCHAR(100),
    idempotency_key     VARCHAR(100)  NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_payments_status CHECK (status IN
        ('CREATED','PENDING','SUCCESS','FAILED','CANCELLED','EXPIRED')),
    CONSTRAINT chk_payments_amount_nonneg CHECK (amount >= 0),
    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_payments_booking
        FOREIGN KEY (booking_id) REFERENCES bookings (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_payments_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
);

-- At most one "live" (non-terminal-failure) payment per booking — the
-- database-level enforcement of "duplicate payment prevention", not just
-- an application-layer check.
CREATE UNIQUE INDEX uq_payments_one_active_per_booking
    ON payments (booking_id)
    WHERE status IN ('CREATED', 'PENDING', 'SUCCESS');

CREATE INDEX idx_payments_booking_id ON payments (booking_id);
CREATE INDEX idx_payments_user_id    ON payments (user_id);
CREATE INDEX idx_payments_status     ON payments (status);

CREATE TRIGGER trg_payments_set_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

`fk_payments_booking`/`fk_payments_user` are real, enforced foreign keys —
not merely conceptual — because this project's services currently share
one physical `eventtick_db` (§17 already documents database-per-service as
a *future* target, not the current reality), exactly the same reasoning
`bookings.user_id`/`bookings.show_id` already rely on for their own
cross-service FKs. At the JPA level, a future `Payment` entity would hold
`bookingId`/`userId` as plain `UUID` fields, not `@ManyToOne` associations
— mirroring `Booking.userId`/`Booking.showId`'s own reasoning exactly,
since `payment-service` would have no local `Booking`/`User` entity class
of its own.

`provider_reference` is the only nullable business field (unset until the
provider assigns one). Primary key, `created_at`/`updated_at` trigger
pattern, `VARCHAR(20)` + `CHECK` for the status enum, and `ON DELETE
RESTRICT ON UPDATE CASCADE` on both FKs all match `bookings`/`booking_seats`
exactly, not a new convention.

### Provider abstraction

```java
public interface PaymentProvider {
    PaymentProviderResult charge(PaymentChargeRequest request);
}

public record PaymentChargeRequest(UUID paymentId, BigDecimal amount, String currency, String idempotencyKey) {}
public record PaymentProviderResult(boolean success, String providerReference, String failureReason) {}
```

`MockPaymentProvider implements PaymentProvider` is the only implementation
Step 1 needs — no network call, no real credentials, a synchronous return.
A real provider (Stripe/Razorpay/etc.) would implement the same interface
later, selected by configuration (e.g. `payment.provider=mock|stripe`) —
the same small-pure-swappable-strategy shape this codebase already uses
for `RequestCategoryClassifier`/`UserTierResolver`/`RateLimitPolicyResolver`
in the Gateway's rate-limiting design, not a new stylistic pattern. The
interface is kept synchronous for now specifically because the state model
above (`PENDING` as its own explicit state, not collapsed into the call)
already accommodates a later provider needing to resolve asynchronously
(webhook-driven) without changing the interface's shape — only its
implementation.

### Open design questions / not yet decided

- **Actual target currency** for `payments.currency`'s value — not guessed
  in this design; needs a decision from whoever owns the project's
  real-world business context.
- **Exactly how `booking-service` reaches `BookingStatus.FAILED`** (new
  endpoint vs. a parameter on the existing `cancel` transition) — flagged
  above. **Resolved for Step 2 as the documented interim option**: a
  payment failure reuses booking-service's existing `cancel` endpoint
  unchanged, landing the booking in `CANCELLED`, not `FAILED` — see §25.2.
  The "proper" fix (giving booking-service a real `FAILED` transition)
  remains deferred.
- **The `SUCCESS`-payment/`PENDING`-booking reconciliation mechanism**
  (scenario G) — named, not designed in detail (retry job vs. admin action
  vs. something else) — still deferred; not built in Step 2.
- Whether `GET /api/payments/{id}` should return `403` or `404` to a
  customer requesting a payment they don't own was decided as `404` (avoid
  confirming the record's existence to an unauthorized caller) — see
  `docs/api-contracts.md`'s Payment API section — but no existing
  row-ownership precedent in this codebase was found to confirm that
  choice against; it's a reasoned decision, not a copied one, and is
  revisitable. **Implemented as designed in Step 2.**

## 25.2 Payment Service Implementation (Phase 15 Step 2)

A real `payment-service` module exists at `backend/payment-service/`,
following every convention identified during this step's inspection of the
three existing services, not a new architecture. What follows records
where it matches §25.1's design exactly and where reality forced a
concrete decision §25.1 had left open.

**Structure**: `controller`/`dto`/`entity`/`exception`/`repository`/
`service`/`provider`/`client`/`security`/`config` packages — the same
shape as `docs/architecture.md`'s own proposed structure, with `client`
(for `BookingServiceClient`) and `security` (see below) added since they
turned out to be genuinely needed, not because a different architecture
was chosen. Java 17, Spring Boot 3.3.4, Maven, Spring Data JPA, PostgreSQL,
Bean Validation, JUnit/Spring Boot Test — identical stack to every other
service, no new framework introduced. Port `8084`, the next in the
existing `8080`–`8083` sequence.

**A genuine new decision §25.1 didn't fully resolve: how does
`payment-service` know the caller's own identity?** Unlike catalog-service/
booking-service (which have no Spring Security dependency at all and fully
trust the Gateway), payment-service's ownership checks (§25.1's security
model) need to know *who* the caller is, not just that the Gateway already
authenticated them. The implementation gives `payment-service` its own
`SecurityConfig`/`JwtAuthenticationFilter`/`JwtService`, independently
validating the exact same JWTs user-service issues — a second copy of
user-service's own defense-in-depth pattern (which already independently
validates the Gateway-forwarded token for `/api/users/me`), not a new
mechanism. `payment-service` never issues a token itself, so its
`JwtService` is parse-only (no `generateToken`).

**Booking-service interaction**: `BookingServiceClient` (a thin wrapper
around Spring's `RestClient`) is the *only* class that knows
booking-service's URL shape, calling it directly
(`booking-service.base-url`, default `http://localhost:8083`) rather than
back out through the Gateway — booking-service has no security of its own
to satisfy, and there is no reason to route internal traffic through the
customer-facing edge. Three existing, **unmodified** booking-service
endpoints are reused exactly as they already work: `GET /api/bookings/{id}`
(authoritative owner/status/amount), `POST /api/bookings/{id}/confirm`
(payment success; since removed — see §25.5), `POST /api/bookings/{id}/cancel` (payment failure — the
interim mechanism above). **No booking-service code was changed for Phase
15 Step 2.** *(Superseded by the Phase 15 Step 4 follow-up, §25.5:
booking-service now validates JWTs and enforces booking ownership on
`/api/bookings/**`, so payment-service calls the equivalent
`/internal/bookings/**` endpoints instead.)* The confirm/cancel calls are deliberately best-effort
(logged, not thrown) — see §25.1 scenario G; a failure there never
retroactively changes the payment's own already-committed status.

**Idempotency and the one-live-payment-per-booking rule**: implemented
exactly as designed — an application-level pre-check first, then a real
insert protected by both `uq_payments_idempotency_key` (a genuine `UNIQUE`
column) and `uq_payments_one_active_per_booking` (the partial index) at
the database level, with `DataIntegrityViolationException` caught and
resolved (re-check by key; if that's not the collision, it's the
one-live-payment index) rather than surfaced as a generic 500.

**Testing found and fixed two real entity-mapping bugs, not just design
gaps**: `Payment.idempotencyKey` was initially mapped without
`unique = true` — Hibernate's own test-schema generation silently didn't
create the constraint the design called for, until a dedicated real-H2 test
(`PaymentRepositoryConstraintTest`) caught it expecting an exception that
never came. Separately, `Payment.createdAt`/`updatedAt` (mapped
`insertable = false`, matching `Booking`'s own pattern, because the real
Postgres migration's `DEFAULT now()` is the intended source of truth) have
no equivalent default in Hibernate's `ddl-auto: create-drop` H2 test schema
on their own — fixed with `@ColumnDefault("CURRENT_TIMESTAMP")` (a
schema-generation hint only, inert against the real migration), the exact
same precedent `User.createdAt`/`updatedAt` already established.

**Gateway**: one new route, `Path=/api/payments/** -> localhost:8084`,
matching `/api/bookings/**`'s own top-level wildcard style. No new Gateway
security rule — the existing `anyExchange().authenticated()` catch-all
already covers it, proven by `GatewayPaymentRouteTest` (unauthenticated
rejected, authenticated request reaches the upstream, the three existing
routes are unaffected) alongside `GatewayRoutesTest`'s route-table check
(now 19 routes, not 18).

**What Step 2 still defers** (see §25.1's "what Step 2 implements vs.
defers" and the open questions above): the `EXPIRED`/`CANCELLED` payment
transitions (no timeout sweep, no "abandon checkout" endpoint), the real
`BookingStatus.FAILED` transition on booking-service's side, the
scenario-G reconciliation mechanism, real provider integration, provider
webhooks, and a frontend payment UI.

## 25.3 Payment Lifecycle and Consistency (Phase 15 Step 3)

### State machine

One transition table, `PaymentStatus.ALLOWED_TRANSITIONS`, exposed as
`canTransitionTo(target)` — the only place transition legality is decided.
`PaymentService.transitionTo` rejects an illegal move with
`InvalidPaymentStateException`. Approved transitions: `CREATED -> PENDING |
CANCELLED`; `PENDING -> SUCCESS | FAILED | EXPIRED | CANCELLED`;
`SUCCESS`, `FAILED`, `EXPIRED`, `CANCELLED` have no outgoing transitions.
`isTerminal()` and `isLive()` are independent (`SUCCESS` is both; the other
three terminal states are not live), so "one live payment per booking" is
unaffected by the new states. `CREATED -> CANCELLED` is valid and tested at
the state-machine level, but no customer-facing "abandon checkout" endpoint
was added in this step, so nothing in production reaches it yet.

### Expiration mechanism

A `@Scheduled` sweep (`PaymentLifecycleScheduler`, `fixedDelay`, so runs never
overlap) calls `PaymentService.expirePendingPayments()`. Payments `PENDING`
for longer than `payment.expiration-minutes` (default 15, configurable) are
moved to `EXPIRED` with a single conditional statement,
`UPDATE ... SET status='EXPIRED' WHERE id=? AND status='PENDING'`
(`PaymentRepository.expireIfStillPending`). The database's row locking is the
concurrency control: repeated sweeps, multiple instances, or a race with the
normal creation flow's own `PENDING -> SUCCESS/FAILED` write all resolve to
"one caller updates the row, everyone else updates zero rows." No in-memory
state, no distributed lock, no new dependency (`@EnableScheduling` is part of
spring-context). Candidates come from the database on every run, so a restart
loses nothing.

Custom `@Modifying` repository queries are not auto-wrapped in a transaction
by Spring Data JPA (only inherited CRUD methods are), so `expireIfStillPending`
and `markBookingSynced` carry their own `@Transactional`, scoped to exactly
one write. This was found by the new tests: without it both throw
`TransactionRequiredException` the first time the scheduler runs.

### Booking / payment consistency

Payment expiry releases the booking, the same as `FAILED`: an expired payment
that left its booking `PENDING` would keep the seats held with no path to
completion. Order is always: (1) the payment status is durably committed,
(2) then booking-service is called, best effort. No distributed transaction
exists or is claimed: the two services are separate HTTP-reachable systems,
so a single atomic commit across them is not available, and holding a
database transaction open across an HTTP call would trade a consistency
problem for a lock-holding one. If the booking-service call fails, the
payment **stays** `SUCCESS`/`FAILED`/`EXPIRED` — it is never reverted — and
the failure is logged at WARN and recorded for retry (below).

`BookingStatus.FAILED` was evaluated and is **not** introduced: reusing
booking-service's existing `cancel` transition still gives the correct
outcome (seats released, booking terminal and not `CONFIRMED`), and a new
status would need a migration, enum, and API change in booking-service for
no behavioral gain. Revisit only if reporting needs to distinguish
"cancelled by user" from "payment failed".

### Reconciliation (scenario G and its siblings)

`payments.booking_sync_status` (`PENDING`/`DONE`, migration `0012`, internal
only, never in `PaymentResponse`) records whether booking-service has
acknowledged a terminal payment's side effect (confirm for `SUCCESS`,
cancel for `FAILED`/`EXPIRED`). It defaults to `PENDING`; it becomes `DONE`
only after booking-service returns 2xx. A second sweep,
`PaymentService.reconcilePendingBookingSync()`, selects terminal payments
still `PENDING` and retries exactly that call, never touching the payment's
own `status`. One column and one sweep cover both "payment succeeded but
confirm failed" and "payment failed/expired but release failed". A partial
index on `(status) WHERE booking_sync_status = 'PENDING'` keeps the sweep
cheap. Progress is visible through WARN/INFO logs; there is no public
endpoint.

### Retry safety

booking-service's `confirmBooking` and `cancelBooking` previously threw on
any non-matching state, so a retry after a lost response would produce a
409. They now return the booking unchanged when it is already `CONFIRMED`
(respectively `CANCELLED`) — the only booking-service change in this step.
A retry therefore never double-books seats, never creates a booking or a
payment, and never re-invokes the payment provider (reconciliation does not
call `PaymentProvider` at all). A `SUCCESS` payment cannot move backward: the
transition table has no outgoing edges from it.

### Idempotency review

The unique idempotency key and the one-live-payment-per-booking index are
unchanged. Replays of a key whose payment is now `PENDING`, `SUCCESS`, or
`EXPIRED` return that payment (200) without re-charging; the same key against
a different booking is still 409; a booking whose only earlier payment is
`FAILED`, `EXPIRED`, or `CANCELLED` accepts a new attempt with a new key.

### Testing note

H2 cannot create partial indexes, so `uq_payments_one_active_per_booking` and
`idx_payments_booking_sync_status_pending` are exercised only by the real
PostgreSQL migrations, not by the H2-backed tests (as in Step 2). Tests set
the sweep intervals to a very large value so the scheduler never fires
mid-test and call the sweep methods directly.

**Still deferred:** a customer-facing abandon-checkout action, a real
provider and webhooks, a frontend payment UI, and booking-service seat-hold
expiry independent of payments.

## 25.4 Integration Validation (Phase 15 Step 4)

Everything below was observed on real running processes with real HTTP
through the Gateway, not inferred from unit tests.

**Environment (and its limits).** All five services, a real Redis (5.0.14
Windows build) and a real PostgreSQL 18.6 were used. `DB_PASSWORD` for the
developer's own PostgreSQL on port 5432 was not available, so instead of
guessing credentials a throwaway PostgreSQL 18.6 cluster on port 5433 was
created outside the repository and migrations 0001–0012 were applied to a
database named `eventtick_db` there. The developer's own instance was
**not** exercised. Payment-service ran with `PAYMENT_EXPIRATION_MINUTES=1`
and 10-second sweeps (environment variables only). Windows note: a Redis
binary installed under `C:\Users\...` makes Spring's Redis health indicator
fail (`Cannot read Redis info`, gateway `/actuator/health` = 503) because
Redis prints its own path in `INFO` and `\u` in `\users` is parsed as a
malformed Unicode escape; run it from a path without `\u`.

**Verified.** Migrations 0001–0012 apply cleanly, the partial unique index
`uq_payments_one_active_per_booking` rejects a second CREATED/PENDING/SUCCESS
payment for a booking but allows FAILED/EXPIRED beside it, every CHECK/FK on
`payments` fires, and 12 up + 12 down migrations round-trip to an empty
schema. Register → login → JWT → `/api/users/me`; 401 for missing, malformed,
tampered and expired tokens; 403 for a customer on `/api/admin/**`. Catalog
create/read through the Gateway. Concurrent seat holds (five users, same
seat, six rounds): exactly one 200 each round. Per-tier rate limits (FREE
16/8, PRO 40/20, PREMIUM 80/40, ADMIN 200/100 burst/replenish observed in
`X-RateLimit-*` headers; JSON 429 with request id). Redis fail-open: with
Redis stopped, requests return 200 with `X-RateLimit-Remaining: -1`,
unauthenticated requests still get 401, login works; limits resume after
Redis restarts. Payments: authoritative amount used (client-supplied
`amount` ignored), SUCCESS confirms the booking, idempotent replay (no
second row, charge or write), same key/different booking 409, forced
failure → FAILED + booking CANCELLED + seat released, expiration by the real
scheduler (PENDING → EXPIRED, booking released, later sweeps no-ops),
reconciliation of a SUCCESS payment whose confirm failed (survived
payment-service and booking-service restarts; ended CONFIRMED / sync DONE
with the same provider reference and one payment row). Gateway: request-id
propagation, 404 JSON, live 504 after the 8-second timeout, 503 when an
upstream is down. Prometheus scraped all five services; Grafana listed all
five and returned payment-service data.

**Defects found and fixed.**
1. *Double booking of a held seat* — `createBooking` only required the seat
   to be `HELD`, and a seat stays `HELD` after a booking exists, so five
   concurrent users each got a PENDING booking on one seat. Fix: under the
   existing row locks, `createBooking` now rejects a seat that already has a
   PENDING/CONFIRMED booking (409 `INVALID_SEAT_STATE`). Re-verified: one
   201 per round across three rounds.
2. *Concurrent same-key payment submits returned 409* — a request whose key
   lookup preceded, but live-payment check followed, the winner's insert saw
   "duplicate live payment". `createPayment` now treats a live payment with
   the same key as a replay (200, showing the in-flight status). Re-verified:
   three concurrent same-key requests → one payment, one charge, all three
   responses carry the same payment id.
3. *Monitoring gap* — `prometheus.yml` did not scrape payment-service; one
   target added.
4. *Timing-dependent gateway test* — `GatewayActuatorTest` sends ~10
   unauthenticated requests from one IP to `/actuator/**`, which is an
   uncategorised path and so uses the fallback rate limit (2/s, burst 5).
   On a fast run it exhausted the bucket and one test saw 429 (seen in 2 of
   4 full-suite runs during this step). Fix: test-only fallback headroom via
   `DynamicPropertySource`, as `AdminRateLimitStatsTest` already does; the
   assertions are unchanged. 240/240 on three consecutive runs afterwards.
   Related product note: the same fallback applies to real actuator traffic,
   so a health prober calling faster than ~2/s from one IP would get 429.

**Known gaps observed, not fixed (design decisions for the owner).**
- ~~booking-service trusts the caller~~ — **fixed, see §25.5.** (Was: any
  authenticated customer could read, list and cancel another user's
  bookings.) Payment creation was never affected — payment-service checks
  ownership itself (403).
- ~~Any authenticated customer can write catalog data through
  `/api/catalog/**`~~ — **fixed, see §25.5.**
- No API creates `show_seats`; a show has no seat availability until rows
  are inserted directly.
- `/api/payments/**` is not classified by the rate limiter, so every tier
  falls back to 2 requests/second, burst 5.
- Holds never expire, and hold ownership is not recorded (§16).
- The gateway aggregate `/actuator/health` is DOWN while Redis is down
  (readiness stays UP, traffic still flows).
- `POST` responses show `createdAt`/`updatedAt` as `null` (columns are
  database-generated and not refreshed after insert); `GET` returns them.
- An unsupported method on payment-service (e.g. `DELETE`) yields 401 rather
  than 405 (error-dispatch through the security chain).

**Not verified:** the developer's own PostgreSQL instance; a real payment
provider; the payment expiry scenario without a database fixture (the mock
provider resolves synchronously, so a PENDING row was inserted directly with
a backdated `created_at`, and the scheduler then acted on it unmodified).

## 25.5 Authorization Fixes (Phase 15 Step 4 follow-up)

The two authorization defects §25.4 reported are fixed.

### Booking ownership (BR-07) — enforced in booking-service

Requirements §21 keeps booking ownership out of the Gateway ("the API
Gateway must not contain business rules such as seat ownership"), so the
rule lives in booking-service, which now identifies the caller the same way
payment-service already does: it validates the user-service-issued JWT
itself (same secret/issuer/audience, `sub` = user id, `role` claim) with a
stateless Spring Security filter chain (`config/SecurityConfig`,
`security/JwtService`, `security/JwtAuthenticationFilter`). Identity is
taken **only** from the validated token — never from a request body or
query parameter.

| Request | Owner CUSTOMER | Other CUSTOMER | ADMIN | No/invalid token |
|---|---|---|---|---|
| `GET /api/bookings/{id}` | 200 | 403 | 200 | 401 |
| `GET /api/bookings` | own bookings | own bookings; `?userId=` of someone else → 403 | all (or `?userId=` user's) | 401 |
| `POST /api/bookings/{id}/cancel` | allowed by the existing state rules | 403 | 403 | 401 |
| `POST /api/bookings` | `userId` in body must equal the caller, else 403 | | | 401 |

A nonexistent booking is 404 for everyone (existence is checked before
ownership), so 404 and 403 stay distinguishable. **Admin cancel is 403**:
FR-39 defines admin booking management as read-only ("no admin
cancel/confirm capability was added"), so an administrator is not exempt from
the owner-only cancel rule. Cancel no longer reads its request body
(`requestingUserId` was never a safe authority); a legacy client that still
sends it is unaffected. Rules live in `BookingService`
(`getBookingForCaller`, `getBookingsForCaller`, `cancelBookingForCaller`);
the booking state machine, seat row-locking and idempotent confirm/cancel
are unchanged. `/api/admin/**` in booking-service additionally requires
`ROLE_ADMIN` (defense in depth behind the Gateway).

**Service-to-service path.** payment-service has no user token (its
reconciliation sweep acts for the system), so booking-service exposes
`GET /internal/bookings/{id}`, `POST /internal/bookings/{id}/confirm` and
`POST /internal/bookings/{id}/cancel` (`InternalBookingController`): the
former system-level behavior, no ownership check, no token. The Gateway has
no route for `/internal/**` (Gateway test: 404 even for an admin), so clients
cannot reach it; like every booking-service endpoint before this change it is
only as private as the service's port. payment-service's
`BookingServiceClient` now uses these paths.

### Booking confirmation is internal-only (final security cleanup)

The public `POST /api/bookings/{id}/confirm` let any authenticated customer
confirm any booking without paying (reproduced live: B confirmed A's unpaid
booking → 200, seat BOOKED, 0 payments — a payment bypass). It is **removed**:
there is no such controller method any more, and both booking-service
(`SecurityConfig`, `requestMatchers("/api/bookings/*/confirm").denyAll()`) and
the Gateway (`GatewaySecurityConfig`, `pathMatchers("/api/bookings/*/confirm").denyAll()`)
deny the path explicitly, for every method and role. Behavior: **403** for any
valid token — customer, another customer, **or admin** (FR-39: admin booking
management is read-only, so admin confirmation is not a capability) — and
**401** for no, malformed, expired or tampered tokens; the request is never
forwarded and never reaches `BookingService`. The lifecycle is therefore:
booking PENDING → payment created → payment SUCCESS durably committed →
payment-service calls `POST /internal/bookings/{id}/confirm` → booking
CONFIRMED, seat BOOKED. Normal confirmation requires a successful payment;
nothing else can perform it. `/internal/**` remains unroutable through the
Gateway (404).

### Catalog management — admin-only at the Gateway

`GatewaySecurityConfig` now requires `ROLE_ADMIN` for `POST`/`PUT`/`PATCH`/
`DELETE` on `/api/catalog/{content,venues,seats,shows}` (and their `/**`
sub-paths), listed explicitly rather than as `/api/catalog/**`. Reads stay
open to any authenticated user; unauthenticated requests are still 401; a
customer's write is 403 from the Gateway's `JsonAccessDeniedHandler` and is
never forwarded; the `/api/admin/**` routes and their rule are untouched.

**Verification.** Unit/slice tests: booking-service 78, gateway 251
(payment-service 98, user 52, catalog 75 unchanged in count). Live through
the Gateway with two customers and an admin: A read/listed/cancelled their
own booking; B got 403 reading it, 403 cancelling it (with `requestingUserId`
set to B or spoofed to A), 403 creating a booking as A and 403 listing
`?userId=A`; admin read and listed everything (200) but got 403 cancelling;
nonexistent bookings were 404; missing tokens 401. Payments still worked
end to end over `/internal/**` (SUCCESS → booking CONFIRMED; forced failure
→ booking CANCELLED, seat released). Catalog: a customer's GETs were 200, all
13 write attempts across content/venues/seats/shows (POST/PUT/PATCH/DELETE)
were 403 with row counts unchanged, and admin writes on both `/api/catalog/**`
and `/api/admin/**` still worked.

**Payment-bypass defect found while fixing this — since fixed** (see
"Booking confirmation is internal-only" above): `POST /api/bookings/{id}/confirm`
was reachable by any authenticated customer and has been removed.

**Live re-verification of the fix** (two customers + admin through the
Gateway, fresh booking owned by A): public confirm by A, B and admin → 403;
no/malformed/expired/tampered token → 401; `/internal/bookings/{id}/confirm`
through the Gateway → 404; direct to booking-service with a valid customer or
admin token → 403; after all attempts the booking was still PENDING, the seat
HELD and no payment existed. Then A paid: payment SUCCESS → booking
CONFIRMED, seat BOOKED, sync DONE; a `FORCE_FAIL_` payment → FAILED, booking
CANCELLED, seat AVAILABLE; an expired PENDING payment → EXPIRED, booking
CANCELLED, seat AVAILABLE; and a SUCCESS payment whose confirm was rejected
was reconciled to CONFIRMED / BOOKED / sync DONE across a restart of both
payment-service and booking-service, with the same provider reference and one
payment row.

---

# 26. Synchronous Communication

REST APIs are used when an immediate response is required.

Example:

```text
Frontend
   │
   ▼
Gateway
   │
   ▼
Catalog Service
   │
   ▼
Response
```

Suitable operations include:

* Searching content
* Retrieving content
* Retrieving shows
* Checking seat availability
* Creating a booking

---

# 27. Asynchronous Communication

A message broker can be introduced for asynchronous workflows.

Potential technology:

* Apache Kafka

Potential events:

```text
BookingCreated
BookingCancelled
PaymentCompleted
PaymentFailed
SeatHeld
SeatReleased
```

Conceptually:

```text
Booking Service
       │
       ▼
BookingCreated
       │
       ▼
Message Broker
       │
   ┌───┼─────────────┐
   ▼   ▼             ▼
Analytics  Notification  Other Consumers
```

Kafka is not required for the initial synchronous implementation.

It should be introduced when there is a clear asynchronous requirement.

---

# 28. Event-Driven Architecture

Potential domain events include:

```text
BookingCreated
BookingCancelled
PaymentCompleted
PaymentFailed
SeatHeld
SeatReleased
```

Events allow independent components to react to changes without requiring every operation to be synchronously coupled.

Event processing should be designed to tolerate duplicate delivery.

---

# 29. Monitoring Architecture

The monitoring stack is:

```text
Microservices
      │
      │ Metrics
      ▼
 Prometheus
      │
      ▼
   Grafana
```

Metrics can include:

* Request count
* Request latency
* Error rate
* HTTP status codes
* Rate-limit decisions
* Rate-limit rejections
* Booking attempts
* Successful bookings
* Failed bookings
* Redis latency
* Database connection usage
* Service health
* Policy refresh status

The monitoring system should help answer:

```text
What happened?
How often?
How long did it take?
Which service is responsible?
Are rate limits working?
Are dependencies healthy?
```

---

# 30. Logging Architecture

Each service should generate structured logs.

Conceptual information:

```text
timestamp
service
request_id
route
method
status
duration
error
```

The same request ID should be propagated where practical.

Sensitive information must not be logged.

Do not log:

* Passwords
* Access tokens
* Refresh tokens
* Payment credentials
* Other sensitive credentials

---

# 31. Data Science / Analytics Architecture

The ML component is intentionally separated from transactional services.

```text
Booking Service
       │
       ▼
Booking Data
       │
       ▼
Analytics / ML Layer
       │
       ├── Demand Analysis
       ├── Traffic Analysis
       ├── User Behavior
       ├── Demand Forecasting
       └── Recommendations
```

Potential technology:

* Python
* Pandas
* NumPy
* Scikit-learn
* FastAPI

Possible future use cases:

* Predict demand for a show
* Analyze peak booking periods
* Identify high-demand venues
* Forecast ticket demand
* Recommend events
* Analyze traffic patterns

The ML service should not directly modify core booking records.

ML is a later extension and is not required for the Gateway to function.

---

# 32. Frontend-to-Backend Booking Flow

Example: a user wants to book a seat.

```text
1. User selects content
            │
            ▼
2. User selects show
            │
            ▼
3. User selects seat
            │
            ▼
4. Frontend sends booking request
            │
            ▼
5. API Gateway
            │
            ├── JWT validation
            ├── Authorization
            ├── Rate-limit check
            └── Request routing
                        │
                        ▼
6. Booking Service
            │
            ▼
7. Check seat availability
            │
            ▼
8. Acquire temporary hold / lock
            │
            ▼
9. Create booking transaction
            │
            ▼
10. Payment workflow if enabled
            │
            ▼
11. Confirm booking
            │
            ▼
12. Return response
```

The exact payment and hold sequence will be finalized during Booking Service implementation.

---

# 33. Why the Gateway Does Not Handle Booking Logic

The Gateway is responsible for **traffic and access management**.

The Booking Service is responsible for **business logic**.

Therefore:

```text
Gateway

→ Is this request authenticated?
→ Is this request authorized?
→ Is this request within the traffic policy?
→ Where should it be routed?
```

while:

```text
Booking Service

→ Is the seat available?
→ Can the seat be held?
→ Can the booking be created?
→ Has the booking already been processed?
```

This separation keeps the architecture maintainable.

---

# 34. Scalability

The architecture supports independent service scaling.

For example:

```text
                 API Gateway
                      │
          ┌───────────┼───────────┐
          ▼           ▼           ▼
      Booking      Booking      Booking
      Instance     Instance     Instance
```

Multiple instances of a service can process requests when traffic increases.

Shared infrastructure such as Redis and PostgreSQL must also be configured appropriately as the system scales.

The rate limiter must use shared state so that multiple Gateway instances do not independently grant the same user a separate allowance.

---

# 35. Fault Isolation

Microservices provide boundaries between application components.

For example:

```text
Catalog Service
      ↓
    Working

Booking Service
      ↓
    Working

Payment Service
      ↓
Temporarily unavailable
```

The system can use:

* Timeouts
* Controlled retries
* Circuit breakers
* Idempotency
* Dead-letter queues for asynchronous processing

as the project evolves.

Failure behavior must be explicitly defined rather than allowing uncontrolled retries or indefinite waiting.

---

# 36. Security Architecture

Security controls include:

```text
HTTPS
  ↓
API Gateway
  ↓
JWT Authentication
  ↓
Authorization
  ↓
Rate Limiting
  ↓
Input Validation
  ↓
Microservices
  ↓
Database
```

Additional practices:

* Password hashing
* Secure secret management
* Environment variables
* Database access controls
* Safe error responses
* No sensitive information in logs
* Backend services should not be publicly exposed in the production architecture
* Validation of user input
* Resource ownership checks

---

# 37. Deployment Architecture

Docker will be introduced after the core services are functional.

A local Docker environment may eventually contain:

```text
┌───────────────────────────────────────┐
│                Docker                 │
│                                       │
│ API Gateway                           │
│ User Service                          │
│ Catalog Service                       │
│ Booking Service                       │
│ Payment Service (optional)            │
│ PostgreSQL                            │
│ Redis                                 │
│ Kafka (optional)                      │
│ Prometheus                            │
│ Grafana                               │
│                                       │
└───────────────────────────────────────┘
```

Docker Compose can initially be used for local orchestration.

Not every component needs to be introduced at the beginning.

---

# 38. Repository Architecture

The target repository structure is:

```text
tichboo/

│
├── README.md
│
├── docs/
│   ├── requirements.md
│   ├── architecture.md
│   ├── database-design.md
│   └── api-contracts.md
│
├── frontend/
│
├── services/
│   ├── api-gateway/
│   ├── user-service/
│   ├── catalog-service/
│   ├── booking-service/
│   └── payment-service/
│
├── ml-service/
│
├── infrastructure/
│   ├── docker/
│   ├── prometheus/
│   └── grafana/
│
└── docker-compose.yml
```

This is the **target structure**.

The directories do not need to exist immediately.

They will be created progressively as each phase is implemented.

---

# 39. Development Sequence

The project will be implemented progressively:

```text
Requirements
      ↓
Architecture
      ↓
Database Design
      ↓
API Contracts
      ↓
Backend Project Structure
      ↓
Frontend
      ↓
Authentication
      ↓
Catalog Service
      ↓
Booking Service
      ↓
API Gateway
      ↓
Redis
      ↓
Rate Limiting
      ↓
Dynamic Rate Limiting
      ↓
Booking Concurrency
      ↓
Admin Policy Management
      ↓
Monitoring
      ↓
Optional Payment
      ↓
Optional Kafka
      ↓
Optional ML
      ↓
Docker
      ↓
Deployment
```

Each phase should be functional and understandable before introducing the next major component.

---

# 40. Architectural Boundaries

| Component       | Primary Responsibility                                     |
| --------------- | ---------------------------------------------------------- |
| Frontend        | User interaction                                           |
| API Gateway     | Routing, authentication, authorization, traffic management |
| User Service    | User and account management                                |
| Catalog Service | Content, shows, venues, and catalog information            |
| Booking Service | Seats, holds, bookings, and concurrency                    |
| Payment Service | Payment workflow                                           |
| Audit Service   | Kafka-consumer-only audit projections (§47–§50); no Gateway route, no synchronous callers |
| Redis           | Rate-limit state only (§20.1). Not used for seat holds — those are plain PostgreSQL row locks, §16 |
| PostgreSQL      | Persistent business data                                   |
| Message Broker  | Asynchronous events                                        |
| Prometheus      | Metrics collection                                         |
| Grafana         | Metrics visualization                                      |
| ML Service      | Analytics and machine learning                             |

---

# 41. Core Data Entities

The initial domain model contains:

```text
User
Plan
Content
Venue
Seat
Show
Booking
BookingSeat
RateLimitPolicy
```

Optional later entities include:

```text
Payment
RefreshToken
PolicyAudit
Notification
Event
```

The important architectural change is that **Movie, Match, Concert, and Event are represented as types of Content rather than separate booking models**.

Example:

```text
Content
│
├── MOVIE
├── SPORTS_MATCH
├── CONCERT
├── THEATRE
└── EVENT
```

---

# 42. High-Level Data Relationships

The initial relationships are:

```text
Plan
  │
  └──< User

Content
  │
  └──< Show

Venue
  │
  ├──< Seat
  │
  └──< Show

Show
  │
  └──< Booking

Booking
  │
  └──< BookingSeat

Seat
  │
  └──< BookingSeat

Plan
  │
  └──< RateLimitPolicy
```

Meaning:

* A Plan can be associated with many Users.
* A User can create many Bookings.
* A Content item can have many Shows.
* A Venue can contain many Seats.
* A Venue can host many Shows.
* A Show can have many Bookings.
* A Booking can contain multiple Seats through BookingSeat.
* A Plan can have multiple RateLimitPolicies.

---

# 43. Initial Entity Attributes

## User

```text
User
----------------
id
name
email
password_hash
role
plan_id
created_at
updated_at
```

## Plan

```text
Plan
----------------
id
name
price
description
created_at
updated_at
```

## Content

```text
Content
----------------
id
type
title
description
language
duration
genre
release_or_event_date
created_at
updated_at
```

Example `type` values:

```text
MOVIE
SPORTS_MATCH
CONCERT
THEATRE
EVENT
```

## Venue

```text
Venue
----------------
id
name
address
city
created_at
updated_at
```

## Seat

```text
Seat
----------------
id
venue_id
section
row
seat_number
seat_type
```

## Show

```text
Show
----------------
id
content_id
venue_id
start_time
end_time
status
created_at
updated_at
```

## Booking

```text
Booking
----------------
id
user_id
show_id
status
total_amount
created_at
updated_at
```

## BookingSeat

```text
BookingSeat
----------------
id
booking_id
seat_id
price
```

The database will enforce appropriate uniqueness and relationship constraints during Phase 2.

## RateLimitPolicy

```text
RateLimitPolicy
----------------
id
plan_id
route_group
refill_rate
capacity
request_cost
enabled
version
updated_at
```

---

# 44. Current Status

Completed, beyond the original Phase 1 scope below — see
`docs/requirements.md` §25 for the equivalent detailed list:

* Full PostgreSQL schema (`database/migrations/0001`–`0010`), matching
  the ownership boundary in §17.
* Backend project structure for all four services (`backend/`), each an
  independently buildable Spring Boot 3.3.4 / Java 17 Maven project.
* Catalog Service and Booking Service fully implemented end to end
  (entities → repositories → services → DTOs → REST controllers →
  exception handling), manually tested.
* User Service authentication backend (§23 Authentication Architecture,
  realized): registration, login, JWT issuance/validation via Spring
  Security, BCrypt password hashing, `CUSTOMER`/`ADMIN` role authority,
  authenticated `GET /api/users/me`. Refresh tokens (§7's "refresh-token
  management") are explicitly deferred — not yet implemented; access-token
  authentication alone is the complete, tested increment for now.
* Eventtick frontend prototype (§4). Its authentication (signup, login,
  session restore, logout) now goes through the API Gateway to the User
  Service (Phase 7.2). *(Stale as of Phase 17 — retained as the Phase 7.2
  snapshot; by Phase 17 the core catalog/show/seat/booking/payment path is
  also real, not mock. See §51.)*

The API Gateway (§5–§6) — the project's stated primary engineering
focus — is partly built: **basic path-based routing** to the three
services (Phase 7.1), **CORS for the browser frontend** (Phase 7.2),
**JWT authentication at the edge** (Phase 7.3), **request correlation ids
with controlled error responses** (Phase 7.4), **upstream timeout
protection** (Phase 7.5), **Redis-backed rate limiting** (Phase 11, static),
and now **dynamic policy selection** for that rate limiting (Phase 12) are
implemented in `backend/gateway-service`, and the frontend's authentication
now goes through it.

How gateway authentication works (Phase 7.3): the User Service remains the
only issuer of JWTs. For every request except `POST /api/auth/register` and
`POST /api/auth/login`, the gateway validates the token *before* forwarding
— HMAC signature with the shared `JWT_SECRET`, plus expiry, issuer and
audience, the same checks the User Service applies — and answers `401`
itself, without contacting any backend, when the token is missing or
invalid. Valid requests are forwarded with their `Authorization` header
unchanged, and the User Service still validates it again for `/api/users/me`.
This is authentication only: the gateway does not decide who may do what.
Authorization policy (roles, ownership) stays service-specific, and for now
`/api/catalog/**` and `/api/bookings/**` simply require *a* valid token.
The `role` and `plan` claims are available in the gateway's authentication
context for later phases.

How request correlation and error handling work (Phase 7.4): every request
through the gateway carries an `X-Request-ID` header — the caller's own value
if it supplied one and it looks safe, otherwise a generated random UUID —
forwarded to the upstream service and echoed on the response, including on
the gateway's own 401/403/404/5xx responses. Every error the gateway
generates itself (not one produced by a backend service) uses one JSON shape
— status, a fixed error code, a fixed human-readable message, the request id,
a timestamp — and never includes a stack trace, an exception's class or
message, a JWT's contents, the signing secret, or an internal host/path.
Request logging is limited to the id, method, path, status and duration;
the query string, headers, `Authorization`, JWTs and passwords are never
logged.

How upstream timeout protection works (Phase 7.5): the gateway bounds how
long it will wait on User Service, Catalog Service, and Booking Service, so
a request fails fast and predictably instead of hanging when a service is
down or stuck. A connection-timeout (default 3s) bounds opening the TCP
connection to the upstream, and a response-timeout (default 8s) bounds
waiting for the upstream's response once the request has been sent — both
conservative values for local development, not aggressive production ones,
and both configured once (`spring.cloud.gateway.httpclient`) so they apply
uniformly to all three routes without per-route repetition. A failure is
reported through the same Phase 7.4 error mechanism and JSON shape: a
refused/unreachable connection is a 503, a timeout is a 504, and any other
upstream I/O failure is a 502 — never with the underlying exception, an
internal hostname, or a port in the response. This is timeout protection
only: it is not a circuit breaker, it does not retry a failed request, and
it does not recover a service automatically — those remain future work.

How rate limiting works (Phase 11): Redis is now part of the gateway's
architecture, as the shared state a request counter needs when more than
one gateway instance is running — an in-memory counter would let each
instance grant its own separate allowance, so the token bucket lives in
Redis instead (Spring Cloud Gateway's own `RedisRateLimiter`, one atomic
Lua script per check). Rate limiting sits after JWT authentication and
before routing (`Client -> Gateway -> JWT authentication -> Rate limiter ->
Route`), and belongs at the gateway rather than in each service for the
same reason authentication, request correlation, and timeout protection
do: it is the one place already doing this kind of cross-cutting work for
every request, so User Service, Catalog Service, and Booking Service do not
each need their own Redis client and rate-limiting logic.

The identity half of the bucket key distinguishes authenticated callers
from everyone else, and is unchanged since Phase 11. A request carrying a
JWT the gateway already validated is keyed by that token's `sub` claim (one
bucket per user, regardless of device or IP); every other request — the
public register/login endpoints, or anything else the gateway did not
authenticate — is keyed by the caller's actual TCP source address, not a
client-supplied header such as `X-Forwarded-For`, since there is no
trusted reverse proxy in front of the gateway yet and trusting a
client-supplied identity would let a caller pick a fresh bucket at will.

If Redis itself is unreachable, the limiter fails open (the request is
allowed, not blocked) rather than a Redis outage becoming a full API
outage; short Redis connect/command timeouts
(`spring.data.redis.timeout`/`.connect-timeout`, 300ms by default) keep
that discovery fast. A request over the limit gets `429 Too Many Requests`
in the same JSON error shape Phase 7.4 established
(`status`/`error`/`message`/`requestId`/`timestamp`), with `X-Request-ID`
and CORS headers intact, plus the conventional (not IETF-standardized)
`X-RateLimit-*` headers on every response so a well-behaved client can back
off before it is ever limited.

How dynamic policy selection works (Phase 12): Phase 11's one shared
numeric policy is replaced by a policy chosen per request from two
independent inputs, resolved in this order:

1. **Request category** — a deterministic classification of the path alone
   (`AUTH`/`CATALOG`/`BOOKING`/`USER`/`UNKNOWN`), independent of which
   Spring Cloud Gateway route id matched. `UNKNOWN` (an unrecognized path)
   short-circuits straight to a fixed, conservative fallback policy — an
   unrecognized endpoint is never left unlimited.
2. **Authentication state** — no validated JWT -> the `PUBLIC` tier.
3. **Role** — an authenticated request with `role=ADMIN` -> the `ADMIN`
   tier, checked *before* plan and regardless of it. `ADMIN` is a role,
   `Free`/`Pro`/`Premium` are plans — two independent columns on the User
   Service's `users` table (a `UserRole` enum and a `plans` foreign key), so
   an account can in principle be an admin on any plan; role wins because it
   represents operational trust, not purchased capacity.
4. **Plan** — otherwise, the JWT's `plan` claim, matched case-insensitively
   against the plans that actually exist (`Free`, `Pro`, `Premium` — see
   `database/migrations/0003_seed_plans.up.sql`; not the frontend's own,
   unrelated mock data, which also lists a `VIP` plan with no backend
   counterpart) -> the matching tier.
5. **Fallback** — a missing or unrecognized `plan` claim resolves to `FREE`
   (the JWT decoder does not structurally validate this claim, only `role`
   and `sub` are validated, so an unrecognized value must never be silently
   read as a higher tier); and if the resolved (category, tier) pair simply
   has no configured policy, the same conservative fallback policy from
   step 1 applies.

Only the validated JWT's claims are ever consulted — never a client-supplied
header, query parameter, or body field, so a caller sending `X-Plan: PRO`
or `X-Role: ADMIN` gets exactly the policy their real, signed token implies
and nothing more.

The category/tier matrix (`eventtick.rate-limit.policies.*` in
`application.yml` — a dedicated namespace, since
`spring.cloud.gateway.redis-rate-limiter.*` only has room for one flat
policy and is retired) holds illustrative development numbers, not
production-certified ones: `CATALOG` (read-heavy) is the most generous
category at every tier, `BOOKING` (seat-lock contention) the strictest
authenticated one, `USER` in between, and `AUTH` has only a `PUBLIC` entry
(the strictest policy of all, IP-keyed) since the public chain never
authenticates a register/login request in the first place. Across tiers,
`PRO` is roughly 2–2.5× `FREE`, `PREMIUM` roughly 2× `PRO`, and `ADMIN` is
generously high everywhere for operational/support work, not as a bypass.
Changing the matrix needs a restart — there is no runtime/admin-configurable
policy management in this phase (Phase 13+ territory).

Two implementation findings worth recording because they shaped the
design: first, reading Spring Cloud Gateway 4.1.5's own source shows
`RedisRateLimiter.isAllowed(routeId, id)` uses `routeId` only to select
which numeric `Config` applies — the actual Redis key is built from `id`
alone — so the bucket key actually used is `<policyId>:<identity>`, not
just the identity, or two different policies applied to the same caller
would silently share one bucket. Second, the Phase 11 rate limiter was
built as a Spring Cloud Gateway `GlobalFilter`, which (discovered while
testing the `UNKNOWN`-category fallback) never runs at all for a path that
matches no route — the same limitation Phase 7.4's `RequestIdWebFilter`
already worked around for 401s/404s. The limiter is now a plain
`WebFilter`, ordered to run after Spring Security's chain (so the
authenticated identity is available) but for every request regardless of
whether a route exists.

This remains authentication-driven, JWT-only policy selection — there is
still no admin-configurable or database-backed runtime policy management,
no adaptive/system-load-based limiting, and no machine-learning-based rate
limiting; all explicitly future work beyond Phase 12. *(Stale as of Phase
17 — true when Phase 12 was written; by Phase 17 the frontend's core
booking/payment path is real and does use the Gateway. See §51.)*

**Redis testing (Phase 11, still used by Phase 12's tests):** this development machine has neither Docker
nor an installed WSL distribution, so Testcontainers — the usual way to get
a real, disposable Redis for tests — was not an option here. Rather than
weaken the tests to a hand-rolled fake of the Redis protocol (which would
not actually prove the Lua-script token bucket works), the gateway's tests
run a genuine `redis-server` binary via `com.github.codemonstur:embedded-redis`
(a maintained fork that bundles native builds for Windows/macOS/Linux),
started fresh per test class and stopped afterward — a real Redis process,
just disposable and test-scoped, exercising the exact code path production
does. A project with Docker or WSL available should prefer a Testcontainers
Redis module instead; this choice was made specifically because that
wasn't available in this environment, not as a general recommendation.

Original Phase 1 — Requirements & Architecture — completed:

* Project scope
* High-level requirements
* High-level architecture
* Service boundaries
* Gateway responsibilities
* Database ownership direction
* Booking/concurrency requirements
* Technology direction
* Development sequence
* GitHub repository setup
* Initial database entities

The architecture has now been generalized from a movie-specific application to a multi-category ticket-booking platform.

---

# 45. Admin Dashboard Architecture (Phase 13 — Complete)

**All six Phase 13 Admin Dashboard requirements are implemented.** This
section documents the architecture for the Admin Dashboard's backend API
surface; the table below tracks each requirement's status. See
`docs/requirements.md` §15 (FR-35–FR-40) for the corresponding numbered
requirements and their status.

| Requirement | Status |
|---|---|
| FR-35 Admin Dashboard Access Control | **Complete** |
| FR-36 Admin Overview and Statistics | **Complete** (backend + frontend) |
| FR-37 Admin User Management | **Complete** (backend) |
| FR-38 Admin Event/Show Management | **Complete** (backend) |
| FR-39 Admin Booking Management | **Complete** (backend) |
| FR-40 Admin Rate-Limit Visibility | **Complete** (backend + frontend) |

**Frontend coverage is partial by design, not by omission.** The `/admin`
page has two real, backend-connected sections: "Platform Overview" (FR-36
— five stat cards, fetched from the three owning services'
`.../stats` endpoints through the Gateway) and "Traffic & Rate Limiting"
(FR-40 — Redis status, allowed/rejected activity by policy, and the
configured policy matrix, fetched from the Gateway's own
`.../rate-limits/stats` endpoint). FR-37/FR-38/FR-39's admin write and
monitoring operations (user plan/role management, Content/Show/Venue
management, booking/seat-activity monitoring) have no frontend UI yet —
those endpoints are implemented and tested backend API surface only,
reached through the Gateway exactly like any other endpoint, with no
corresponding admin UI built for them in Phase 13.

## 45.1 Purpose and Access

The Admin Dashboard is an operational interface for users whose JWT `role`
claim is `ADMIN` — not a new role or a new kind of account, the same
`ADMIN` already defined in §4.2/§23–§24 and carried in the JWT `role`
claim since the User Service issued its first token.

Access is enforced through the **existing** JWT authentication/authorization
architecture, not a parallel one:

```text
Admin Frontend
      │
      ▼
API Gateway :8080
      │
      ├── JWT authentication (§6.2, §23)      — valid token required
      ├── Coarse-grained authorization (§6.3) — role must be ADMIN
      │      │
      │      ├── CUSTOMER  → 403 Forbidden
      │      └── no/invalid token → 401 Unauthorized
      │
      ▼
   ADMIN request forwarded to the owning service
```

A `CUSTOMER` request to an admin-only operation receives `403 Forbidden`;
an unauthenticated request receives `401 Unauthorized` — the same
distinction, and the same response shape (`status`/`error`/`message`/
`requestId`/`timestamp`), Phase 7.3/7.4 already established for every
other protected endpoint. No separate error format is introduced.

## 45.2 Dashboard Capabilities

| Capability | Description |
|---|---|
| Overview / statistics | Summary counts (users, content/shows, bookings) and current rate-limit activity, each sourced from the service that owns it — see §45.3. |
| User management | View/list users; change a user's plan or role. |
| Event/show management | Create, update, and remove Content, Shows, and Venues. |
| Booking management | View/list bookings and their status; view seat-hold/booking activity for a show. |
| Rate-limit visibility | View the active Phase 12 policy matrix and recent rate-limit activity. **Read-only** — see §45.4. |

## 45.3 Service Ownership and Admin API Boundaries

The Admin Dashboard introduces no new service and no new data ownership.
Every admin operation is served by whichever service already owns that
data (§17's boundary, unchanged):

```text
Gateway Service   → routing, JWT enforcement, request correlation,
                     error handling, rate limiting (unchanged: §6, §19–§22)

User Service      → users, plans
                     admin operations: list/view users, change plan/role

Catalog Service   → content, venues, seats, shows
                     admin operations: create/update/remove content,
                     shows, venues

Booking Service   → show_seats, bookings, booking_seats
                     admin operations: list/view bookings, view seat-hold
                     and booking activity

Gateway Service   → rate-limit policy matrix (Phase 12), request/response
                     metadata
                     admin operation: view (not edit) the active policy
                     matrix and recent 429 activity
```

Concretely, an admin API call is `Admin Frontend -> API Gateway -> the
owning service`, exactly the same path a customer-facing request already
takes (§26, §32) — the Gateway does not gain a new role as a data owner,
and the Admin Dashboard's frontend never talks to a backend service
directly (consistent with FR-19, Centralized Entry Point). **One
deliberate exception**: FR-40's `GET /api/admin/rate-limits/stats` is
served directly *by* the Gateway itself, since the Gateway is the actual
owner of the rate-limit policy matrix and activity counters — there is no
"owning service" further downstream to forward to. See §45.5.

No `admin-service` is introduced. Admin-only *authorization* is enforced
once, at the Gateway (§45.1); each service still performs its own
business-specific validation of the operation itself (§6.3's "coarse
vs. business-specific" split, unchanged).

## 45.4 Explicit Non-Goals for Phase 13

The following are deliberately out of scope for this phase, to be
reconsidered only in a later phase if at all:

* A new `admin-service` — admin operations are served by the existing
  three services (§45.3).
* Runtime editing of rate-limit policies. The Phase 12 policy matrix
  remains configuration-driven (`application.yml`/environment variables,
  restart to change); the dashboard exposes it read-only (§45.2, FR-40).
  FR-31 (administrative rate-limit configuration) stays unimplemented.
* Payment, Kafka/event-driven communication, data science/ML, monitoring
  (§29–§31, §25, §27–§28 remain as previously documented, unaffected by
  this section), and Docker/deployment changes.

## 45.5 FR-40: Gateway Rate-Limit Visibility (Implementation Notes)

`GET /api/admin/rate-limits/stats` is gateway-service's first **locally
served** admin endpoint — a real `@RestController` (`AdminRateLimitController`),
not a proxied route, and deliberately has no `application.yml` route entry.
The existing `/api/admin/** -> hasAuthority("ROLE_ADMIN")` rule in
`GatewaySecurityConfig` already protects it unchanged: that rule is
enforced by the reactive `SecurityWebFilterChain`, which runs regardless of
whether a request is ultimately served by a proxied route or a local
controller — no new security configuration was needed. Full endpoint
contract: `docs/api-contracts.md`.

Two independent data sources, matching FR-40's own two asks:

* **The active policy matrix** (`policies`/`fallback` in the response) —
  read live from the existing `RateLimitPolicyProperties` bean (bound from
  `eventtick.rate-limit.*` at startup, Phase 12). No new state; nothing
  hardcoded.
* **Current activity** (`activity` in the response) — new, deliberately
  minimal Redis counters (`RateLimitActivityRecorder`), incremented from
  the single existing `RateLimitingGlobalFilter.respond()` method (the one
  place that already sees every rate-limit decision) as a purely
  observational side effect — the token-bucket `isAllowed`/`429` decision
  itself, its headers, and its JSON error body are all unchanged.

**A completely separate Redis namespace** (`gateway:admin:rate-limit-activity:{policyId}:{allowed|rejected}`,
plain `INCR`) from `RedisRateLimiter`'s own internal token-bucket keys
(`request_rate_limiter.{id}.tokens`/`.timestamp`, an undocumented Spring
Cloud Gateway implementation detail) — the two are never read across, by
design, so the admin feature can never become unsafely coupled to
rate-limiting internals that could change between Spring Cloud Gateway
versions.

**Since-startup counters, not historical analytics.** They represent
"activity since this gateway instance's counters were initialized" — no
sliding time window, no TTL/time-bucketing, no permanent record; a restart
resets them to zero. This is a deliberately minimal reading of FR-40's
"current traffic/rate-limit activity," consistent with this section's own
non-goals above.

**Redis unavailable → the endpoint still returns `200`, never `5xx`.**
Recording (writes) is fire-and-forget and non-blocking — a Redis failure
there is logged and swallowed, and can never fail the *original* request
being rate-limited, the same fail-open principle already established for
`RedisRateLimiter` itself (§19–§22). Reading (the stats endpoint itself)
degrades gracefully: `policies`/`fallback` are unaffected (they need no
Redis), and `activity.redisAvailable = false` with an empty `byPolicy`
replaces the counters rather than the request failing.

**No business data.** The new counters hold only a policy id
(`CATEGORY:TIER`, a Gateway-internal classification label) and two
integers — never a `userId`, `bookingId`, or other domain identifier. The
Gateway remains an operational component, not a data owner for any
business entity (§33), extended here to "the Gateway does not accumulate
business data" for FR-40 specifically.

# 46. Monitoring (Phase 14 — Step 1: Actuator health + Prometheus metrics)

**Phase 14 is not complete.** This section covers Step 1 only. Before this
step, the project had no Actuator/Micrometer/Prometheus dependency, no
health endpoint, and no Docker/Compose anywhere; the only existing
observability was Gateway-local (`RequestIdWebFilter`'s per-request log
line, `GatewayErrorHandler`'s 5xx logging, and FR-40's
`RateLimitActivityRecorder` — see §6.6, §6.7, §45.5). user-service,
catalog-service, and booking-service had, and outside of this step's health
endpoint still have, zero custom log statements.

## 46.1 What Step 1 added

`spring-boot-starter-actuator` and `micrometer-registry-prometheus` were
added to all four backend services (`gateway-service`, `user-service`,
`catalog-service`, `booking-service`), each now exposing, on its own port:

- `GET /actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`
- `GET /actuator/prometheus` (standard Prometheus text exposition format;
  built-in `http_server_requests`/JVM/process/system metrics only — no
  custom metric has been added yet)

`management.endpoints.web.exposure.include` is explicitly `health,prometheus`
on every service (never `*`), so no other Actuator endpoint id (`env`,
`beans`, `configprops`, `mappings`, `loggers`, `heapdump`, ...) is registered
or reachable.

**Service-local, never proxied.** There is deliberately no
`/actuator/**` entry in `gateway-service`'s `application.yml`
`spring.cloud.gateway.routes` list — a request to the Gateway's own
`/actuator/health` is answered by `gateway-service` itself, and there is no
gateway-level aggregation of the other three services' health/metrics.

**Security.** `gateway-service` and `user-service` (the two services with
their own Spring Security filter chain) each got a narrow, explicit
allowlist for exactly the four paths above — not a blanket `/actuator/**`
permit — added to `GatewaySecurityConfig`/`SecurityConfig` respectively;
every other existing authentication/authorization rule (`/api/admin/**` →
`ROLE_ADMIN`, everything else → any valid JWT) is unchanged. catalog-service
and booking-service have no Spring Security dependency at all — every
endpoint there, including `/api/admin/**`, is already open at the service
level (the Gateway is what enforces role/JWT for those two), so Actuator
needed no new permit to be reachable there. *(As of the Phase 15 Step 4
follow-up, §25.5, that is no longer true of booking-service: it validates
JWTs and its `SecurityConfig` allowlists the same four Actuator paths.)*

**Readiness and the existing fail-open behavior.** Actuator's `readiness`
group only contains the `readinessState` app-availability marker by default
(unrelated to any dependency) — for user/catalog/booking-service, `db` was
added to it explicitly (`management.endpoint.health.group.readiness.include:
readinessState,db`) so the auto-configured `DataSourceHealthIndicator`
against the existing PostgreSQL connection actually participates in
readiness. For gateway-service, readiness was left at the default; Redis
connectivity instead participates in the top-level aggregate
`/actuator/health` automatically (no group configuration needed) via the
auto-configured Redis health indicator against the existing
`spring.data.redis.*` connection — **diagnostic only**: a `DOWN` Redis status
does not change `RateLimitingGlobalFilter`'s existing fail-open behavior
(§19–§22), does not cause Gateway requests to be rejected, and does not
affect `/actuator/health/readiness` specifically. No rate-limit, JWT, CORS,
timeout, or FR-40 (§45.5) behavior was changed by this step. No custom
`HealthIndicator` code was written for any of the four services.

**Testing note.** The embedded test-Redis used by `GatewayActuatorTest`
(`com.github.codemonstur:embedded-redis`) returns an `INFO` response Spring
Data Redis 3.3.4's health indicator can't parse, so the aggregate
`/actuator/health` reports `DOWN` under that specific test double even
though Redis is fully functional there for rate limiting (`INCR`/`EVAL`,
which don't call `INFO`) — a known limitation of that lightweight test-only
redis-server distribution, not of real Redis or of this project's code.

## 46.2 Explicitly not part of Step 1

No Prometheus server, no Grafana dashboard, no custom `rate_limit.requests`
Micrometer counter, no distributed tracing, no alerting, and no
Docker/Compose packaging for any of the above — all deferred to later Phase
14 steps.

## 46.3 Local Prometheus Scraping (Step 2)

**Prometheus is a separate monitoring component, not an Eventtick service.**
`monitoring/prometheus.yml` configures a Prometheus *server* — a standalone
process you run yourself locally (see `monitoring/README.md` for exact,
Windows-specific instructions) — to scrape the four `/actuator/prometheus`
endpoints Step 1 added. No Eventtick service reads this file, starts
Prometheus, or depends on it in any way; nothing added in this step changes
application behavior.

**Static targets, no service discovery.** Consistent with §46's original
proposal ("standalone Prometheus instance with static scrape targets,
because the project currently has no service-discovery infrastructure"),
the config lists fixed `host:port` targets, each labeled with its
`service` name for easier querying. One job (`eventtick-services`),
`metrics_path: /actuator/prometheus` (the same Step 1 endpoint, not a new
one), scrape interval `15s`. Moving any service off `localhost`, or to a
container, or running multiple instances, means hand-editing these
targets — there is no Consul/Eureka/Kubernetes discovery mechanism in
this project.

**Updated target list (as of Phase 17 documentation sync) — five of six
services, not the four this step originally added.** `payment-service`
(`localhost:8084`) was added to `monitoring/prometheus.yml` after this
step, once Phase 15 gave it the same Actuator/Micrometer setup as the
original four. **`audit-service` (`localhost:8085`, added Phase 16 Step
3) has the identical Actuator/Micrometer dependency and `/actuator/
prometheus` endpoint as every other service, but is not in
`monitoring/prometheus.yml`'s `static_configs` at all** — a real,
confirmed gap (metrics exist and are reachable; they are simply not
scraped), not a missing capability. Closing it is a one-line addition to
that file, not yet made.

**Prometheus talks to each service directly — it does not go through the
Gateway.** It scrapes `localhost:8080`–`8083` one by one, the same way any
other local HTTP client would; `gateway-service`'s
`spring.cloud.gateway.routes` list has (and needs) no entry for
`/actuator/**` or for Prometheus itself — see §46.1's "Service-local, never
proxied" note, unchanged by this step. `/actuator/prometheus` is not an
Eventtick business API and is not documented as one in
`docs/api-contracts.md`.

**Local development scope only.** No remote-write, no long-term storage
configuration, no authentication on the scrape targets (deliberate — the
Actuator endpoints were already exposed unauthenticated for exactly this
purpose in Step 1). A specific Prometheus version is not pinned in this
repository; `monitoring/README.md` points at the official downloads page
instead of hardcoding a version number that would go stale. Step 3 (§46.4)
adds Grafana as a read-only consumer of this same Prometheus server —
nothing above changed to accommodate it.

## 46.4 Local Grafana Visualization (Step 3)

**Grafana is a separate, read-only visualization layer — it does not scrape
Eventtick services and does not touch the Gateway.** The flow:

```
Eventtick services --/actuator/prometheus--> Prometheus --PromQL--> Grafana
      (Step 1)                                  (Step 2)             (Step 3)
```

Grafana's only upstream is Prometheus, queried over PromQL at
`http://localhost:9090` (Prometheus's default port, unchanged from §46.3).
Grafana never calls `/actuator/prometheus` on any of the four services
itself, never appears in `gateway-service`'s `spring.cloud.gateway.routes`
list, and no `/api/admin/...` endpoint was added for it — consistent with
§46.1's "service-local, never proxied" principle and §46.3's "not an
Eventtick business API" note, both unchanged by this step.

**Datasource provisioning.**
`monitoring/grafana/provisioning/datasources/prometheus.yml` auto-registers
a `Prometheus` datasource (type `prometheus`, `url: http://localhost:9090`,
explicit `uid: prometheus`) on Grafana startup, so it doesn't need to be
added by hand through the UI. The explicit `uid` was added in Phase 14 Step
4 (see the fix note below) so the dashboard can reference this datasource by
a fixed literal id instead of Grafana's auto-generated one.

**Step 4 validation finding, fixed.** The dashboard JSON as originally
written in Step 3 referenced its datasource as `"uid": "${DS_PROMETHEUS}"`
with a matching `__inputs` block — Grafana's dashboard-*export* format,
resolved only by the **Dashboards → Import** UI wizard, never by file-based
provisioning (the mechanism `dashboards.yml` actually uses). Left as-is,
every panel and the `service` variable would provision "successfully" but
bind to no datasource at all. Fixed by giving the datasource an explicit
`uid: prometheus` and having every panel/variable reference that literal
uid directly; `__inputs` was removed. Confirmed via Grafana's own
provisioning documentation and community reports of the identical symptom —
not by running Grafana, which remains uninstalled in this environment (see
§46.5 "Live verification").

**Dashboard provisioning.**
`monitoring/grafana/provisioning/dashboards/dashboards.yml` points Grafana
at `monitoring/grafana/dashboards/` and auto-loads every dashboard JSON file
there on startup — currently one file,
`eventtick-service-overview.json`. The provider's `path` is committed as an
absolute path example for this repository's current checkout location; see
`monitoring/grafana/README.md` for adjusting it on another machine (Grafana
requires an absolute filesystem path here, not a repo-relative one).

**Current dashboard purpose.** "Eventtick Service Overview" — one small,
intentionally minimal dashboard (5 panels, not dozens), built only from
metrics the existing Actuator/Micrometer setup (§46.1) already exposes; no
new metric was added for it. A `service` template variable
(`label_values(up, service)`) — backed by the `service` label
`monitoring/prometheus.yml`'s `static_configs` already attaches to every
scraped series (§46.3) — lets a viewer filter to one service or look at all
four together. Panels:

| Panel | Query basis | Note |
|---|---|---|
| HTTP request rate | `rate(http_server_requests_seconds_count[...])` summed by `service` | |
| HTTP request latency | `rate(..._sum)/rate(..._count)` — a **mean**, not a percentile | No `_bucket` series exist (`management.metrics.distribution.percentiles-histogram` was never enabled), so `histogram_quantile()` cannot be used — documented here rather than inventing a metric |
| HTTP 5xx error rate | same `_count` series, `status=~"5.."` | |
| JVM heap memory used | `jvm_memory_used_bytes{area="heap"}` summed by `service` | |
| Process CPU usage | `process_cpu_usage` | Micrometer's standard process metric |

All five metric names (`http_server_requests_seconds_{count,sum}`,
`jvm_memory_used_bytes`, `process_cpu_usage`) were confirmed against a real
`GET /actuator/prometheus` response from a live `gateway-service` process
during this step, not assumed from documentation — see
`monitoring/grafana/README.md` for the one caveat this surfaced:
gateway-service's WebFlux-proxied routes report `uri="UNKNOWN"` in
`http_server_requests_seconds` (only the gateway's own local endpoints, like
`/actuator/health`, resolve a real `uri`), which is why the dashboard's
panels aggregate by `service`, not by `uri`.

**Local development scope only.** No provisioned alerting, no remote
Grafana instance, no Grafana auth changes beyond its own default login,
no Docker/Compose. A specific Grafana version is not pinned;
`monitoring/grafana/README.md` points at the official downloads page.

## 46.5 Final Validation (Step 4)

Step 4 re-inspected every monitoring file (this section's own subsections,
`monitoring/prometheus.yml`, `monitoring/README.md`,
`monitoring/grafana/README.md`, both provisioning YAMLs, the dashboard JSON)
and every service's actual Actuator/security configuration on disk, rather
than trusting Steps 1–3's own summaries. Findings:

- **Actuator/security configuration** — unchanged and consistent across all
  four services: `management.endpoints.web.exposure.include: health,
  prometheus` (never `*`) on all four; the narrow `/actuator/health(/live
  ness|/readiness)`/`/actuator/prometheus` allowlist still present in
  `user-service`'s `SecurityConfig` and `gateway-service`'s
  `GatewaySecurityConfig`; no `/actuator/**` entry in
  `gateway-service`'s `spring.cloud.gateway.routes`. No Java code changed.
- **Service-naming consistency** — `gateway-service`/`user-service`/
  `catalog-service`/`booking-service` are the exact same four `service`
  label values across `monitoring/prometheus.yml`'s `static_configs`, the
  dashboard's `service` variable (dynamically discovered via
  `label_values(up, service)`, not hardcoded), and this document.
- **One real inconsistency found and fixed** — the dashboard JSON's
  `${DS_PROMETHEUS}`/`__inputs` datasource reference (an import-dialog-only
  construct that file-based provisioning never resolves); see §46.4's "Step
  4 validation finding" note. This is the only monitoring-config change
  Step 4 made.
- **Metric names re-confirmed live** — a fresh `gateway-service` process was
  started, sent real traffic, and its `/actuator/prometheus` output was
  captured again; `http_server_requests_seconds_count`/`_sum`,
  `jvm_memory_used_bytes`, and `process_cpu_usage` all still present,
  matching every dashboard query exactly. No histogram (`_bucket`) series
  exist, confirming the latency panel's "average, not percentile"
  documentation is still accurate.
- **Live Prometheus/Grafana verification** — neither was installed in this
  environment as of Step 4; no target-UP status or rendered-dashboard claim
  was made. This gap was closed in Step 5 — see §46.6.

## 46.6 Live Infrastructure Verification (Step 5)

Step 4 validated configuration only, because neither Prometheus nor Grafana
was installed. Step 5 installed both locally (official Windows distributions
— Prometheus v3.15.0, Grafana v13.2.2 — extracted outside the repository,
never committed) and ran a real end-to-end verification. Nothing below is
inferred; every result was obtained by querying Prometheus's and Grafana's
own HTTP APIs directly.

**Prometheus.** Started with `prometheus.exe --config.file=` pointed at the
repository's actual `monitoring/prometheus.yml`, unmodified. Loaded without
error ("Completed loading of configuration file"); web UI reachable on
**http://localhost:9090** as documented; `GET /api/v1/targets` confirmed the
`eventtick-services` job with all four configured targets, each carrying its
correct `service` label. With only `gateway-service` started (see below):

| Target | Status | Reason |
|---|---|---|
| `gateway-service` (`localhost:8080`) | **UP** | Started successfully; scraped every 15s as configured |
| `user-service` (`localhost:8081`) | **DOWN** | Connection refused — the service itself never started in this environment (its PostgreSQL connection requires `DB_PASSWORD`, not set here; a real, environment-specific credential gap, not a scrape-config problem) |
| `catalog-service` (`localhost:8082`) | **DOWN** | Same reason as `user-service` |
| `booking-service` (`localhost:8083`) | **DOWN** | Same reason as `user-service` |

**End-to-end chain, `gateway-service`.** Real HTTP traffic
(`GET /actuator/health`, `GET /actuator/prometheus`) was sent to the running
service. `http_server_requests_seconds_count` was confirmed to increase
across successive Prometheus scrapes (e.g. `43` → `45` for one endpoint,
purely from Prometheus's own repeated scraping) — genuinely live, changing
samples, not a static snapshot.

**Grafana.** Started with `grafana.exe server` (see §46.4's note — Grafana
13.x's binary layout, discovered during this step) pointed at the
repository's actual `monitoring/grafana/provisioning/`. Two real,
Windows-specific problems were found and fixed — full detail in
`monitoring/grafana/README.md`'s "Known issues, fixed in Phase 14 Steps 4–5":
a relative `--homepath` resolving incorrectly (Grafana exited immediately),
and Grafana's background updater failing to replace its own bundled
Prometheus plugin on Windows (`Access is denied`), which left that plugin
type unregistered for the run. Neither required any change to this
project's own provisioning YAML or dashboard JSON. After both fixes:

- `GET /api/health` → `200`, Grafana listening on **http://localhost:3000**.
- `GET /api/datasources` (admin/admin) → exactly one datasource, `name:
  Prometheus`, `uid: prometheus`, `url: http://localhost:9090` — matching
  the provisioning YAML exactly, confirming automatic provisioning worked.
- `GET /api/datasources/uid/prometheus/health` → `{"status":"OK","message":
  "Successfully queried the Prometheus API."}`.
- `GET /api/search?query=Eventtick` → found the **Eventtick Service
  Overview** dashboard (`uid: eventtick-service-overview`) automatically —
  no import step, confirming dashboard provisioning worked.
- **All five panel queries executed via `POST /api/ds/query`** (the same
  endpoint the dashboard UI itself calls) and returned real data for
  `gateway-service`: request rate (`~0.067 req/s`), average latency
  (`~0.071s`), JVM heap used (`~64.9 MB`), process CPU (`~0.0002`), and the
  5xx error-rate panel — which read `0` until fresh `/actuator/health`
  traffic was generated (that endpoint's aggregate health check genuinely
  returns 503 locally, since no Redis is running — Step 1's documented
  behavior), then rose to a positive value in the very next query, exactly
  tracking the newly-generated errors.
- The `service` template variable's underlying data (`label_values(up,
  service)`) was confirmed via a direct Prometheus label-values query,
  returning all four configured service names — the same `up`-plus-`service`
  label mechanism every panel query above already exercised successfully.
  (Grafana's own variable-resolution endpoint has no direct equivalent to
  `/api/ds/query` for a quick API-only check; the underlying data source for
  the variable is the same one just proven correct.)

**Not claimed:** `user-service`/`catalog-service`/`booking-service` panels —
those three services never started in this environment, so their series
don't exist in Prometheus and nothing was claimed about them beyond the
target-DOWN status above. Nothing about a browser rendering of the dashboard
is claimed either — all evidence above is from Grafana's and Prometheus's
own HTTP APIs, the same data path the UI uses, not a screenshot.

## Current Status

The API Gateway (§5–§6) is implemented: request routing to the three
services, JWT validation at the edge, and dynamic rate limiting backed by
Redis (§19–§22) are all in place.

The Admin Dashboard (§45) is the project's current area of work. Phase 13
implementation is in progress — see §45's status table and
`docs/requirements.md` §15 for the current status of each requirement
(FR-35–FR-40).

Phase 14 (Monitoring, §46) — Steps 1 through 5 are complete: Actuator
health/Prometheus metrics endpoints (Step 1), local Prometheus scrape
configuration (Step 2), local Grafana visualization (Step 3), a
configuration-validation pass that found and fixed one real provisioning
bug (Step 4), and a live installation/verification pass that confirmed the
full chain actually works and found and fixed two further real,
Windows-specific setup problems (Step 5, §46.6). Custom metrics, tracing,
alerting, and Docker/Compose packaging remain undone and are out of scope
for this phase as currently defined.

Phase 15 (Optional Payment Integration, §25.1) — Step 1 (requirements,
architecture, and API contract design) is complete; **no payment Java
code, entity, controller, repository, or database migration exists yet.**
See `docs/requirements.md` §26 (FR-41–FR-50) and `docs/api-contracts.md`'s
Payment API section for the full design.

*(This paragraph is stale — retained rather than corrected, since fixing
it is out of scope for the Phase 16 addition below. Phase 15 payment code
was implemented in Steps 2–4; see §25.1–§25.5 for its actual, current
state.)*

*(Updated, Phase 17 documentation sync.)* Phase 16 (Event-Driven
Architecture / Kafka, §47–§50) is also complete through Step 4: real
transactional outboxes in both `booking-service` and `payment-service`,
two live Kafka producers, and `audit-service` consuming both into its own
read-only projections — see §50.9 for the precise implemented/not-
implemented boundary. Phase 17 (Frontend Integration, §51) is also
complete: the frontend's catalog browsing, show selection, seat holds,
booking creation, and payment now call real services through the Gateway
end to end, not mock data — see §51 for exactly what is real, what
remains mock, and the `show_seats` inventory gap (§17) this phase
surfaced. The monitoring stack (§46) gained a fifth scrape target
(`payment-service`) after Phase 15 but still lacks a sixth
(`audit-service`, §46.3) after Phase 16 — monitoring configuration was not
revisited when either backend phase landed.

---

# 47. Phase 16 — Kafka / Event-Driven Architecture (Design Only, Not Implemented)

**Status: design checkpoint. No Kafka dependency, broker, topic, producer,
or consumer exists anywhere in this repository as of this section** —
confirmed by inspection (`grep -i kafka` across every `pom.xml` and
`application.yml` returns nothing) and consistent with
`PaymentLifecycleScheduler`'s own Javadoc, which already documents that its
two sweeps use plain Spring `@Scheduled` specifically because no external
broker or job scheduler exists in this project. §27 and §28 sketched a
placeholder event list before any of the current domain model existed;
this section is what actually implements that sketch into a design,
built against the real, now-implemented booking/payment/catalog/user
services — it supersedes §27/§28 for design purposes, though their text is
left as-is (a historical placeholder, not a competing design).

## 47.1 Why Kafka, and why not everywhere

Every synchronous flow already validated in Phase 15 — booking creation,
seat holds (§15–§16), payment creation/success/failure/expiration/
reconciliation (§25.3), and the internal, payment-driven booking
confirmation (§25.5) — stays exactly as it is. Kafka is not a replacement
for any of it. The concrete gap Kafka fills is different: today, nothing
outside the service that owns a piece of state can learn that the state
changed, except by being called synchronously (adding a new HTTP call to
an existing critical path) or by polling. Kafka lets independent
consumers — analytics, notifications, a future Data Science pipeline —
learn about domain changes without adding a synchronous dependency to the
service that produced them, and without that service knowing anything
about who consumes its events (matches this project's own existing
service-ownership discipline: a producer publishes what it knows to be
true about its own domain; it doesn't reach into another service's data
to build a payload — see §47.6's payload rule).

This step is documentation only, per its own instructions. Sections
47.2–47.10 form the design; 47.11 states, once, that nothing was built.

## 47.2 Event catalogue

For every candidate event: producer, trigger, payload shape (IDs-only vs.
denormalized), likely consumers, and a classification —
**(A)** strong Kafka candidate, **(B)** better handled synchronously or
folded into another event, **(C)** not useful yet.

### Booking (producer: booking-service — owns `bookings`, `booking_seats`, `show_seats`)

| Event | Trigger | Payload | Class |
|---|---|---|---|
| `BookingCreated` | `BookingService.createBooking` commits a new `PENDING` booking | `bookingId, userId, showId, seatIds, totalAmount, status, createdAt` | **A** |
| `BookingCancelled` | `cancelBookingForCaller` (customer) or the internal `cancelBooking` (payment-service, on FAILED/EXPIRED) commits `CANCELLED` | `bookingId, userId, showId, previousStatus, reason` (`CUSTOMER_CANCELLED` \| `PAYMENT_FAILED` \| `PAYMENT_EXPIRED`), `cancelledAt` | **A** |
| `BookingConfirmed` | the internal `confirmBooking` (payment-service only, §25.5) commits `CONFIRMED` | `bookingId, userId, showId, seatIds, totalAmount, confirmedAt` | **A** — see §47.8: this is a broadcast of an already-committed fact, never a trigger |

`reason` on `BookingCancelled` matters for every named consumer
(analytics needs to separate abandonment from payment failure;
notifications need different copy for each) and costs nothing to add,
since `cancelBookingForCaller`/`cancelBooking`'s two call sites already
know which case they are.

### Seat

| Event | Trigger | Class | Why |
|---|---|---|---|
| `SeatHeld` | `holdSeats` (AVAILABLE → HELD) | **C** | High-frequency, ephemeral (a hold that is never converted to a booking leaves no other trace), and — per §16's own documented gap — holds don't even record *who* holds a seat yet. An event built on an incomplete domain fact isn't worth publishing; revisit once Redis-backed holds with real ownership exist. |
| `SeatReleased` | `releaseHold` (HELD → AVAILABLE) | **C** | Same reasoning as `SeatHeld`; also fires on the existing best-effort/idempotent release path, which would need to distinguish "released because of an explicit customer action" from "released because nothing happened and the request was a no-op" to be a meaningful signal. |
| `SeatBooked` | seats transition HELD → BOOKED inside `confirmBooking` | **B** | Not a separate business fact — it's part of *becoming confirmed*. Its information (`seatIds`) is already on `BookingConfirmed`'s payload. A dedicated event would just double-publish the same moment under two names. |

### Payment (producer: payment-service — owns `payments`)

| Event | Trigger | Payload | Class |
|---|---|---|---|
| `PaymentCreated` | `createPayment` commits the initial `CREATED` row | `paymentId, bookingId, userId, amount, currency, status, createdAt` | **A** |
| `PaymentSucceeded` | `chargeAndResolve` commits `SUCCESS` | `paymentId, bookingId, userId, amount, currency, providerReference, succeededAt` | **A** — see §47.8 |
| `PaymentFailed` | `chargeAndResolve` commits `FAILED` | `paymentId, bookingId, userId, amount, currency, failureReason, failedAt` | **A** |
| `PaymentExpired` | `expirePendingPayments`'s conditional `UPDATE` actually transitions a row | `paymentId, bookingId, userId, amount, currency, expiredAt` | **A** |

None of these carry the idempotency key: it is a client-supplied value
with no business meaning to a downstream consumer, and publishing it
adds a way to correlate a customer's separate checkout attempts for no
documented benefit. Every payment event fires only *after* its
corresponding status is durably committed to `payments` — the same
commit-then-side-effect ordering `PaymentService` already uses for its
booking-service calls (§25.3), extended to one more best-effort side
effect (§47.8).

### Catalog (producer: catalog-service — owns `content`, `venues`, `seats`, `shows`)

| Event | Class | Why |
|---|---|---|
| `ShowCreated` | **A** | Directly feeds the "show popularity"/demand-prediction use case §47.10 names, from the moment inventory exists — a real, named future consumer. |
| `ShowCancelled` | **A** | Business-significant and already customer-visible (an admin cancelling a show is exactly the kind of change notifications/analytics need to know about). **Open question, not resolved here:** whether admin show-cancellation already synchronously cascades to affected bookings was not verified as part of this design pass; if it does not, that is a synchronous consistency gap to fix on its own terms, not something an event should silently paper over (§47.8's boundary applies here too — Kafka broadcasts a fact, it does not become the mechanism that decides what should happen to affected bookings). |
| `ContentCreated`/`Updated`/`Deleted`, `VenueCreated`/`Updated`/`Deleted`, `ShowUpdated`/`Deleted` | **C** | Low-volume admin bookkeeping with no concrete consumer identified yet beyond a generic "might be useful for analytics later." Publishing eight event types with no named consumer is exactly the over-eager instrumentation this design pass was told to avoid. Revisit if/when a concrete consumer (a search index, a cache, a reporting need) is actually proposed. |

### User (producer: user-service — owns `users`, `plans`)

| Event | Class | Why |
|---|---|---|
| `UserRegistered` | **A** | Low-risk, high-value: signup-funnel analytics now, a natural notification (welcome email) and recommendation cold-start signal later — three independent, named future consumers for one event. |
| `UserRoleChanged` | **A**, narrowly, for one consumer: audit | Today a role change (`AuthController`/`UserService.changeRole`) simply overwrites `users.role` — there is no history of who was promoted/demoted or when, anywhere. An immutable event log is the natural, minimal fix, and it is exactly the kind of fact Kafka's own persistence (§47.5) is suited to, rather than a new audit table with its own migration. |
| `UserPlanChanged` | **C** | No concrete consumer today: the rate limiter already reads the caller's `plan` claim fresh from the JWT on every request (§9–§12), never from a cached/event-sourced copy, so there is nothing currently waiting to be told about a plan change. Revisit if a future consumer (billing, plan-change analytics) is proposed. |

## 47.3 Event producer ownership

One rule, already established by this project's own service boundaries and
just carried into the event design: **the service that owns the state
publishes the event for that state — never a separate "event service."**

| Producer | Owns (per §13, §25.1, catalog/user service boundaries) | Publishes |
|---|---|---|
| booking-service | `bookings`, `booking_seats`, `show_seats` | `BookingCreated`, `BookingCancelled`, `BookingConfirmed` |
| payment-service | `payments` | `PaymentCreated`, `PaymentSucceeded`, `PaymentFailed`, `PaymentExpired` |
| catalog-service | `content`, `venues`, `seats`, `shows` | `ShowCreated`, `ShowCancelled` (only the two **A**-classified events from §47.2 for now) |
| user-service | `users`, `plans` | `UserRegistered`, `UserRoleChanged` |
| gateway-service | nothing (§6, §33: routing/auth/rate-limiting only, no business rules and no owned state) | nothing |

## 47.4 Topic architecture

**Decision: one topic per owning service/domain** —
`eventtick.booking`, `eventtick.payment`, `eventtick.catalog`,
`eventtick.user` — partitioned by `aggregateId`, each with exactly one
producing service.

| Criterion | Option A: per-domain (chosen) | Option B: single `eventtick.domain-events` | Option C: per-event-type (20+ topics) |
|---|---|---|---|
| Service ownership | One producer per topic — trivial ACL ("only booking-service may write to `eventtick.booking`") | Every producer writes the same topic — no natural write-ACL boundary | One producer per topic too, but fragmented across many topics for one owner |
| Consumer independence | A consumer subscribes to exactly the domain(s) it cares about | Every consumer must filter every event by `eventType` client-side, even if it wants only one domain | Maximally independent, at the cost below |
| Ordering | Per-partition (per-aggregate) ordering within a domain; no cross-domain ordering is ever required, so this loses nothing | Same per-partition guarantee, but aggregate IDs from unrelated domains now share one partition key space arbitrarily | Same per-partition guarantee, no advantage over Option A for ordering |
| Retention | Tunable per domain (e.g. payment events retained longer than catalog bookkeeping events) | One retention setting for everything, or a workaround splitting the "single" topic anyway | Tunable per event type — finer than needed today |
| Operational complexity | 4 topics, matching the 4 producing services — easy to reason about | Fewest topics, but pushes all the complexity onto every consumer's filtering logic instead | 20+ topics for a system with 4 producers and no consumers yet — topic sprawl with no current benefit |
| Future Data Science use | A "payment features" pipeline naturally subscribes to `eventtick.payment` alone | Same pipeline must consume and discard most of the single topic | No advantage over Option A; more topics to discover and wire up |

Option A is chosen because it mirrors service ownership exactly (no new
boundary invented), keeps per-aggregate ordering without paying for
cross-domain ordering nobody needs, and leaves room to split a single
event type into its own topic later — without disturbing the others — if
one specific type's volume or retention needs ever diverge enough to
justify it.

## 47.5 Event envelope

```text
EventEnvelope:
  eventId          UUID    — unique per publish; the idempotency key a consumer dedupes on
  eventType        string  — e.g. "BookingConfirmed"; distinguishes events sharing one topic
  eventVersion     int     — per event type, starting at 1; allows schema evolution without a new topic
  occurredAt       instant — the business moment the event describes (e.g. Payment.succeededAt),
                             not the Kafka broker's own append timestamp
  producer         string  — the owning service's name (e.g. "payment-service"), the same
                             `service` label already used for every Prometheus target (§46)
  aggregateType    string  — "Booking" | "Payment" | "Show" | "User" | ...
  aggregateId      UUID    — the domain row's own primary key; also the Kafka partition key
  correlationId    UUID    — ties together the events/requests belonging to one business
                             transaction; derived from the originating request's own
                             X-Request-ID (§6) when one exists (any event raised from an HTTP
                             request), or freshly minted when there is none (an event raised
                             from a scheduled sweep, e.g. PaymentExpired)
  causationId      UUID?   — nullable; the eventId of the specific upstream event that directly
                             caused this one (see below)
  payload          object  — event-specific fields, see §47.2's tables and the IDs-only rule below
```

Every field from the task's suggested envelope is kept; none were dropped
as inapplicable. `causationId` was evaluated separately: it is genuinely
useful once a *consumer* of one event goes on to publish another (a future
notification-sent event caused by consuming `PaymentSucceeded`, say), but
none of the events catalogued in §47.2 are themselves caused by consuming
another Kafka event — they are all caused directly by a committed database
write, which `correlationId` (tracing back to the originating request) and
`occurredAt` already capture. It is included as an optional field for that
future case, not as a required one today.

**Payload rule:** IDs and the minimal fields a consumer needs to decide
whether to act (status, amount, timestamps) — never a denormalized join of
another service's data. This matches payment-service's own existing
stance of holding no `Booking`/`User` entity of its own (§25.1) and calling
the owning service's read API instead of trusting a cached copy; a
consumer that needs more than an event's IDs calls the producing service's
existing REST API for the current state, the same way payment-service
already does.

## 47.6 Delivery semantics

**At-least-once, explicitly not exactly-once.** This project has no
distributed-transaction infrastructure (no XA, no two-phase commit across
Postgres and a broker), and claiming application-level exactly-once
without one would be dishonest. Kafka's own idempotent-producer feature
only prevents a duplicate *at the broker* from a producer's own retry — it
does not, by itself, make a consumer's business effect happen exactly
once. The honest, achievable target is **effectively-once**: at-least-once
delivery plus idempotent consumption.

- **Duplicates:** possible (retries, rebalances, an outbox re-publish after
  a crash — §47.9) and expected. Every consumer dedupes on `eventId`,
  exactly the same discipline `payment.idempotency_key` already enforces
  for HTTP requests (§25.1), just applied to a Kafka message instead of a
  request body.
- **Ordering:** guaranteed only per-partition. Partitioning by
  `aggregateId` guarantees every event for one booking (or one payment)
  arrives in order; there is no guarantee — and no consumer should assume
  one — across different aggregates or across topics.
- **Retry:** consumer-side, with backoff, for transient failures (e.g. a
  consumer's own database briefly unavailable); a bounded number of
  attempts before a message moves to that topic's dead-letter topic rather
  than blocking the partition indefinitely.
- **Dead-letter strategy:** one DLQ topic per source topic
  (`eventtick.booking.dlq`, `eventtick.payment.dlq`, ...) for symmetry with
  §47.4's per-domain decision — a message that exhausts retries lands
  there for manual inspection/replay, instead of a shared DLQ that mixes
  every domain's failures in one place. This is a minor decision either
  way; a single shared DLQ would not be wrong, just less consistent with
  the rest of this design.
- **Consumer failure:** an ordinary instance crash is handled by Kafka's
  own consumer-group rebalancing (another instance picks up the orphaned
  partitions); the redelivery this can cause is just another source of
  "at-least-once," not a special case requiring its own handling.
- **Broker failure:** out of scope until a broker actually exists (§47.11);
  documented here only as a production requirement (replication factor
  > 1) for whenever one is stood up.
- **Replay:** because events persist on a topic for its retention window,
  a consumer can replay history by resetting its consumer-group offset —
  the exact mechanism §47.10's future analytics backfill depends on, and
  the reason retention (§47.4) should be set with backfill needs in mind,
  not just "how long until the live consumers would need it."

## 47.7 Ordering strategy

Stated once here for clarity, though it is implied by §47.4 and §47.6
together: ordering is guaranteed **per-aggregate, per-topic** (partition
key = `aggregateId`), and nowhere else. No event in §47.2's catalogue
needs cross-aggregate or cross-domain ordering — a `BookingConfirmed` for
booking X has no ordering relationship to a `PaymentCreated` for booking
Y, and even within one business transaction (one booking's payment), the
synchronous flow (§47.8) — not event ordering — is what already guarantees
`PaymentSucceeded` reflects a state that was committed before
`BookingConfirmed` could possibly have been published.

## 47.8 The payment/Kafka boundary

This is the one boundary the task explicitly required preserving, stated
as plainly as possible: **nothing changes about §25.3's synchronous flow.**

```text
Payment SUCCESS
    │
    ▼
payment-service durably commits SUCCESS to `payments`         ← unchanged, still first
    │
    ▼
payment-service calls the internal, synchronous
POST /internal/bookings/{id}/confirm                          ← unchanged, still required,
    │                                                            still the only confirmation path (§25.5)
    ▼
booking-service commits CONFIRMED, seat → BOOKED
```

A future `PaymentSucceeded` publish is an **additional**, independent,
best-effort step alongside the existing internal confirm call — a peer,
not a replacement, not a precondition, and not a downstream trigger for
it:

```text
                              ┌─→ internal confirm call (existing, synchronous, required)
payment SUCCESS committed ────┤
                              └─→ publish PaymentSucceeded (new, best-effort, optional)
```

If the Kafka publish fails, the booking is still confirmed exactly as it
is today — a publish failure must never be allowed to affect booking
confirmation, and a slow or unavailable broker must never be allowed to
delay it either. Conversely, `booking_sync_status`/
`reconcilePendingBookingSync()` (§25.3) remains the **sole** mechanism that
retries a failed booking-confirmation call; Kafka does not become a
second, competing reconciliation path, and no consumer of
`PaymentSucceeded` is ever responsible for confirming a booking. Any
future outbox row for `PaymentSucceeded` (§47.9) is written and retried
completely independently of `booking_sync_status`, using the same
database-backed-status-column idea, not the same column or scheduler.

## 47.9 Transactional outbox decision

**Decision: yes, the Transactional Outbox Pattern is the correct
architecture for whichever service eventually publishes to Kafka** — not
implemented now, per this step's own instructions, but the intended shape
for later.

**The dual-write problem, stated concretely** (booking-service confirming
a booking is the illustrative case, though the same problem applies to
every producer):

1. Booking-service updates `bookings.status = CONFIRMED` **and** publishes
   `BookingConfirmed` — if the database commit succeeds but the Kafka
   publish fails (broker down, network partition), the event is silently
   lost: analytics undercounts, a notification is never sent, and nothing
   about the *booking* itself is wrong, but every downstream consumer's
   view of the world now quietly disagrees with the database.
2. The reverse: the Kafka publish succeeds but the database transaction
   then rolls back (e.g. a later step in the same method throws) — now a
   consumer reacts to `BookingConfirmed` for a booking that, in the
   database, was never confirmed at all.

**The outbox pattern, mapped onto this project's own existing
conventions** — this is not a foreign pattern being imported; it is the
same idea `PaymentService`/`PaymentLifecycleScheduler` already implement
for the booking-service HTTP call (§25.3: commit the durable state change
first, treat the side effect as independently retryable, track its status
in the database rather than in memory), extended one step further:

1. The business transaction (e.g. `confirmBooking`) writes its normal state
   change **and** an `outbox_events` row **in the same database
   transaction** — atomic for free, because both are writes to the same
   Postgres database; no distributed transaction or two-phase commit is
   needed.
2. A separate, small poller — structurally identical to
   `PaymentLifecycleScheduler`'s existing `@Scheduled` sweeps — reads
   unpublished outbox rows, publishes each to Kafka, and marks it
   published. A crash between actually publishing and marking-published
   causes a duplicate publish on the next sweep; consumers already dedupe
   on `eventId` (§47.6), so this is safe, not a special case.
3. This guarantees a `BookingConfirmed` event is published if and only if
   the booking was actually confirmed — the write can never disagree with
   the event, in either direction.

No `outbox_events` table, migration, or code exists; this is the intended
design for when publishing is actually implemented, not a claim that it
exists.

## 47.10 Future Data Science connection (Phase 17, not built)

The anticipated shape of the pipeline this design enables, later:

```text
Eventtick services (booking/payment/catalog/user)
       │
       ▼
Kafka (eventtick.booking / .payment / .catalog / .user)
       │
       ▼
ingestion (e.g. a Kafka Connect sink, or a simple consumer job)
       │
       ▼
data warehouse / lake
       │
       ▼
feature engineering
       │
       ▼
ML models — demand prediction, recommendations, anomaly detection
```

Kafka's specific value for this future phase: it decouples *when* Phase
17's analytics infrastructure gets built from Phase 15/16's already-live
transactional services. A future analytics job consumes replayed history
from a topic (§47.6's replay mechanism) rather than querying booking-service
or payment-service's own production database directly — the exact
anti-pattern this project's service-ownership boundaries (§13, §25.1)
already avoid for synchronous calls, now avoided for analytics too. Named,
concrete mappings from §47.2's catalogue to future use cases:

- `BookingCreated`/`BookingCancelled`/`BookingConfirmed` → booking-behavior
  and demand-prediction features.
- `PaymentSucceeded`/`PaymentFailed`/`PaymentExpired` → payment-funnel
  analytics, and a plausible input to future anomaly detection (e.g. an
  unusual spike in failures or expirations for one show).
- `ShowCreated`/`ShowCancelled` → show-popularity/inventory features.
- `UserRegistered` → recommendation cold-start signal.

None of this is built. It is documented so that Phase 16's event design,
if implemented, doesn't have to be redesigned when Phase 17 starts.

## 47.11 What will not be event-driven, and confirmation nothing was built

Not event-driven, by explicit decision above: booking confirmation
(§47.8 — stays synchronous, permanently, not just "for now"), seat holds
(§47.2 — `SeatHeld`/`SeatReleased` classified **C**), and all
low-volume catalog bookkeeping beyond `ShowCreated`/`ShowCancelled`
(§47.2 — classified **C**). `UserPlanChanged` is also **C** — the rate
limiter reads a caller's plan fresh from their JWT on every request, never
from an event-sourced copy.

**Confirmed: no Kafka dependency was added to any `pom.xml`, no broker
config was added to any `application.yml`, no topic, producer, consumer,
outbox table, or migration was created, and no application code changed
as part of this section.** This section is `docs/architecture.md` content
only.

---

# 48. Phase 16 Step 2 — Kafka Infrastructure and Outbox Foundation (Implemented)

Implements a first, deliberately narrow slice of §47's design: the Kafka
client, the shared event envelope, the transactional outbox, and exactly
one real producer (`BookingCreated`). No consumer, and no other event from
§47.2's catalogue, exists yet — see 48.8.

## 48.1 Where this lives

booking-service only — the one producer this step adds. Not a shared Maven
module: this project has no shared-module convention (see
`SecurityConfig`/`JwtService`'s own precedent, copied per-service rather
than extracted, when booking-service gained JWT validation in Phase 15 Step
4); the same reasoning applies here. `spring-kafka` (compile) and
`spring-kafka-test` (test) were added only to
`backend/booking-service/pom.xml`, both with no explicit `<version>`,
resolved through `spring-boot-starter-parent:3.3.4`'s own dependency
management (`spring-kafka 3.2.4`, `kafka-clients 3.7.1` — Spring Boot's own
compatible pairing, not independently chosen). New code lives in two new
packages: `com.eventtick.booking.outbox` (the generic outbox/envelope
mechanism — `OutboxEvent`, `OutboxEventStatus`, `OutboxEventRepository`,
`OutboxService`, `OutboxPublisher`, `EventEnvelope`) and
`com.eventtick.booking.event` (domain-specific: `EventTopics`,
`BookingCreatedPayload`).

## 48.2 Kafka runtime requirement

`spring.kafka.bootstrap-servers`, from `KAFKA_BOOTSTRAP_SERVERS`
(default `localhost:9092`, the same env-var-with-a-local-dev-fallback
pattern every other configurable value in this project already uses — not
a "localhost-only" assumption baked into the config's meaning, just its
unset-environment fallback). No Kafka broker is installed by this
repository; see `kafka/README.md` for how to run one locally (a standalone
binary, KRaft mode — this machine has neither Docker nor WSL, the same
constraint already documented for Redis/Prometheus/Grafana) and the four
topics' partition/replication/retention configuration. **`mvn test` needs
no broker at all** — every Kafka-touching test uses `spring-kafka-test`'s
`@EmbeddedKafka` (a real, in-process broker, not a mock — the same
"real infrastructure" choice already made for Redis via `embedded-redis`)
or a deliberately unreachable address to test the failure path; every other
test has zero Kafka involvement.

Booking creation itself has **zero runtime dependency on Kafka being
reachable** — `BookingService`/`OutboxService` inject no `KafkaTemplate` at
all, only `OutboxPublisher` does, and nothing in the booking-creation call
path invokes it (proven directly by
`BookingCreationOutboxIntegrationTest#createBooking_succeeds_evenThoughNoKafkaBrokerIsReachableAtAll`,
run with `bootstrap-servers=localhost:1`). `management.health.kafka.enabled:
false` keeps `/actuator/health` and the readiness group from reporting DOWN
purely because Kafka is unreachable, for the same reason.

## 48.3 Topic configuration

The four topics approved in §47.4 (`eventtick.booking`, `eventtick.payment`,
`eventtick.catalog`, `eventtick.user`) are named as plain constants in
`EventTopics` — not derived from `aggregateType` by any mapping function,
since only one producer (`EventTopics.BOOKING`) exists to name yet; a
generic derivation would be premature abstraction for a one-entry mapping.
Topics are provisioned as infrastructure (`kafka/README.md`'s
`kafka-topics.sh --create` commands), never by application startup code —
no `NewTopic`/`KafkaAdmin` bean exists anywhere in booking-service.

## 48.4 Event envelope (implemented)

`EventEnvelope` (a record) carries every field §47.5 approved: `eventId`,
`eventType`, `eventVersion`, `occurredAt`, `producer`, `aggregateType`,
`aggregateId`, `correlationId`, `causationId`, `payload`. One refinement
from the design, made concrete during implementation:
**`correlationId`/`causationId` are `String`, not `UUID`.** A correlation
id is usually the Gateway's own `X-Request-ID`
(`RequestIdWebFilter`), already validated there as "1–128 characters of
letters, digits, `. _ : -`" — not guaranteed to parse as a `UUID` — so
typing this field as `UUID` would risk rejecting a value the rest of the
system already accepts. Serialized to plain JSON via the ordinary,
Spring-managed `ObjectMapper` (`SerializationFeature.WRITE_DATES_AS_TIMESTAMPS`
disabled by Spring Boot's own default, so `occurredAt` is ISO-8601, not an
epoch number) — plain `StringSerializer` on both the Kafka key and value,
not Spring Kafka's class-based `JsonSerializer<T>`, so the wire format is
readable by `kafka-console-consumer` with no producer-side Java class on
the reader's classpath (§47.5's "inspectable" requirement, concretely).

## 48.5 Outbox table and entity (implemented)

`database/migrations/0013_create_booking_outbox_events_table` creates
`booking_outbox_events` (prefixed `booking_`, unlike every domain table so
far, because "outbox_events" is generic pattern infrastructure that a
future payment-service/catalog-service/user-service outbox would collide
with under one shared physical database otherwise — see the migration's
own table comment). Columns: `event_id` (primary key, application-assigned,
not database-generated — there is no separate surrogate id), `event_type`,
`aggregate_type`, `aggregate_id`, `topic`, `payload` (the fully-serialized
envelope, plain `TEXT` not `JSONB` — nothing queries into it via Postgres's
JSON operators, so `JSONB`'s extra capability would be unused complexity;
a one-line `ALTER` later if that ever changes), `occurred_at`, `status`
(`PENDING`/`PUBLISHED`/`FAILED` — `FAILED` reserved, unset by any code path
today, the same "defined but not yet reachable" precedent as
`BookingStatus.FAILED`), `attempts`, `last_error`, `published_at`
(`NOT NULL` only when `status = 'PUBLISHED'`, enforced by a `CHECK`),
`created_at`/`updated_at`. A partial index on `PENDING` rows backs the
publisher's own query shape (matches `idx_payments_booking_sync_status_pending`'s
precedent) — not exercised by any H2-backed test, the same documented H2
partial-index gap as that precedent. **No row is ever deleted** by this
step's own code — a published row is retained, not pruned.

`OutboxEvent` maps this table and implements Spring Data's
`Persistable<UUID>`. **A genuine bug found and fixed during this step:**
because `eventId` has no `@GeneratedValue`, Spring Data's default
"is this entity new?" check (id == null) always said "no" — every
`repository.save(...)` therefore called `entityManager.merge(...)` instead
of `persist(...)`, silently **upserting** on an accidental id collision
instead of failing the unique-constraint check it exists to enforce. Found
by `OutboxEventRepositoryTest` itself (a deliberate duplicate-`event_id`
save didn't throw); fixed by implementing `Persistable` with an explicit,
constructor-set `isNew` flag — the standard, idiomatic fix for a
client-assigned natural-key id.

`Booking`/`ShowSeat`/`BookingSeat` gained the same `@ColumnDefault
("CURRENT_TIMESTAMP")` schema-generation hint on their `createdAt`/
`updatedAt` columns that `Payment`/`User` already had, needed the first
time this module's own tests actually generated a real H2 schema from
these entities (`create-drop`, scoped to the new outbox-related tests only
via `@TestPropertySource` — booking-service's shared test config stays
`ddl-auto: none`). Inert against the real Postgres migration, which
already owns these values via `DEFAULT now()` and a trigger.

`OutboxEventRepository`'s two `@Modifying` methods (`markPublished`,
`recordFailure`) each carry their own `@Transactional` — the exact,
proactively-applied lesson from Phase 15 Step 3's `PaymentRepository` bug
(a custom `@Modifying @Query` method is not auto-wrapped in a transaction
the way inherited `save`/`delete` are).

## 48.6 Outbox service and the atomicity guarantee (implemented)

`OutboxService.record(...)` is the one place business code creates an
outbox row. It has **no dependency on `KafkaTemplate` or any Kafka client
at all** — structurally, not by convention, it cannot publish to Kafka,
which is what makes it "difficult to accidentally publish inside the
business transaction." It builds the envelope, serializes it, and calls
`outboxEventRepository.save(...)` — an inherited `SimpleJpaRepository`
method that already carries `@Transactional(propagation = REQUIRED)`, so
calling `record(...)` from inside `BookingService.createBooking`'s own
`@Transactional` method makes the outbox write join that exact transaction,
with no coordination code needed. This is the entire mechanism; nothing
more elaborate was built.

**Proven, not just claimed**
(`BookingCreationOutboxIntegrationTest`, real H2, real repositories, real
`OutboxService` — no mocks):
- A successful `createBooking` call writes both the booking row and its
  `BookingCreated` outbox row, in the same call. The outbox payload's own
  `bookingId`/`userId`/`showId`/`seatIds`/`totalAmount` and `correlationId`
  were asserted directly against the actual booking created.
- A failure — "this seat already has an active booking," the closest
  existing exception to the writes, since `createBooking`'s outbox write is
  unconditionally its last statement and nothing today can fail after it —
  leaves **neither** the booking **nor** the outbox row behind, compared
  against exact row counts before and after.
- Booking creation succeeds in full — booking row **and** outbox row —
  against a Spring context whose `bootstrap-servers` points at
  `localhost:1` (nothing listening), because nothing in that call path ever
  reaches Kafka.

## 48.7 `BookingCreated`: the one real producer (implemented)

Fires from `BookingService.createBooking`, after both the booking and its
`BookingSeat` line items are written, inside the same transaction.
Payload (`BookingCreatedPayload`): `bookingId`, `userId`, `showId`,
`seatIds`, `totalAmount` — leaner than §47.2's own illustrative table:
`status` is omitted (always `PENDING` for a just-created booking — the
event type already says so) and there is no separate payload-level
`createdAt` (the envelope's own `occurredAt` already carries that exact
moment; repeating it would say nothing new). `aggregateType` is `"Booking"`,
`aggregateId` is the booking's own id, `topic` is `EventTopics.BOOKING`.

**Correlation id** (§47's `correlationId`, §12 of this step's own
instructions): `BookingController.createBooking` now reads the incoming
`X-Request-ID` header — the Gateway's `RequestIdWebFilter` already sets
this on every routed request and forwards it upstream, unread by
booking-service until now — and threads it through as `createBooking`'s new
final parameter, down to `OutboxService.record`. No second, unrelated
request-id mechanism was introduced. When absent (a call with no
originating HTTP request — not a real case yet, since `BookingCreated` only
ever fires from `POST /api/bookings`, but the mechanism is ready for a
future background-originated event), `OutboxService` generates a fresh
correlation id rather than leaving the field blank.

`OutboxPublisher.publishPending()` (public, separate from its
`@Scheduled` wrapper — the same reason `PaymentService#expirePendingPayments`
is a plain method too, so tests call it directly rather than waiting on a
timer): reads a bounded batch of `PENDING` rows oldest-first
(`eventtick.events.publish-batch-size`, default 50), and for each,
publishes to `KafkaTemplate.send(topic, aggregateId.toString(), payload)`
— **`aggregateId` as the Kafka message key** (§47.7's per-aggregate
ordering guarantee) — blocking up to 5 seconds on the returned future so
that only a **real broker acknowledgement** marks a row `PUBLISHED`, never
merely that a send was initiated. `acks: all` on the producer. On any
failure, `attempts` increments and `last_error` is recorded; the row stays
`PENDING` (§48.5's reserved `FAILED` is not used) for the next sweep — one
bad send never stops the rest of its batch and never throws out of
`publishPending()`. `@Scheduled(fixedDelayString =
"${eventtick.events.publish-sweep-interval-ms}")` (default 5000ms;
`fixedDelay`, not `fixedRate` — the next sweep starts only after the
previous one finishes, the same choice `PaymentLifecycleScheduler` already
makes). Booking-service's test config sets this interval to 999999999ms,
so the sweep never fires in the background of an ordinary
`@SpringBootTest` context — the same proactively-applied fix Phase 15 Step
3 needed for `PaymentLifecycleScheduler`'s own equivalent risk.

**At-least-once, concretely, not just asserted:** if this process crashes
after a broker ack but before `markPublished` commits, the row is still
`PENDING` and gets published again under the **same `eventId`** on the next
sweep — expected, not a bug; a consumer dedupes on `eventId` (§47.6).
Concurrent publisher instances racing the same batch have the identical
failure mode (a duplicate publish, never a lost one), for the same reason.
Proven with a real, in-process broker
(`OutboxPublisherEmbeddedKafkaTest`, `@EmbeddedKafka`): a published row is
marked `PUBLISHED` only after a real consumer actually receives the
message, keyed by the booking's own `aggregateId`, with the exact payload
bytes the outbox row held. Proven for the failure path with a genuinely
unreachable broker (`OutboxPublisherUnavailableKafkaTest`,
`bootstrap-servers=localhost:1`): `publishPending()` never throws, the row
stays `PENDING` across repeated sweeps, `attempts` increments once per
sweep, and multiple unpublishable rows in one batch are each retried
independently.

## 48.8 What is, and is not, implemented

**Implemented:** the Kafka client dependency and configuration; the shared
`EventEnvelope`; the transactional outbox (table, entity, repository,
service); `OutboxPublisher`, including at-least-once delivery with a real
broker acknowledgement requirement, per-row retry with `attempts`/
`last_error`, and `aggregateId`-keyed publishing; one real producer,
`BookingCreated`, wired into `BookingService.createBooking` with
`X-Request-ID`-derived correlation ids; the four approved topics documented
and provisionable (`kafka/README.md`).

**Not yet implemented (as of Step 2):** `PaymentSucceeded`/`PaymentFailed`/
`PaymentExpired` producers (payment-service has no outbox of its own yet,
and `PaymentService`/`PaymentStatus`/`BookingSyncStatus`/
`PaymentLifecycleScheduler`/`BookingServiceClient` were not touched by this
step); `BookingConfirmed`/`BookingCancelled` producers (booking-service's
own outbox mechanism could carry them next, but neither call site does
yet); any catalog or user event; any consumer (Step 3, §49, adds the
first); analytics; notifications; the ML pipeline (§47.10). The existing,
synchronous payment/booking consistency mechanism (§25.3) is completely
unmodified — `PaymentService` still commits `SUCCESS` first and calls
booking-service's internal confirm endpoint synchronously;
`booking_sync_status` remains the only mechanism that retries that call.
Kafka plays no role in that path.

---

# 49. Phase 16 Step 3 — First Kafka Consumer and End-to-End Event Flow (Implemented)

Adds one real consumer of `eventtick.booking`'s `BookingCreated` events,
proving the full chain §48 built the producing half of: producer →
transactional outbox → Kafka → consumer → consumer-side persistence →
idempotent processing. An infrastructure/domain-event proof, per this
step's own framing, not a business-critical workflow — nothing in the
synchronous booking/payment path depends on this consumer existing,
running, or being healthy.

## 49.1 Consumer location and why

**A new, dedicated service: `audit-service`** (port 8085). Not gateway-service
or payment-service (both explicitly ruled out), and not catalog-service or
user-service either — neither has any natural relationship to booking
events, and bolting an unrelated cross-cutting concern onto either would be
arbitrary. Not booking-service itself: even though `booking_event_audit`
is not booking-service's own domain state (`bookings`/`booking_seats`/
`show_seats`), a service consuming its own just-published event proves
JSON serialization and DB persistence but not genuine cross-service
decoupling — and this step's own live end-to-end procedure (49.8) treats
"the consumer service" as a separate process to start alongside the five
existing backend services, not something embedded in one of them.

A dedicated service is the interpretation that satisfies every constraint
without stretching one: it structurally cannot own booking/payment/catalog/
user domain state (it owns exactly one table, `booking_event_audit`, and
nothing else); it needs no new dependency added to any *existing* service
(payment-service in particular stays untouched, per this step's own
explicit rule); and it matches how a real audit/analytics consumer is
normally deployed — independently of the producers, so it can fail, lag,
or be redeployed without affecting them. No shared Maven module was
created for this (§48.1's reasoning applies identically): `EventTopics` is
duplicated, not shared, exactly as small infra classes already are
per-service throughout this project (`SecurityConfig`/`JwtService`'s own
precedent).

**No Spring Security dependency** — the same choice, and the same
reasoning, catalog-service's own `pom.xml` already documents: this service
has no REST API of its own (no business endpoints at all, only Actuator),
so there is nothing else to protect by adding one. **No Gateway route** —
confirmed directly (no route to port 8085 anywhere in
`gateway-service/src/main/resources/application.yml`): a background Kafka
consumer has no HTTP surface for the Gateway to route to.

## 49.2 Projection purpose and schema

`booking_event_audit` (`database/migrations/0014_create_booking_event_audit_table`
— confirmed as the next available number by inspection, not assumed).
Columns, exactly the minimal set this step's own instructions named, no
more: `event_id` (primary key, the consumed envelope's own eventId — not
database-generated, mirroring `booking_outbox_events.event_id`'s identical
reasoning), `event_type`, `booking_id`, `user_id`, `show_id`,
`occurred_at`, `correlation_id`, `processed_at` (when *this* service
persisted the row — distinct from `occurred_at`, the upstream event's own
timestamp; the gap between the two is an observable measure of end-to-end
pipeline latency). Not copied: `seatIds`/`totalAmount` from the producer's
own payload, `eventVersion`, `producer`, `aggregateType` — this is an
asynchronous event-consumption record, not a second source of truth for
bookings, and the task's own instructions were explicit about not copying
the whole `Booking` entity.

`booking_id`/`user_id`/`show_id` are plain `UUID` references, not foreign
keys — the same reasoning `show_seats.show_id`/`seat_id` already
establishes for a cross-service reference that currently happens to share
one physical database.

## 49.3 Consumer group

`eventtick-booking-audit`, from `KAFKA_BOOKING_AUDIT_GROUP_ID` (default
that literal value) — fixed, never randomly generated, so committed
offsets survive a restart under the same identity.

**A real bug this step's own restart test found and fixed:** `@KafkaListener`'s
`id` attribute (set so a test can look the container up by name —
`idIsGroup` defaults to `true`) silently *becomes* the effective Kafka
consumer group id whenever `groupId` is not given separately — without an
explicit `groupId = "${spring.kafka.consumer.group-id}"` alongside `id`,
the live group would have been `"bookingCreatedListener"` (the annotation's
literal `id` value), never the configured, meaningful
`eventtick-booking-audit` at all. Caught by
`BookingCreatedConsumerRestartTest#consumerGroupId_isFixed_notRandomlyGenerated`
asserting the container's actual `groupId`, not merely reading the YAML
back — the exact gap that would have let the requirement silently fail
while looking correctly configured.

## 49.4 Event deserialization

Consumes `eventtick.booking` as a plain `String` (`StringDeserializer`,
mirroring booking-service's own `StringSerializer` producer choice — see
§48.4's "inspectable" reasoning) and parses it with a flexible Jackson
`JsonNode` tree — **not** a shared, typed `EventEnvelope` class (this
service has no dependency on booking-service's Maven artifact; no shared
module exists — §49.1) and **not** Spring Kafka's class-based
`JsonDeserializer<T>` either, so a future producer-side `eventVersion` bump
doesn't require this consumer's deserializer configuration to change in
lockstep. Reads `eventId`, `eventType`, `occurredAt`, `correlationId`, and
the nested `payload.bookingId`/`userId`/`showId` directly off the tree.
`eventType == "BookingCreated"` is checked explicitly before any further
processing; anything else is logged and skipped (49.7).

## 49.5 Idempotent consumption

The database's `event_id` primary key is the actual authority, not an
application-level `if (exists) return` (which races under concurrent
redelivery, per this step's own explicit warning). `BookingEventAuditService.persist`
attempts the insert directly (`saveAndFlush`) and catches
`DataIntegrityViolationException` — then **re-queries** `existsById(eventId)`
before deciding this was a genuine duplicate, rather than assuming any
constraint violation must be one. This mirrors
`PaymentService.recoverFromInsertRace`'s own established disambiguation
discipline exactly (re-query to confirm which constraint actually fired,
don't guess) — reused, not reinvented. A violation that is *not* the
expected `event_id` duplicate is rethrown uncaught, never silently treated
as "already processed."

Also uses `Persistable<UUID>` on `BookingEventAudit`, for the identical
reason `OutboxEvent` needed it (§48.5): `event_id` has no
`@GeneratedValue`, so without this, `save(...)` would `merge` instead of
`persist`, silently upserting on a collision instead of failing the
uniqueness check idempotency depends on. Applied proactively this time,
not rediscovered by a failing test.

## 49.6 Acknowledgement semantics

`spring.kafka.consumer.enable-auto-commit: false` +
`spring.kafka.listener.ack-mode: MANUAL_IMMEDIATE`: nothing commits a
Kafka offset merely because a record was polled. `BookingCreatedConsumer.onMessage`
calls `Acknowledgment#acknowledge()` only after
`BookingEventAuditService.persist(...)` has already returned — and that
call is itself a database write via an inherited, independently
transactional repository method (the exact reasoning already given for
`OutboxService`, §48.6, applied symmetrically to the consumer side). No
distributed transaction spans Postgres and Kafka; the ordering (DB commit,
*then* acknowledge) is the entire mechanism, matching this step's own
"do not acknowledge before the DB transaction commits" instruction
literally.

## 49.7 Failure behavior, duplicate delivery, future DLQ

Three outcomes, stated once, in `BookingCreatedConsumer`'s own class
Javadoc:

| Case | Outcome |
|---|---|
| Valid `BookingCreated` | Persisted, acknowledged |
| Duplicate `event_id` (redelivery) | Recognized via §49.5, acknowledged, no second row |
| Malformed JSON | Logged, **acknowledged** (skipped) — retrying can never fix it |
| Missing a required field | Logged, **acknowledged** (skipped) — same reason |
| Unknown `eventType` | Logged, **acknowledged** (skipped) — not this consumer's concern |
| Genuine persistence failure (not the expected duplicate) | **Not acknowledged** — propagates, retried |
| Kafka itself unavailable | The consumer naturally processes nothing; every other service (including this one's own HTTP actuator surface) starts and stays up regardless — no synchronous dependency on Kafka anywhere in this service's own startup path |

A genuine failure is retried by `KafkaConsumerConfig`'s `DefaultErrorHandler`
(`FixedBackOff(1000ms, UNLIMITED_ATTEMPTS)` — every second, forever, not a
small bounded count that would eventually give up and silently skip the
record). This is deliberately not a DLQ or a complex backoff framework, per
this step's own instructions. **Where a future DLQ would fit:** the three
"permanently unprocessable, acknowledge-and-skip" branches above (malformed
JSON, missing field, unknown type) are exactly where one belongs — instead
of a log line, that branch would publish the raw envelope to a
`eventtick.booking.dlq` topic for manual inspection/replay. Not built; no
DLQ topic, producer, or consumer exists.

## 49.8 Live verification

Run against a real local Kafka 4.3.1 broker (KRaft standalone mode, per
`kafka/README.md`), a real throwaway PostgreSQL 18.6 cluster (port 5433,
the same one used for Phase 15 Step 4's own live verification — see §25.4),
a real Redis, and all six service jars (gateway, user, catalog, booking,
payment, audit-service) as separate real OS processes — not `@EmbeddedKafka`,
not H2, not mocks. Every result below was read back from PostgreSQL with a
direct `psql` query, not inferred from application logs.

**End-to-end (Step 12).** Register → login → JWT via the real gateway;
`GET /api/catalog/shows/{id}` for an existing scheduled show; seat map via
`GET /api/bookings/shows/{id}/seats`; hold an `AVAILABLE` seat; `POST
/api/bookings` with an explicit `X-Request-ID`. Traced one booking
(`bookingId=46188c27-…`) through all four layers: `booking_outbox_events`
(`status=PUBLISHED`, `event_id=62bf443e-…`) → the real `eventtick.booking`
topic (read back verbatim with `kafka-console-consumer`) → the running
`audit-service` process's log (`recorded BookingCreated audit: eventId=…`)
→ `booking_event_audit`, where `event_id`, `booking_id`, `user_id`,
`show_id`, and `correlation_id` all matched the source booking and the
`X-Request-ID` sent on the request exactly; `processed_at` was ~2 seconds
after `occurred_at` — real consumer latency, not zero.

**Duplicate delivery (Step 13).** The exact JSON bytes of that same
already-published, already-consumed event were read back from the real
topic with `kafka-console-consumer` and re-published verbatim with
`kafka-console-producer` — a genuine redelivery of the same `eventId`, not
a second automated test process. The running `audit-service` logged the
insert's `DataIntegrityViolationException` (`Key (event_id)=(…) already
exists`), then `BookingEventAuditService`'s duplicate branch
(`already recorded — duplicate delivery, ignoring`), then acknowledged.
`select count(*) … where event_id = …` was `1`, both before and after.

**Restart (Step 14).** A booking was created, its audit row confirmed
present, then the real `audit-service` OS process was killed
(`taskkill /F`) — not a container stop/start inside one JVM. A brand-new
process (different PID) was started against the same `application.yml`.
`kafka-consumer-groups.sh --describe --group eventtick-booking-audit`
confirmed the same group id resumed with zero lag (no re-fetch of already-
committed offsets). The pre-restart booking's audit row count was still
exactly `1` (not reprocessed). A second booking created after the restart
was consumed normally, producing a correct new row — the group didn't just
stop working after coming back up.

All three live results match their automated-test equivalents
(`BookingCreatedConsumerEmbeddedKafkaTest`,
`BookingCreatedConsumerRestartTest`) exactly, on real infrastructure
instead of an embedded broker.

## 49.9 What is, and is not, implemented

**Implemented:** `audit-service` (a new, dedicated service, no Gateway
route, no Spring Security dependency); `booking_event_audit`; the
`BookingCreated` consumer, with real deserialization, idempotent
consumption enforced at the database level, manual commit-after-persist
acknowledgement, and indefinite retry on genuine failure; a fixed,
restart-stable consumer group.

**Not yet implemented:** consumption of `PaymentSucceeded`/`PaymentFailed`/
`PaymentExpired`, `BookingConfirmed`/`BookingCancelled`, or any catalog/user
event (none of those producers exist yet either — §48.9); a DLQ of any
kind; analytics; notifications; the ML pipeline (§47.10). The payment/
booking synchronous consistency mechanism (§25.3) remains completely
unmodified and untouched by this step — `PaymentService`, `PaymentStatus`,
`BookingSyncStatus`, `PaymentLifecycleScheduler`, and `BookingServiceClient`
were not changed; Kafka still plays no role in that path.

# 50. `PaymentSucceeded` Event + Payment Audit Consumption (Phase 16 Step 4)

Extends the event pipeline proven in §48 (producer/outbox) and §49
(consumer) to a second domain, payment-service, without inventing a
second design: the same transactional-outbox pattern, the same
raw-JSON-envelope consumer style, the same database-level idempotency —
each reused deliberately, not reinvented, per this step's own explicit
instruction not to duplicate a design that's already proven.

## 50.1 Payment outbox: reuse the design, not the table

payment-service gets its **own** copy of booking-service's outbox
infrastructure (`EventEnvelope`, `OutboxEventStatus`,
`PaymentOutboxEvent`/`PaymentOutboxEventRepository`/`PaymentOutboxService`/
`PaymentOutboxPublisher`, each field-for-field identical to their
booking-service namesakes) — not a shared table, not a shared Maven
module. `payment_outbox_events` (migration 0015) is a physically separate
table from `booking_outbox_events` (migration 0013): each service owns
its own persistence boundary (§17, BR-11), and a single shared
`outbox_events` table would have made that boundary meaningless the
moment a second producer needed one. This is exactly the collision the
`booking_` table-name prefix was chosen to avoid in Step 2 — now actually
avoided by a second real table, not just a convention nothing had tested
yet.

## 50.2 `PaymentSucceeded` event schema

Topic `eventtick.payment`, Kafka key = `paymentId` (per-payment
ordering, same `aggregateId`-as-key rule as `BookingCreated`). Payload
(`PaymentSucceededPayload`): `paymentId`, `bookingId`, `userId`,
`amount`, `currency`, `providerReference`. Deliberately excludes:
`idempotencyKey` (an internal API detail, meaningless downstream),
`status` (the event type already says `SUCCESS`, and `PaymentStatus`'s
own state machine makes that terminal and one-way — see §50.6), `provider`
(an implementation detail), any JWT/auth data, and any denormalized
booking data beyond the bare `bookingId` reference. `providerReference`
**is** included — the one piece of externally-meaningful evidence a real
charge happened, and exactly what §50.8's live verification checks
against the authoritative `payments.provider_reference` row.

## 50.3 Transaction boundary

The `SUCCESS` status write and the `PaymentSucceeded` outbox row commit
**atomically, in one database transaction** — this step's central,
explicitly-required guarantee. Sequence, inside
`PaymentSuccessRecorder#recordSuccess`:

```
@Transactional
    payment.status -> SUCCESS   (already validated/set in-memory by caller)
    paymentRepository.save(payment)
    paymentOutboxService.record(PaymentSucceeded, ...)
COMMIT
```

Only *after* that method returns — strictly outside any transaction —
does `PaymentService.chargeAndResolve` call the existing, unmodified
`bookingServiceClient.confirmBooking(...)`. The ordering is unchanged
from before this step: booking confirmation was already best-effort,
called after the payment row's own commit, with its outcome recorded in
`bookingSyncStatus` rather than thrown back as a payment failure (§25.1/
§25.3). This step adds one more thing to that same already-committed
moment (the outbox row); it does not touch the HTTP call or its
failure-handling at all.

If publishing to Kafka later fails or Kafka is entirely unreachable, the
outbox row simply stays `PENDING` — the payment is unaffected, already
durably `SUCCESS`, already returned to the caller. See §50.8 (Step 12)
for the live proof.

## 50.4 Why the atomic write needed a separate bean

Spring's `@Transactional` is proxy-based: it only takes effect on a call
that actually goes through the managed bean's proxy. `PaymentService`'s
own entry point (`createPayment`) is deliberately **not**
`@Transactional` (§25's own established reasoning — it spans outbound
HTTP calls to booking-service and the payment provider, and holding a DB
transaction open across those would be wrong). Everything
`chargeAndResolve` calls internally — `this.chargeAndResolve(...)`,
`this.transitionTo(...)` — is self-invocation, which bypasses the proxy
entirely; annotating any of those methods `@Transactional` would silently
do nothing (a well-documented Spring pitfall, confirmed here by design
review, not left to be discovered by a flaky test later).

The fix: `PaymentSuccessRecorder` is a **separate** Spring bean, injected
into `PaymentService` and called from `chargeAndResolve` as a genuine
external bean-to-bean call — which *does* go through
`PaymentSuccessRecorder`'s own proxy, so its `@Transactional` on
`recordSuccess` actually applies. `PaymentService` still owns all
transition **validation** (via the centralized `PaymentStatus.canTransitionTo`,
unchanged); `PaymentSuccessRecorder` only owns the atomic **persistence**
of an already-validated transition plus its event.

## 50.5 Correlation ID

Reused, not reinvented: `PaymentController.createPayment` now reads
`X-Request-ID` (the same header `BookingController.createBooking` already
reads — the Gateway sets it on every routed request) and threads it
through `PaymentService.createPayment` → `chargeAndResolve` →
`PaymentSuccessRecorder.recordSuccess` → `PaymentOutboxService.record`,
which becomes the envelope's `correlationId` — letting one payment
attempt be traced across the Gateway's own logs, payment-service, and the
audit projection, exactly like `BookingCreated`'s correlation id already
does (§49's own `correlation_id` column). `PaymentOutboxService.record`
keeps booking-service's own documented fallback (a fresh UUID if blank)
for symmetry with a hypothetical future background-triggered event, even
though nothing in this step actually triggers `PaymentSucceeded` from a
background process — see §50.6.

## 50.6 Producer-side duplication safety

`PaymentStatus.SUCCESS` is **terminal with no outgoing transitions**
(`ALLOWED_TRANSITIONS.put(SUCCESS, EnumSet.noneOf(...))`, unchanged by
this step) — a payment can reach `SUCCESS` at most once in its entire
lifetime, structurally, not by convention. Because
`PaymentSuccessRecorder.recordSuccess` is only ever called from the one
branch in `chargeAndResolve` that executes immediately after that one
`PENDING -> SUCCESS` transition, a second, semantically-duplicate
`PaymentSucceeded` event is not just avoided by discipline — there is no
code path that could produce one:

- **A replayed `createPayment` call** (same idempotency key) short-circuits
  at the very first check (`findByIdempotencyKey`) and returns the
  existing payment without ever reaching `chargeAndResolve` again.
- **Reconciliation** (`reconcilePendingBookingSync`) only retries the
  best-effort `confirmBooking`/`releaseBooking` HTTP call for payments
  whose `bookingSyncStatus` is still `PENDING` — it never touches
  `PaymentSuccessRecorder`, `PaymentOutboxService`, or the payment's own
  `status` at all.
- **The expiration sweep** only ever moves `PENDING -> EXPIRED`, never
  touches a `SUCCESS` payment.

Live-proven, not just reasoned about (§50.8): a replay with the same
idempotency key, and a reconciliation sweep run after a successful
payment, were both checked directly against `payment_outbox_events` —
exactly one row either way.

## 50.7 Payment audit consumer

Extends **audit-service** (not a new service — this step's own explicit
instruction) with a second `@KafkaListener`, `PaymentSucceededConsumer`,
consuming `PaymentSucceeded` from `eventtick.payment` into a new
projection table, `payment_event_audit` (migration 0016): `event_id`
(primary key), `event_type`, `payment_id`, `booking_id`, `user_id`,
`amount`, `currency`, `provider_reference`, `occurred_at`,
`correlation_id`, `processed_at`. Same "audit projection, not a second
source of truth" reasoning as `booking_event_audit` (§49.2).

**Consumer group:** `eventtick-payment-audit` — a distinct, fixed,
restart-stable id, deliberately **not** `eventtick-booking-audit`.
`eventtick.booking` and `eventtick.payment` are two different topics with
two entirely independent consumption progress/offsets; Kafka partition
assignment and rebalancing are scoped per group, so sharing one group id
across both listeners would conflate two unrelated consumption streams —
a real correctness concern, not just tidiness. Configured via
`eventtick.consumers.payment-audit-group-id`
(`KAFKA_PAYMENT_AUDIT_GROUP_ID` env var), referenced directly in
`PaymentSucceededConsumer`'s own `@KafkaListener(groupId = ...)` — the
same explicit-groupId discipline `BookingCreatedConsumer` already
established, for the same `idIsGroup` reason (§49.3).

**Idempotency, acknowledgement, and failure classification:** identical
in every respect to `BookingCreatedConsumer`/`BookingEventAuditService`
(§49.5–§49.7) — insert-then-catch on `event_id`'s own primary key as the
final authority (never a check-then-insert race), manual
commit-after-persist acknowledgement (`MANUAL_IMMEDIATE`), and the same
three-way classification (permanently unprocessable → ack+skip; duplicate
→ ack; genuine failure → never ack, retried by the same shared
`KafkaConsumerConfig` `DefaultErrorHandler` bean, which applies to every
listener container in the service — no second retry framework was
introduced). `providerReference` is the one payload field treated as
optional (nullable in the payload and the projection), matching §50.2's
own payload design — its absence is not classified as "missing a required
field."

## 50.8 Live verification

Run against the same real Kafka 4.3.1 broker, real throwaway PostgreSQL
(port 5433), real Redis, and all six service jars as separate OS
processes already set up for §49.8's own live verification — migrations
0015/0016 applied first.

**Payment SUCCESS, end to end (kickoff Step 11).** A fresh booking, paid
via a real `POST /api/payments` through the real Gateway. Traced payment
`ddbab80d-…` through every layer: `payments` (`SUCCESS`,
`booking_sync_status=DONE`), `bookings` (`CONFIRMED`), `show_seats`
(`BOOKED`), `payment_outbox_events` (`PUBLISHED`, `event_id=6779b3eb-…`)
→ the real topic → the running audit-service's log → `payment_event_audit`,
where `payment_id`, `booking_id`, `user_id`, `amount`, `currency`,
`provider_reference`, and `correlation_id` all matched the source payment
and the `X-Request-ID` sent on the request exactly.

**Kafka unavailable (kickoff Step 12) — the central architectural proof.**
Rather than stopping the shared Kafka broker (audit-service's own,
already-running consumer also depends on it, and killing shared dev
infrastructure mid-session was avoided deliberately), payment-service
alone was restarted with `KAFKA_BOOTSTRAP_SERVERS` pointed at an
unreachable address — the identical observable condition ("Kafka
unreachable from payment-service") without disrupting other components.
A new payment still reached `SUCCESS`; its booking still became
`CONFIRMED`; its outbox row stayed `PENDING`, with `attempts` genuinely
incrementing (2, confirmed by polling) as `PaymentOutboxPublisher`'s
sweep kept trying and failing every 5 seconds — no payment or booking
rollback either way. payment-service was then restarted pointed at the
real broker again: the previously-`PENDING` row transitioned to
`PUBLISHED` on the very next sweep, and the real, running audit-service
consumed it normally seconds later — full recovery, unattended.

**Duplicate delivery, live (kickoff Step 13).** The first live payment's
exact `PaymentSucceeded` bytes were read back from the real topic with
`kafka-console-consumer` and re-produced verbatim. The running
audit-service logged the constraint-violation → duplicate-branch path
exactly as designed; `payment_event_audit` stayed at exactly one row for
that `event_id`; the `payments`/`bookings` rows were untouched (the
consumer never calls either service — structurally cannot cause a second
charge).

**Restart, live (kickoff Step 14).** A payment was created and its audit
row confirmed present; the real audit-service OS process was killed
(`taskkill /F`) and a fresh process (different PID) started against the
same config. `kafka-consumer-groups.sh --describe --group
eventtick-payment-audit` confirmed the same group id resumed with zero
lag. The pre-restart event's row count was still exactly 1 (not
reprocessed). A payment created after the restart was consumed normally.

**Failure path unaffected (kickoff Step 15).** A payment forced to
`FAILED` (`MockPaymentProvider`'s `FORCE_FAIL_` test key) still produced
`bookings.status=CANCELLED` and `show_seats.status=AVAILABLE` exactly as
before this step, with **zero** rows in either `payment_outbox_events` or
`payment_event_audit` for that payment — `PaymentSucceeded` is never
published for a non-`SUCCESS` outcome, confirmed directly, not just by
code inspection.

## 50.9 What is, and is not, implemented

**Implemented:** payment-service's own transactional outbox
(`payment_outbox_events`, `PaymentOutboxService`/`PaymentOutboxPublisher`);
`PaymentSucceeded`, published atomically with a payment's `SUCCESS`
transition via `PaymentSuccessRecorder`; a second audit-service consumer
(`PaymentSucceededConsumer`) with its own fixed, distinct consumer group
(`eventtick-payment-audit`) and its own projection
(`payment_event_audit`); correlation id propagation from
`X-Request-ID` through to the consumed projection; database-level
idempotent consumption; Kafka-unavailability proven, live, to never
affect payment `SUCCESS`.

**Not implemented (explicitly out of scope for this step):**
`PaymentFailed`, `PaymentExpired` (payment-service publishes neither —
§50.9's own live check confirms a `FAILED` payment produces no event at
all, not merely "not yet consumed"); `BookingConfirmed`/`BookingCancelled`;
any catalog/user event; a DLQ of any kind; notifications; analytics; the
ML pipeline. The payment/booking synchronous consistency mechanism
(§25.3) remains completely unmodified — `PaymentStatus`,
`BookingSyncStatus`, `PaymentLifecycleScheduler`, and
`BookingServiceClient` were not changed; `booking_sync_status` remains
the sole authoritative confirm/cancel-acknowledgement mechanism, with
`PaymentSucceeded` existing purely as an independent, best-effort,
asynchronous broadcast alongside it (FR-52).

---

# 51. Phase 17 — Frontend Integration (Implemented, with explicit gaps)

**Status: the frontend's core catalog-browse → show-select → seat-hold →
booking → payment path calls real backend services through the Gateway,
end to end.** Earlier phases (§25, §47–§50) built and live-verified the
backend; this phase replaced the matching frontend mock functions in
`eventtick/src/services/api.ts` with real `fetch` calls through
`BASE_URL` (`http://localhost:8080`), never a direct call to any service
port. `docs/requirements.md` §28 (FR-57–FR-59) states the requirements
this satisfies; this section states the architecture.

## 51.1 What is real

| Frontend function (`api.ts`) | Real endpoint | Backend owner |
|---|---|---|
| `login`, `signup`, `logout`, `getCurrentUser` | `POST /api/auth/{login,register}`, `GET /api/users/me` | user-service |
| `getEvents`, `getEventById` | `GET /api/catalog/content(/{id})` | catalog-service |
| `getVenueById`, `getVenuesByCity` | `GET /api/catalog/venues(/{id})` | catalog-service |
| `getShowsByEvent`, `getShowById` | `GET /api/catalog/shows(/{id})` | catalog-service |
| `getSeatMap` | `GET /api/catalog/seats?venueId=`, `GET /api/bookings/shows/{id}/seats` | catalog-service + booking-service, joined client-side |
| `holdSeats` | `POST /api/bookings/shows/{showId}/seats/hold` | booking-service |
| `createBooking`, `getBooking` | `POST /api/bookings`, `GET /api/bookings/{id}` | booking-service |
| `createPayment` | `POST /api/payments` | payment-service |
| `getAdminOverviewStats`, `getRateLimitStats` | `GET /api/admin/{users,content,bookings}/stats`, `GET /api/admin/rate-limits/stats` | user/catalog/booking-service, gateway-service |

Every call carries the authenticated user's real JWT (`auth: true` in the
shared `request()` helper); no user id, booking id, payment id, show id,
or credential is hardcoded anywhere in this layer.

## 51.2 Catalog's real contract, and what the frontend adapted to

Catalog Service's list endpoints (`GET /api/catalog/{content,venues,shows}`)
take **no query parameters at all** — no `type`/`contentId`/`city` filter,
no pagination, no search (§8). Rather than inventing endpoints that don't
exist, the frontend fetches the full list and filters client-side
(`getEvents(type)`, `getShowsByEvent(contentId)`,
`getVenuesByCity(city)`) — acceptable at this project's seed-data scale,
not a pattern that would survive real volume. `getTrendingEvents`/
`getFeaturedEvents` remain mock: Catalog Service's `Content` entity has no
trending/featured concept, and `searchEvents` remains mock: Catalog
Service has no search endpoint (FR-09 is unimplemented on the backend).

Real `Content` (§9) has no image/banner/rating/cast/price/city columns;
real `Venue` (§11) has no state/pincode. The frontend's `Venue.state`/
`pincode` fields were changed from required to optional to stop claiming
data the backend doesn't have, and the UI shows a generic placeholder
image rather than a fabricated one for content with none. `EventFilters`'
`city`/`genre`/`sport`/`minRating` fields still exist in the frontend but
are inert against real data (no caller currently passes them) — documented
rather than removed, since removing working filter UI was out of this
phase's scope.

## 51.3 Booking and payment: sequencing, idempotency, and the sync boundary

`BookingSummary.tsx` performs `createBooking` then, on success,
`createPayment` — never the reverse, and never with a client-chosen
amount (payment-service computes it server-side from the booking's own
`total_amount`, §25.1). The payment idempotency key is derived
deterministically as `booking-${bookingId}-payment`, not generated via
`crypto.randomUUID()` into component state — a re-render, remount, or
retry of the same checkout attempt for the same booking always reuses the
same key, which payment-service treats as a safe replay (§25.2/FR-46)
rather than a new charge. The existing `loading` state/disabled button
(unchanged from the booking-only integration) blocks a concurrent
duplicate submission across the whole booking→payment sequence, not just
the booking step.

**The synchronous confirm boundary (§25.1/FR-43/FR-52) directly shapes the
frontend's post-payment behavior.** A `SUCCESS` response from
`POST /api/payments` means payment-service already called
booking-service's internal confirm endpoint *before* that HTTP response
returned — not an asynchronous Kafka-driven confirmation (an earlier
framing of this work incorrectly assumed Kafka; corrected here against
the actual code, §47.8/§50). That synchronous call can rarely still be
settling via the `booking_sync_status` reconciliation sweep (§25.3/FR-45a)
when the response arrives, so the frontend re-fetches the booking
(`getBooking`) up to three times with a bounded one-second gap — never an
unbounded loop — before displaying whatever status Booking Service
actually reports. A booking that is still `PENDING` after that bounded
window is shown as `PENDING`, not silently upgraded to `CONFIRMED` on the
frontend.

## 51.4 The catalog ↔ booking inventory gap, surfaced concretely

§17 documents that no API creates `show_seats` rows. This phase makes the
consequence concrete: a `Show` reachable through the now-real
`GET /api/catalog/shows/{id}` is not guaranteed to have any `show_seats`
rows in booking-service at all, and the frontend has no way to detect this
distinction in advance — `getSeatMap` simply returns an empty seat layout
for such a show, and `EventDetails.tsx` falls back to its existing "No
Shows Available"/empty-seat-map handling (built for the ordinary
empty-catalog case, not originally written with this specific gap in
mind, but behaviorally adequate for it). No frontend code was added to
paper over this — it is a backend data-model gap, not something the
client layer should compensate for.

## 51.5 Error handling

Every newly-real call can now genuinely fail (network error, 401/403/404/
409/429/5xx) where its mock predecessor never could. Pages that call these
functions (`Events`, `Movies`, `Concerts`, `Sports`, `Theatre`, `Home`,
`EventDetails`, `SeatSelection`, `BookingSummary`) were each given an
explicit error state reusing the existing `EmptyState` component/inline-
banner convention already established for the booking flow — a failed
real request shows an error, never silently falls back to stale or
fabricated data, and never leaves a loading spinner stuck indefinitely.

## 51.6 What remains mock, deliberately

`getTrendingEvents`, `getFeaturedEvents`, `searchEvents` (no backend
capability to call, §51.2); `getBookings`, `getBookingById`,
`cancelBooking` (booking history/cancellation UI); `getPlans`,
`subscribeToPlan` (user-service has real plan data, but the frontend
Plans page was not reached this phase); `getAdminStats`,
`getRateLimitPolicies` (their real counterparts,
`getAdminOverviewStats`/`getRateLimitStats`, exist and are used by the
Admin dashboard's Phase 13 sections — §45 — but these two originals
remain for dashboard sections Phase 13 never built a frontend for).

## 51.7 Live verification performed this phase

The core sequence (login → real event → real show → real seat map → hold
→ create booking → create payment → `SUCCESS` → booking `CONFIRMED` →
seat `BOOKED` → frontend ticket page) was live-verified end to end through
the Gateway in an earlier session of this same integration work. During
the specific session that added catalog/show/venue integration,
`catalog-service` could not be started in that environment (a local
PostgreSQL role/credential mismatch unrelated to any code in this
project), so only the Gateway's authentication and failure-response
behavior for `/api/catalog/**` were verified live at that time (a real,
freshly-registered test user; `401` unauthenticated, clean `503` with
catalog-service down, both in well under a second — confirming the
Gateway boundary and the frontend's error-handling path, not the
catalog happy path itself in that specific session).

---

# 52. Phase 18 — Show-Seat Inventory Creation API

**Status: implemented.** `POST /api/admin/shows/{showId}/seats`
(booking-service) closes the gap §17 documents: until this phase, nothing
in the codebase created a `show_seats` row outside a JUnit test or a
manual SQL insert.

## 52.1 Why booking-service, not catalog-service

`show_seats` is booking-service's own table (§17's ownership boundary:
Catalog Service owns `shows`/`seats`; Booking Service owns whether a given
seat is available/held/booked for a given show). The new endpoint
therefore lives in booking-service, alongside the existing read-only seat
map (`ShowSeatQueryService`) and the existing lifecycle transitions
(`BookingService`), as a new, separate `ShowSeatInventoryService` —
inventory *creation* is a different concern from both (it inserts brand
new rows; it never transitions an existing row's status), so it gets its
own class rather than being added to either existing one.

## 52.2 The ownership question: no new cross-service HTTP call

The kickoff for this phase explicitly asked whether booking-service
should validate a show's existence against its own database or by calling
catalog-service, and said not to choose arbitrarily. Inspection of the
existing code answered this: **neither.** `ShowSeat`'s own class Javadoc
already documents that `showId`/`seatId` are plain UUID foreign-key
references, with no JPA relationship to a Show/Seat entity, specifically
so the mapping stays correct if the databases are ever physically split
— and no existing booking-service code path (`holdSeats`, `createBooking`)
validates show/seat existence at all, against either its own database or
catalog-service's API. The new endpoint follows this exact, already-
established precedent: it does not call catalog-service, and it does not
query the `shows`/`seats` tables directly (which would mean adding a JPA
entity for tables this service doesn't own, undoing the separation
`ShowSeat`'s Javadoc deliberately preserves). The real safety net is the
database itself: `fk_show_seats_show`/`fk_show_seats_seat`
(migration 0008, unchanged, already in the real PostgreSQL schema) reject
a nonexistent show or seat at insert time, in production, exactly as they
already silently did for every write to this table before this phase.
This cannot be exercised by this project's H2-backed tests (Hibernate
only generates a foreign key from a mapped JPA relationship, and this
entity deliberately has none — the same reason FR-47's partial-unique-
index test gap exists), so it is not something this phase's test suite
asserts; it is a real constraint in the real database regardless.

## 52.3 Duplicate protection: application-level, not just the database

`uq_show_seats_show_seat UNIQUE (show_id, seat_id)` (migration 0008) was
already sufficient — **no migration was created for this phase.**
`ShowSeatInventoryService.createSeats` pre-checks for an existing
(show, seat) pairing with a plain repository read (`findByShowId`,
already used by `ShowSeatQueryService`) before inserting, rather than
relying on catching the database's own constraint violation — the
`PaymentService`-style "pre-check plus `DataIntegrityViolationException`
backstop" pattern (FR-46) was considered and deliberately not copied here:
that pattern earns its complexity under genuine concurrent-retry traffic
(a customer's browser retrying a payment), which this low-frequency,
administrator-only seeding operation does not have. A rejected pre-check
throws `DuplicateShowSeatException` (409 `DUPLICATE_SHOW_SEAT`). A
duplicate seat id *within the same request* is a different, request-shape
problem, checked first and separately: it throws `IllegalArgumentException`
(400 `VALIDATION_ERROR`), mirroring `BookingService.requireNonEmpty`'s
existing "duplicate seat ids in request" check for `holdSeats`/
`createBooking`. Both checks run before any row is persisted, and the
whole operation is one `@Transactional` method — all-or-nothing.

## 52.4 Request/response contract

```
POST /api/admin/shows/{showId}/seats
{ "seats": [ { "seatId": "<uuid>", "price": 450.00 }, ... ] }

201 Created
Location: /api/bookings/shows/{showId}/seats
{ "showId": "<uuid>", "seats": [ { "showSeatId": "<uuid>", "seatId": "<uuid>", "status": "AVAILABLE", "price": 450.00 }, ... ] }
```

The response reuses the existing `SeatMapResponse`/`SeatMapItemDto` shape
— exactly what `GET .../seats`/`POST .../seats/hold` already return —
rather than inventing a second seat model for the same data. `status` is
never a request field: every newly created row starts `AVAILABLE`, the
same "not a real decision, so not a caller-supplied parameter" reasoning
`ShowService.create` already applies to a new Show's status (§8).

## 52.5 Authorization: no new security mechanism

No security code changed anywhere, in either service. `/api/admin/**`
already required `ROLE_ADMIN` at both the Gateway
(`GatewaySecurityConfig`) and booking-service's own `SecurityConfig`
(defense in depth, Phase 15 Step 4) before this phase, as a path-based
rule — the new endpoint's path already matches it. The only new
configuration is one explicit Gateway route entry
(`admin-show-seats-create`, `Path=/api/admin/shows/{showId}/seats` →
booking-service), following the exact same one-literal-path-per-endpoint
discipline every other admin route in this project already uses — no
`/api/admin/shows/**` wildcard was introduced, and the route does not
collide with the sibling `.../seat-activity`, `.../cancel`, or bare
`.../{id}` routes (each has a different, distinct final path segment or
depth).

## 52.6 What changed, concretely

New (booking-service): `ShowSeatInventoryService`,
`AdminShowSeatInventoryController`, `CreateShowSeatsRequest`,
`ShowSeatDefinition`, `DuplicateShowSeatException`, plus one new
`GlobalExceptionHandler` entry (`DUPLICATE_SHOW_SEAT` → 409). One existing
file changed: `ShowSeat`'s no-arg constructor went from `protected` to
`public` — it was `protected` only because, until this phase, nothing in
production code legitimately constructed a new `ShowSeat` (every status
transition mutated an existing, already-persisted row; only JPA itself and
test reflection ever called it). New (gateway-service): one route entry,
no security-rule change. No database migration — the schema already
supported everything this endpoint needed (table, columns, `AVAILABLE`
default, the unique constraint, both foreign keys).

## 52.7 What this phase does not change

Seat holds remain exactly as §16 describes: a plain PostgreSQL status
column under row-level locking, no Redis, no ownership tracking, no TTL —
this phase only creates rows in the `AVAILABLE` state; it does not touch
`holdSeats`, `releaseHold`, `confirmBooking`, or `cancelBooking`, and
nothing about seat-hold architecture changed. Catalog Service still has no
mechanism — automatic or otherwise — that calls this new endpoint when a
`Show` is created; an administrator must still call it separately,
per show. That remaining gap (§17) is unchanged by this phase.
