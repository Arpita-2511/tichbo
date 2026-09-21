# Tichboo — System Architecture

## 1. Architecture Overview

Tichboo uses a microservices-based architecture.

The system is divided into independently responsible services rather than implementing all functionality inside one backend application.

The major architectural components are:

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
                    │ Rate Limiting     │
                    │ Logging           │
                    └─────────┬─────────┘
                              │
             ┌────────────────┼────────────────┐
             │                │                │
             ▼                ▼                ▼
       ┌───────────┐   ┌────────────┐   ┌────────────┐
       │   User    │   │  Catalog   │   │  Booking   │
       │  Service  │   │  Service   │   │  Service   │
       └─────┬─────┘   └─────┬──────┘   └─────┬──────┘
             │               │                │
             ▼               ▼                ▼
          User DB        Catalog DB       Booking DB
                                             │
                                             ▼
                                           Redis
```

Additional services can be introduced as the project evolves.

---

# 2. Architectural Objectives

The architecture is designed to achieve:

* Separation of concerns
* Independent service ownership
* Centralized request management
* Dynamic traffic control
* Secure authentication
* Concurrency-safe booking
* Independent scalability
* Fault isolation
* Observability
* Future ML integration

---

# 3. Client Layer

The client is the user-facing web application.

The planned frontend technology is:

* React or Next.js
* TypeScript
* Tailwind CSS

The frontend provides:

* Registration
* Login
* Home/catalog
* Search
* Event/show details
* Seat selection
* Booking
* Booking history
* User profile
* Admin dashboard

The frontend communicates with the backend through the API Gateway.

It should not directly access internal microservices.

---

# 4. API Gateway

The API Gateway is the central entry point into the backend.

Planned implementation:

**Spring Cloud Gateway**

Architecture:

```text
Client
   |
   ▼
API Gateway
   |
   ├── User Service
   ├── Catalog Service
   ├── Booking Service
   └── Payment Service
