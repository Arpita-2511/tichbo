# Tichboo — System Architecture

## 1. Purpose

This document describes the technical architecture of Tichboo, a scalable ticket-booking platform supporting movies, events, shows, and sports matches.

The architecture is designed to demonstrate how a distributed ticket-booking system can handle authentication, traffic management, dynamic rate limiting, concurrent bookings, persistent data, asynchronous processing, monitoring, and analytics.

---

# 2. Architectural Goals

The architecture has the following goals:

1. Separate business responsibilities into independent services.
2. Provide a single entry point for client requests.
3. Protect backend services from excessive traffic.
4. Support dynamic rate-limit policies.
5. Prevent double booking during concurrent requests.
6. Maintain independent data ownership.
7. Allow services to scale independently.
8. Support asynchronous communication.
9. Provide system observability.
10. Allow future integration of data science and machine learning.

---

# 3. High-Level System

```text
                         CLIENT
                  Web / Mobile Application
                           |
                           | HTTPS
                           ▼
                ┌────────────────────────┐
                │      API GATEWAY       │
                │                        │
                │ • Routing              │
                │ • Authentication       │
                │ • Rate Limiting        │
                │ • Request Logging      │
                │ • Request ID           │
                └───────────┬────────────┘
                            |
            ┌───────────────┼────────────────┐
            │               │                │
            ▼               ▼                ▼
      User Service    Catalog Service   Booking Service
            │               │                │
            ▼               ▼                ▼
       User DB          Catalog DB        Booking DB
                                            │
                                            ▼
                                          Redis
```

Additional infrastructure:

```text
Booking / Payment / Other Services
              |
              ▼
        Message Broker
        Kafka/RabbitMQ
              |
      ┌───────┼────────┐
      ▼       ▼        ▼
   Payment  Analytics Notifications
    Service   / ML       Service
```

---

# 4. Client Layer

The client layer contains the web application.

Possible implementation:

* React
* Next.js
* Tailwind CSS
* TypeScript

The frontend is responsible for user interaction.

Examples:

* Registration
* Login
* Browsing events
* Searching shows
* Viewing seat availability
* Selecting seats
* Booking tickets
* Viewing bookings
* Managing profile

The frontend should communicate with the backend through the API Gateway rather than directly accessing individual microservices.

---

# 5. API Gateway Layer

The API Gateway is implemented using Spring Cloud Gateway.

It acts as the entry point for backend requests.

Instead of:

```text
Frontend → User Service
Frontend → Catalog Service
Frontend → Booking Service
```

the architecture uses:

```text
Frontend
    |
    ▼
API Gateway
    |
    ├── User Service
    ├── Catalog Service
    └── Booking Service
```

## Gateway Responsibilities

### 5.1 Routing

The Gateway determines which service should receive a request.

Example:

```text
/api/users/**       → User Service
/api/events/**      → Catalog Service
/api/shows/**       → Catalog Service
/api/bookings/**    → Booking Service
/api/payments/**    → Payment Service
```

### 5.2 Authentication

The Gateway can validate JWT tokens before forwarding protected requests.

Example:

```text
Request
   |
   ▼
JWT present?
   |
   ├── No → Reject
   |
   └── Yes
         |
         ▼
      Validate
         |
         ▼
      Forward
```

The downstream service can still enforce authorization for its own business operations.

### 5.3 Rate Limiting

The Gateway checks whether a request is allowed before forwarding it.

### 5.4 Request Identification

Each incoming request can receive a unique request ID.

This helps trace a request across multiple services.

### 5.5 Logging

The Gateway can record:

* Request path
* HTTP method
* User/request identity
* Response status
* Processing time
* Rate-limit result

---

# 6. User Service

The User Service owns user-related functionality.

## Responsibilities

* User registration
* User information
* Login-related operations
* User profiles
* Subscription plans
* User roles
* Account management

## Example data

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

The service owns its own database.

Other services should not directly access the User Service database.

---

# 7. Catalog Service

The Catalog Service manages ticket-bookable content.

The platform supports multiple categories:

```text
Movie
Event
Sports Match
Show
```

## Responsibilities

* Event creation
* Event information
* Venue information
* Show schedules
* Movie information
* Match information
* Seat-layout information

