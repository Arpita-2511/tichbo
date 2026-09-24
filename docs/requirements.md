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
* **FR-27** (dynamic rate limiting) — **implemented for the "vary by
  plan/route group/request type" part.** The policy resolved for a request
  depends on its category (route group) and the caller's plan/role (from
  the validated JWT) — see `RateLimitPolicyResolver`. **Not implemented**:
  "administrative configuration" as a policy dimension — see FR-31.
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
* **FR-31** (administrative rate-limit configuration) — **not
  implemented.** Policy values (now a full category × tier matrix) are read
  from configuration/environment variables at startup; there is no
  runtime/admin-configurable policy store and no way to change a policy
  without restarting the Gateway. Explicitly Phase 13+ (and the Admin
  Dashboard phase).
* **FR-32** (rate-limit failure handling) — implemented, with an
  explicit, documented policy: if Redis is unreachable, the limiter fails
  open (requests are allowed, not blocked) rather than silently bypassing
  the *concept* of a failure policy — see `docs/architecture.md`'s Phase 12
  section for the reasoning. Unchanged since Phase 11.

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
  in the frontend still uses mock data.

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

## Next Phase

The API Gateway. (Frontend ↔ Authentication integration is done — see
above.)