```

The Gateway provides cross-cutting functionality that should apply across multiple services.

---

# 5. Gateway Responsibilities

## 5.1 Request Routing

The Gateway routes incoming requests to the appropriate service.

Conceptually:

```text
/api/users/**       → User Service
/api/events/**      → Catalog Service
/api/shows/**       → Catalog Service
/api/bookings/**    → Booking Service
/api/payments/**    → Payment Service
```

The exact route configuration will be finalized during API implementation.

---

## 5.2 Authentication

The Gateway can validate JWT tokens before forwarding protected requests.

```text
Request
   |
   ▼
Gateway
   |
   ▼
JWT Validation
   |
   ├── Invalid → Reject
   |
   └── Valid
         |
         ▼
    Forward Request
```

Authorization rules remain the responsibility of the appropriate business service as required.

---

## 5.3 Rate Limiting

The Gateway performs rate-limit checks before forwarding requests.

```text
Request
   |
   ▼
Gateway
   |
   ▼
Rate-limit check
   |
   ├── Allowed → Backend Service
   |
   └── Exceeded → HTTP 429
```

This protects downstream services from excessive traffic.

---

## 5.4 Request Logging

The Gateway records request-related information for operational visibility.

---

## 5.5 Request ID

Each request can receive a unique request identifier.

Example:

```text
Client
   |
   | Request ID: ABC123
   ▼
Gateway
   |
   | ABC123
   ▼
Booking Service
```

This allows the request to be traced across multiple services.

---

# 6. User Service

The User Service owns user/account functionality.

Responsibilities:

* Registration
* Login
* User profile
* User roles
* Subscription plans
* Account management

Conceptual data:

```text
User
----------------
id
name
email
password_hash
role
plan
created_at
updated_at
```

The User Service owns its user data.

Other services should not directly query the User Service database.

---

# 7. Catalog Service

The Catalog Service manages bookable content.

Supported categories:

```text
Movie
Event
Sports Match
Show
```

Responsibilities:

* Event information
* Movie information
* Match information
* Show schedules
* Venue information
* Seat-layout information

Conceptually:

```text
Content
   |
   ▼
Show
   |
   ▼
Venue
   |
   ▼
Seat Layout
```

---

# 8. Booking Service

The Booking Service owns the booking lifecycle.

Responsibilities:

* Seat availability
* Seat holds
* Booking creation
* Booking cancellation
* Booking history
* Booking state
* Concurrency control

The Booking Service is the service responsible for deciding whether a requested seat can actually be reserved.

---

# 9. Booking State Model

A conceptual booking state may be:

```text
PENDING
   |
   ├── SUCCESS
   |
   └── FAILED
```

Seat state:

```text
AVAILABLE
    |
    ▼
  HELD
    |
    ▼
 BOOKED
```

Expired holds:

```text
HELD
  |
  | hold expires
  ▼
AVAILABLE
```

---

# 10. Concurrent Booking

Concurrency is a critical requirement.

Suppose:

```text
Show A
Seat A10
```

Three users request the same seat simultaneously:

```text
User A ──┐
User B ──┼──> A10
User C ──┘
```

The Booking Service must ensure that only one request successfully reserves the seat.

The system will use database transaction/concurrency-control mechanisms to make the seat allocation operation atomic.

The frontend cannot be trusted to enforce this rule.

---

# 11. Seat Hold Architecture

Redis can be used for temporary seat holds.

Conceptually:

```text
User selects seat
       |
       ▼
Booking Service
       |
       ▼
Redis
       |
       ▼
Temporary hold
       |
       ├── Payment/booking succeeds
       │       ↓
       │     BOOKED
       │
       └── Timeout
               ↓
           AVAILABLE
```

The durable booking record remains in PostgreSQL.

---

# 12. Database Architecture

The architecture follows the database-per-service principle.

```text
User Service
     |
     ▼
 User Database

Catalog Service
     |
     ▼
Catalog Database

Booking Service
     |
     ▼
Booking Database

Payment Service
     |
     ▼
Payment Database
```

A service should not directly access another service's database.

Communication should happen through:

* REST APIs
* Events/messages

---

# 13. PostgreSQL

PostgreSQL is the primary persistent database technology.

It stores durable business information such as:

* Users
* Events
* Shows
* Bookings
* Payments
* Venues

The exact schema and relationships will be designed in Phase 2.

---

# 14. Redis

Redis is used for fast-changing state.

Primary use cases:

### Rate Limiting

```text
User + Endpoint
      |
      ▼
Redis counter
```

### Temporary Seat Holds

```text
Seat
 |
 └── Temporary hold
```

### Future Caching

Frequently requested information can potentially be cached later.

Redis is not intended to replace PostgreSQL for durable business records.

---

# 15. Dynamic Rate Limiting

The rate limiter is one of the core architectural features of Tichboo.

Instead of defining a single hard-coded limit:

```text
100 requests/minute
```

the system uses configurable policies.

Conceptually:

```text
User
  |
  ▼
Identify Plan
  |
  ▼
Identify Endpoint
  |
  ▼
Load Policy
  |
  ▼
Redis
  |
  ├── Allowed
  │      ↓
  │   Backend
  │
  └── Exceeded
         ↓
       429
```

---

# 16. Rate-Limit Policy

A policy can conceptually contain:

```text
Policy
-------------------------
plan
endpoint
request_limit
time_window
enabled
```

For example:

```text
FREE
PREMIUM
VIP
```

may have different configured limits.

The actual numeric values will be determined during implementation/testing.

---

# 17. Dynamic Policy Management

The important distinction is:

```text
Static Rate Limiting
       ↓
Limit exists in application code
```

versus:

```text
Dynamic Rate Limiting
       ↓
Policy stored/configured externally
       ↓
Gateway reads current policy
       ↓
Policy can be changed
```

This allows administrators to modify traffic policies without rebuilding the Gateway.

---

# 18. Authentication Architecture

Authentication uses Spring Security and JWT.

General flow:

```text
User
 |
 | Login
 ▼
User Service
 |
 | Validate credentials
 ▼
JWT
 |
 ▼
Client
 |
 | Authorization header
 ▼
API Gateway
 |
 | Validate token
 ▼
Protected Service
```

The JWT can contain claims required for authorization.

Sensitive information should not be stored inside the token.

---

# 19. Authorization Architecture

Authentication determines the identity of a user.

Authorization determines what the user is allowed to do.

Example:

```text
CUSTOMER
 ├── Browse
 ├── Search
 ├── Book
 └── View own bookings

ADMIN
 ├── Browse
 ├── Manage users
 ├── Manage events
 ├── Monitor bookings
 └── Manage rate-limit policies
```

---

# 20. Payment Service

The Payment Service handles payment-related operations.

Responsibilities:

* Payment initiation
* Payment processing state
* Transaction records
* Payment success/failure

Initially, payment can be simulated.

Later, an external payment provider can be integrated.

The Payment Service should remain independent of the Booking Service.

---

# 21. Synchronous Communication

REST APIs will be used when an immediate response is required.

Example:

```text
Frontend
   |
   ▼
Gateway
   |
   ▼
Catalog Service
   |
   ▼
Response
```

This is suitable for operations such as:

* Searching events
* Retrieving show information
* Checking catalog information

---

# 22. Asynchronous Communication

A message broker can be introduced for asynchronous workflows.

Potential technologies:

* Apache Kafka
* RabbitMQ

Conceptually:

```text
Booking Service
       |
       ▼
BookingCreated
       |
       ▼
Message Broker
       |
   ┌───┼──────────┐
   ▼   ▼          ▼
Payment Analytics Notification
Service   Service    Service
```

The final messaging technology can be selected during implementation.

---

# 23. Event-Driven Architecture

Potential domain events include:

```text
BookingCreated
BookingCancelled
PaymentCompleted
PaymentFailed
SeatHeld
SeatReleased
```

Events allow services to react to changes without requiring direct synchronous communication for every operation.

---

# 24. Monitoring Architecture

The monitoring stack is:

```text
Microservices
      |
      | Metrics
      ▼
 Prometheus
      |
      ▼
   Grafana
```

Metrics can include:

* Request count
* Request latency
* Error rate
* HTTP status codes
* Rate-limit rejections
* Booking attempts
* Successful bookings
* Failed bookings
* Service health

---

# 25. Logging Architecture

Each service should generate useful logs.

Conceptual log information:

```text
timestamp
service
request_id
endpoint
method
status
duration
error
```

The same request ID should be propagated where practical.

This makes distributed debugging easier.

---

# 26. Data Science Architecture

The ML component is intentionally separated from transactional services.

```text
Booking Service
       |
       ▼
Booking Data
       |
       ▼
Analytics / ML Service
       |
       ├── Demand Analysis
       ├── Traffic Analysis
       ├── User Behavior
       ├── Demand Forecasting
       └── Recommendations
```

Planned technology:

* Python
* Pandas
* NumPy
* Scikit-learn
* FastAPI

The ML service should not directly modify core booking records.

---

# 27. Frontend-to-Backend Request Flow

Example: user wants to book a seat.

```text
1. User selects a show
          |
          ▼
2. Frontend sends request
          |
          ▼
3. API Gateway
          |
          ├── JWT validation
          ├── Rate-limit check
          └── Request routing
                    |
                    ▼
4. Booking Service
          |
          ▼
5. Check seat state
          |
          ▼
6. Reserve/hold seat
          |
          ▼
7. Payment workflow
          |
          ▼
8. Confirm booking
          |
          ▼
9. Return response
```

---

# 28. Why the Gateway Does Not Handle Booking Logic

The Gateway is responsible for **traffic and access management**.

The Booking Service is responsible for **business logic**.

Therefore:

```text
Gateway
→ "Is this request allowed to enter?"

Booking Service
→ "Can this seat actually be booked?"
```

This separation is important for maintaining clear service boundaries.

---

# 29. Scalability

The architecture supports independent service scaling.

For example:

```text
                API Gateway
                     |
          ┌──────────┼──────────┐
          ▼          ▼          ▼
      Booking     Booking     Booking
      Instance    Instance    Instance
```

Multiple instances of a service can process requests when traffic increases.

The infrastructure components such as Redis and PostgreSQL must also be appropriately configured as the system scales.

---

# 30. Fault Isolation

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

The system can use mechanisms such as:

* Timeouts
* Retries
* Circuit breakers
* Idempotency
* Dead-letter queues

as the project evolves.

---

# 31. Security Architecture

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
```

Additional practices:

* Password hashing
* Secure secrets
* Environment variables
* Database access controls
* Safe error responses
* No sensitive information in logs

---

# 32. Deployment Architecture

Docker will be introduced after the core services are working.

A local Docker environment may contain:

```text
┌─────────────────────────────────────┐
│             Docker                  │
│                                     │
│ API Gateway                         │
│ User Service                        │
│ Catalog Service                     │
│ Booking Service                     │
│ Payment Service                     │
│ PostgreSQL                          │
│ Redis                               │
│ Kafka/RabbitMQ                      │
│ Prometheus                          │
│ Grafana                             │
│                                     │
└─────────────────────────────────────┘
```

Docker Compose can initially be used for local orchestration.

---

# 33. Repository Architecture

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

This is the target structure, not the structure that must exist immediately.

Services and directories will be created progressively.

---

# 34. Development Sequence

The architecture will be implemented progressively:

```text
Requirements
      ↓
Architecture
      ↓
Database Design
      ↓
API Contracts
      ↓
Frontend
      ↓
Authentication
      ↓
Microservices
      ↓
API Gateway
      ↓
Booking
      ↓
Redis
      ↓
Dynamic Rate Limiting
      ↓
Admin
      ↓
Monitoring
      ↓
ML
      ↓
Docker
      ↓
Deployment
```

Each phase should be functional before introducing the next major architectural component.

---

# 35. Architectural Boundaries

The following boundaries should remain clear:

| Component       | Primary Responsibility                      |
| --------------- | ------------------------------------------- |
| Frontend        | User interaction                            |
| API Gateway     | Routing, access control, traffic management |
| User Service    | User/account management                     |
| Catalog Service | Movies, events, shows, venues               |
| Booking Service | Seats and bookings                          |
| Payment Service | Payment workflow                            |
| Redis           | Fast temporary state                        |
| PostgreSQL      | Persistent business data                    |
| Message Broker  | Asynchronous events                         |
| Prometheus      | Metrics collection                          |
| Grafana         | Metrics visualization                       |
| ML Service      | Analytics and machine learning              |

---

# 36. Current Status

Current project phase:

**Phase 1 — Requirements & Architecture**

Completed:

* Project scope
* System requirements
* High-level architecture
* Service boundaries
* Technology direction
* Development sequence
* GitHub repository setup

Next phase:

**Phase 2 — Database Design + API Contracts**

Phase 2 will define:

1. Database entities
2. Tables
3. Primary keys
4. Foreign keys
5. Relationships
6. Service database ownership
7. REST endpoints
8. HTTP methods
9. Request bodies
10. Response bodies
11. Authentication requirements for each endpoint

## Core Data Entities

The application uses the following core business entities:

- User 
- Plan
- Movie
- Event
- Match
- Venue
- Seat
- Show
- Booking
- BookingSeat
- RateLimitPolicy

These entities represent the users, ticketable content,
venues, scheduled shows, bookings, and gateway traffic
policies required by the platform.


## High-Level Data Relationships

- A Plan can be associated with many Users.
- A User can create many Bookings.
- A Venue can contain many Seats.
- A Venue can host many Shows.
- A Booking can contain multiple Seats through BookingSeat.
- A Show represents a scheduled occurrence that can be booked.
- A Plan can have multiple RateLimitPolicies.

## Initial Entity Attributes

### User 
-id 
-name 
-email
-role
-plan
-created_at

### plans
- id
- name
- price
- description


### Rate_limit_policies
- id
- plan
- endpoint
- requests_per_second
- burst capacity
- enabled

### Movie

- id
- title
- description
- duration
- language
- genre
- release_date


### Event

- id
- name
- description
- event_type
- date
- venue


### Match

- id
- sport
- team_a
- team_b
- date
- venue


### Venue

- id
- name
- address
- city


### Show

- id
- movie
- event
- match
- venue
- start_time
- end_time