Example relationship:

```text
Event
  |
  └── Venue
        |
        └── Show
              |
              └── Seats
```

The exact database model will be finalized during the database-design phase.

---

# 8. Booking Service

The Booking Service is responsible for ticket reservations.

This is a critical service because booking involves concurrent users accessing the same resources.

## Responsibilities

* Check availability
* Hold seats
* Release seats
* Create bookings
* Cancel bookings
* Maintain booking history
* Prevent double booking

---

# 9. Concurrent Booking Problem

Consider a show with:

```text
Seat A1
```

Suppose:

```text
User A ──┐
         │
User B ──┼──> Seat A1
         │
User C ──┘
```

All three users request the same seat.

The system must ensure:

```text
Only one successful allocation
```

The other requests must receive an appropriate response.

---

# 10. Seat State

A seat can have states such as:

```text
AVAILABLE
    ↓
HELD
    ↓
BOOKED
```

If a user selects a seat:

```text
AVAILABLE → HELD
```

The seat is temporarily reserved.

If payment/booking succeeds:

```text
HELD → BOOKED
```

If the hold expires:

```text
HELD → AVAILABLE
```

Redis can be used for temporary holds, while PostgreSQL remains the durable source of booking records.

---

# 11. Concurrency Control

The Booking Service must perform seat allocation atomically.

The general process is:

```text
Booking Request
      |
      ▼
Check seat
      |
      ▼
Attempt atomic reservation
      |
      ├── Successful
      │      |
      │      ▼
      │   Continue booking
      │
      └── Already reserved
             |
             ▼
        Reject request
```

Database transactions and appropriate locking/atomic update mechanisms will be used to prevent inconsistent seat states.

The exact implementation will be finalized during the booking-service development phase.

---

# 12. Redis

Redis provides fast in-memory storage.

Tichboo uses Redis primarily for:

### Rate limiting

```text
user + endpoint
       |
       ▼
Redis counter
```

### Temporary seat holds

```text
seat
 |
 └── temporary reservation
```

### Potential caching

Frequently accessed information may later be cached.

Redis should not replace PostgreSQL as the primary persistent database for business records.

---

# 13. Dynamic Rate Limiting Architecture

The rate limiter is designed to be configurable.

Instead of hard-coding:

```text
100 requests/minute
```

the system can store policies such as:

```text
Plan       Endpoint       Limit
--------------------------------
FREE       Search         X
PREMIUM    Search         Y
VIP        Search         Z
```

The actual numeric values will be configured during implementation.

---

# 14. Rate-Limit Request Flow

```text
                  Request
                     |
                     ▼
               API Gateway
                     |
                     ▼
             Identify User
                     |
                     ▼
              Identify Plan
                     |
                     ▼
             Identify Endpoint
                     |
                     ▼
          Load applicable policy
                     |
                     ▼
                  Redis
                     |
            ┌────────┴────────┐
            │                 │
         Allowed            Exceeded
            │                 │
            ▼                 ▼
      Backend Service       HTTP 429
```

HTTP `429 Too Many Requests` is returned when the configured request allowance has been exceeded.

---

# 15. Why Dynamic Rate Limiting?

The platform may have different traffic requirements for different operations.

For example:

```text
Search API
    ↓
High request volume expected

Booking API
    ↓
Sensitive operation

Admin API
    ↓
Restricted operation
```

Therefore, a single global limit is not appropriate for every endpoint.

Dynamic policies allow the administrator to change policies according to system requirements.

---

# 16. Plan-Based Rate Limiting

A user's subscription plan can be one input into rate-limit policy selection.

Example:

```text
                 User
                   |
                   ▼
               Plan = ?
                   |
       ┌───────────┼───────────┐
       ▼           ▼           ▼
     FREE       PREMIUM        VIP
       |           |           |
       ▼           ▼           ▼
    Policy A    Policy B     Policy C
```

This does not mean the plan directly controls booking authorization.

Rate limiting and business authorization are separate concerns.

---

# 17. Admin-Controlled Policies

The administrator can modify rate-limit configurations.

Example:

