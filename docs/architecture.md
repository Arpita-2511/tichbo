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
* Eventtick frontend prototype (§4), currently on mock data only.

The API Gateway (§5–§6) — the project's stated primary engineering
focus — has **not** been implemented yet. Everything above was built
directly against each service; there is no Gateway routing, JWT
validation at the edge, or dynamic rate limiting in front of them yet.

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

## Next Phase

Frontend ↔ Authentication integration, then the API Gateway (§5–§6):
request routing to the three services, JWT validation at the edge, and
eventually dynamic rate limiting backed by Redis (§19–§22). None of that
exists yet — the Gateway remains the largest unbuilt piece of the
project's stated primary objective.
