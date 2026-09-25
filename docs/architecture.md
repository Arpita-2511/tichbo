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
                     ┌──────────────────┼──────────────────┐
                     │                  │                  │
                     ▼                  ▼                  ▼
              ┌────────────┐     ┌────────────┐     ┌────────────┐
              │   User     │     │  Catalog   │     │  Booking   │
              │  Service   │     │  Service   │     │  Service   │
              └─────┬──────┘     └─────┬──────┘     └─────┬──────┘
                    │                  │                  │
                    ▼                  ▼                  ▼
              User Data          Catalog Data        Booking Data
                    │                  │                  │
                    └──────────────────┼──────────────────┘
                                       │
                                ┌──────┴──────┐
                                │ PostgreSQL  │
                                │             │
                                │ Persistent  │
                                │ Data        │
                                └─────────────┘

                                       ▲
                                       │
                                ┌──────┴──────┐
                                │    Redis    │
                                │             │
                                │ Rate Limit  │
                                │ Seat Holds  │
                                │ Cache        │
                                └─────────────┘
```

Additional components such as Payment Service, Notification Service, Kafka, Prometheus, Grafana, and an ML Service can be introduced progressively.

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

### Future Caching

Frequently requested catalog information can potentially be cached later.

Redis is not intended to replace PostgreSQL as the durable source of business records.

---

# 20. Dynamic Rate Limiting

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
| Redis           | Rate-limit state, temporary holds, optional cache          |
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
  Service (Phase 7.2); everything else is still mock data.

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
limiting; all explicitly future work beyond Phase 12. The mock-data parts
of the frontend don't use the gateway yet.

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

## Current Status

The API Gateway (§5–§6) is implemented: request routing to the three
services, JWT validation at the edge, and dynamic rate limiting backed by
Redis (§19–§22) are all in place.

The Admin Dashboard (§45) is the project's current area of work. Phase 13
implementation is in progress — see §45's status table and
`docs/requirements.md` §15 for the current status of each requirement
(FR-35–FR-40).