```text
Admin Dashboard
       |
       ▼
Policy Management API
       |
       ▼
Policy Storage
       |
       ▼
Gateway
       |
       ▼
New policy applied
```

This allows policies to be changed without modifying and rebuilding the Gateway application.

---

# 18. Payment Service

The Payment Service handles payment-related operations.

Responsibilities:

* Payment initiation
* Payment status
* Transaction records
* Payment confirmation
* Payment failure handling

During initial development, payment can be simulated.

Later, a real payment provider can be integrated.

---

# 19. Service Communication

Two communication patterns can be used.

## Synchronous communication

REST APIs can be used when an immediate response is required.

Example:

```text
Frontend
   ↓
Gateway
   ↓
Catalog Service
   ↓
Response
```

## Asynchronous communication

Kafka or RabbitMQ can be introduced for events that do not require an immediate response.

Example:

```text
Booking Service
      |
      ▼
BookingCreated
      |
      ▼
Message Broker
      |
 ┌────┼────────────┐
 ▼    ▼            ▼
Payment Analytics Notification
```

---

# 20. Message/Event Model

Potential events:

```text
BookingCreated
BookingCancelled
PaymentCompleted
PaymentFailed
SeatHeld
SeatReleased
```

Services can subscribe only to events relevant to them.

This reduces direct coupling.

---

# 21. Database Architecture

The project follows the database-per-service concept.

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

A service should not directly query another service's database.

Communication between services should occur through APIs or events.

---

# 22. Authentication Architecture

Authentication uses JWT.

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
JWT generated
 |
 ▼
Client
 |
 | JWT
 ▼
API Gateway
 |
 | Validate JWT
 ▼
Protected Service
```

The token can contain claims such as:

```text
userId
role
plan
```

Only non-sensitive authorization information should be included in JWT claims.

---

# 23. Authorization

Authentication answers:

> Who is this user?

Authorization answers:

> What is this user allowed to do?

Example:

```text
USER
 └── Browse / Book

ADMIN
 ├── Browse
 ├── Book
 ├── Manage events
 └── Manage rate-limit policies
```

Authorization rules will be implemented using Spring Security.

---

# 24. Monitoring Architecture

Prometheus and Grafana will provide observability.

```text
Services
   |
   | Metrics
   ▼
Prometheus
   |
   ▼
Grafana
```

Potential metrics:

```text
HTTP requests
Request latency
HTTP errors
Rate-limit rejections
Booking attempts
Successful bookings
Failed bookings
Service health
```

---

# 25. Logging

Each service should produce structured logs.

Important information can include:

```text
timestamp
service
request_id
endpoint
status
duration
error
```

Request IDs allow a request to be traced across services.

Example:

```text
Client
  |
  | request-id: ABC123
  ▼
Gateway
  |
  | ABC123
  ▼
Booking Service
  |
  | ABC123
  ▼
Payment Service
```

---

# 26. Data Science / ML Architecture

The ML layer is intentionally separated from the core transaction system.

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
       └── Demand Forecasting
```

The ML service can be implemented using:

* Python
* Pandas
* NumPy
* Scikit-learn
* FastAPI

The ML service can expose predictions or analytics through REST APIs.

---

# 27. Why Python for ML?

The core backend uses Java/Spring Boot because the project is primarily a backend distributed-system project.

Python is used separately for the data-science layer because the Python ecosystem provides libraries specifically designed for:

* Data processing
* Statistical analysis
* Machine learning
* Model development

This keeps the transactional Java services separate from ML experimentation and model serving.

---

# 28. Deployment Architecture

Docker will be introduced after the application is functional.

Conceptually:

```text
Docker Environment

┌───────────────────────────────────────┐
│                                       │
│ API Gateway                           │
│ User Service                          │
│ Catalog Service                       │
│ Booking Service                       │
│ Payment Service                       │
│ Redis                                 │
│ PostgreSQL                            │
│ Kafka/RabbitMQ                        │
│ Prometheus                            │
│ Grafana                               │
│                                       │
└───────────────────────────────────────┘
```

Docker Compose can initially be used to run the local distributed environment.

---

# 29. Scalability

Microservices allow individual services to scale independently.

For example, during a popular ticket release:

```text
              API Gateway
                  |
        ┌─────────┼─────────┐
        ▼         ▼         ▼
    Booking   Booking   Booking
    Instance  Instance  Instance
```

The Booking Service can have multiple instances without requiring the entire application to be replicated as one large monolith.

Redis and PostgreSQL must also be designed appropriately as the system scales.

---

# 30. Fault Isolation

A major advantage of the architecture is service isolation.

For example:

```text
Catalog Service
      ↓
Available

Booking Service
      ↓
Available

Payment Service
      ↓
Temporarily unavailable
```

The system can handle service failures according to the business workflow rather than causing every component to fail together.

Failure-handling strategies can later include:

* Timeouts
* Retries
* Circuit breakers
* Idempotency
* Dead-letter queues

---

# 31. Security Considerations

The system should follow basic security practices:

* Password hashing
* HTTPS in deployment
* JWT validation
* Role-based authorization
* Input validation
* API rate limiting
* Secure environment variables
* No secrets committed to Git
* Database access restrictions
* Proper error handling

Sensitive configuration should be stored outside source code.

For example:

```text
.env
application-local.yml
environment variables
```

These should not be committed to GitHub when they contain secrets.

---

# 32. Repository Architecture

The repository is organized into documentation, frontend, services, ML, and infrastructure.

```text
tichboo/
│
├── README.md
│
├── docs/
│   ├── requirements.md
│   ├── architecture.md
│   ├── api-contracts.md
│   └── database-design.md
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

This structure is a target architecture. The folders will be created progressively as development moves through each phase.

---

# 33. Request Lifecycle Example

Consider a user booking seat A10 for a movie.

```text
1. User logs into Tichboo
          |
          ▼
2. User receives JWT
          |
          ▼
3. User requests movie/show information
          |
          ▼
4. API Gateway
          |
          ▼
5. Catalog Service
          |
          ▼
6. User selects Seat A10
          |
          ▼
7. Booking request
          |
          ▼
8. API Gateway
          |
          ├── Authenticate
          ├── Apply rate-limit policy
          └── Route request
                    |
                    ▼
             Booking Service
                    |
                    ▼
              Check Seat A10
                    |
                    ▼
             Reserve/Hold Seat
                    |
                    ▼
              Payment Process
                    |
                    ▼
             Booking Confirmed
                    |
                    ▼
              Seat = BOOKED
```

The important architectural boundary is:

```text
Gateway controls access to the system.

Booking Service controls seat allocation.
```

The Gateway should not decide whether a seat is available.

---

# 34. Development Strategy

The project will not be built as all microservices simultaneously.

Development will proceed incrementally.

```text
Requirements
     ↓
Architecture
     ↓
Database Design
     ↓
API Contracts
     ↓
Basic Backend
     ↓
Authentication
     ↓
Frontend Integration
     ↓
Microservices
     ↓
API Gateway
     ↓
Redis Rate Limiting
     ↓
Dynamic Policies
     ↓
Concurrency
     ↓
Admin
     ↓
Monitoring
     ↓
ML
     ↓
Docker / Deployment
```

This approach makes debugging easier and allows each architectural concept to be understood before adding the next layer.

---

# 35. Architectural Principles

The following principles guide the implementation:

### Separation of concerns

Each service should have a clearly defined responsibility.

### Loose coupling

Services should avoid unnecessary direct dependencies.

### High cohesion

Related functionality should remain within the same service.

### Database ownership

Services own their data.

### Stateless API Gateway

The Gateway should avoid storing user session state.

### Centralized traffic management

Rate limiting is handled at the Gateway.

### Business ownership

Business operations remain inside their respective services.

### Concurrency safety

Booking operations must be atomic and consistent.

### Observability

Services should expose logs, metrics, and health information.

### Independent scalability

Services should be capable of scaling independently.

---

# 36. Current Implementation Status

Current phase:

```text
PHASE 1
Requirements & Architecture
```

Completed:

* GitHub repository
* Local Git repository
* README
* Initial architecture documentation

Next phase:

```text
PHASE 2
Database Design + API Contracts
```

The next phase will define the actual database entities, relationships, service boundaries, REST endpoints, request bodies, response bodies, and API responsibilities before implementation begins.
