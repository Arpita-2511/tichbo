# Tichboo — System Requirements

## 1. Document Purpose

This document defines the functional and non-functional requirements for Tichboo, a scalable ticket-booking platform.

Tichboo is designed to support multiple types of bookable experiences, including:

* Movies
* Events
* Sports matches
* Shows

The primary technical objective is to build a distributed ticket-booking system capable of handling multiple concurrent users while demonstrating:

* Microservices architecture
* API Gateway
* Authentication and authorization
* Dynamic API rate limiting
* Redis
* Concurrent seat booking
* Database transactions
* Asynchronous communication
* Monitoring
* Data analytics and machine learning

This document defines **what the system must do**. The architecture document defines **how the system will do it**.

---

# 2. Problem Statement

A ticket-booking platform can experience a large number of simultaneous users when a popular movie, event, or match becomes available.

For example, assume a show contains 100 seats.

If thousands of users simultaneously attempt to:

* View the show
* Check seat availability
* Select seats
* Hold seats
* Book tickets

the system must continue operating correctly while preventing:

* Duplicate bookings
* Invalid seat states
* Excessive API traffic
* Unauthorized access
* Inconsistent booking information

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
8. Use Redis for rate-limit state and temporary data.
9. Prevent double booking of seats.
10. Maintain persistent booking information.
11. Provide administrative functionality.
12. Provide system monitoring and logging.
13. Support future horizontal scaling.
14. Provide a foundation for analytics and machine learning.

---

# 4. Users and Roles

The system initially supports two primary roles.

## 4.1 Customer

A customer can:

* Register
* Log in
* Manage their profile
* Browse movies/events/matches
* Search for shows
* View event details
* View seat availability
* Select seats
* Hold seats temporarily
* Book tickets
* View booking history
* Cancel eligible bookings
* View subscription information

## 4.2 Administrator

An administrator can:

* Manage users
* Manage movies/events/matches
* Manage shows
* Manage venues
* Monitor bookings
* Configure rate-limit policies
* Monitor system traffic
* View system statistics

Additional roles may be introduced later if required.

---

# 5. Functional Requirements

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

After successful authentication, the system shall issue a JWT containing the required authorization information.

Protected APIs shall require a valid authentication token.

---

## FR-03: Authorization

The system shall distinguish between users based on their roles and permissions.

For example:

```text
CUSTOMER
    → Browse
    → Book
    → View own bookings

ADMIN
    → Manage events
    → Manage users
    → Manage rate-limit policies
```

Authentication and authorization shall be treated as separate concerns.

---

# 6. Subscription Plans

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
FREE     → Policy A
PREMIUM  → Policy B
VIP      → Policy C
```

The exact numerical limits will be defined during implementation.

Subscription plans shall not automatically determine whether a user is authorized to perform a business operation.

---

# 7. Catalog Requirements

## FR-04: Content Management

The system shall support bookable content such as:

* Movies
* Events
* Sports matches
* Shows

Each item may contain information such as:

* Name/title
* Description
* Category
* Date
* Venue
* Duration
* Status

---

## FR-05: Show Management

A show represents a scheduled occurrence of an event/content item.

A show shall be associated with:

* Event/content
* Venue
* Date
* Start time
* End time
* Seat configuration

---

## FR-06: Venue Management

The system shall maintain venue information.

A venue may contain:

* Name
* Location
* Capacity
* Seat layout

---

# 8. Search and Discovery

## FR-07: Browse Content

Users shall be able to browse available content.

## FR-08: Search

Users shall be able to search for relevant movies, events, matches, or shows.

## FR-09: Filtering

The system should support filters such as:

* Category
* Date
* Location
* Venue
* Availability

Additional filters may be added later.

---

# 9. Seat Management

## FR-10: Seat Availability

The system shall display the current availability of seats for a selected show.

Seats should have clearly defined states.

Initial states:

```text
AVAILABLE
HELD
BOOKED
```

---

## FR-11: Temporary Seat Hold

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

---

# 10. Booking Requirements

## FR-12: Create Booking

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

## FR-13: Prevent Double Booking

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

## FR-14: Booking Cancellation

Users shall be able to cancel bookings when cancellation is permitted according to the configured business rules.

When a booking is cancelled, the associated seats may become available again.

---

## FR-15: Booking History

Users shall be able to view their previous bookings.

---

# 11. Payment Requirements

## FR-16: Payment Processing

The system shall support a payment-processing workflow.

During initial development, payment may be simulated rather than integrated with a real payment provider.

Possible states include:

```text
PENDING
SUCCESS
FAILED
```

Payment functionality may later be replaced by a real payment gateway.

---

# 12. API Gateway Requirements

## FR-17: Centralized Entry Point

Client applications shall communicate with backend services through the API Gateway.

The frontend should not directly communicate with individual internal microservices.

---

## FR-18: Request Routing

The Gateway shall route requests to appropriate services.

Example:

```text
/api/users/**       → User Service
/api/events/**      → Catalog Service
/api/shows/**       → Catalog Service
/api/bookings/**    → Booking Service
/api/payments/**    → Payment Service
```

The exact API paths will be defined in the API-contract phase.

---

## FR-19: Request Authentication

The Gateway shall validate authentication information for protected routes.

---

## FR-20: Request Logging

The Gateway shall record appropriate request information for monitoring and debugging.

---

# 13. Rate-Limiting Requirements

## FR-21: API Rate Limiting

The system shall limit the number of requests that a user/client can make to specified APIs during a configured period.

---

## FR-22: Dynamic Rate Limiting

Rate limits shall not be permanently hard-coded into the Gateway.

The system shall support configurable policies.

Policies may depend on:

* User plan
* Endpoint
* Request type
* Administrative configuration

---

## FR-23: Redis-Based Rate Limiting

Redis shall be used for storing fast-changing rate-limit state such as request counters.

Conceptually:

```text
User + Endpoint
       ↓
    Redis
       ↓
Request count
       ↓
Allow / Reject
```

---

## FR-24: Rate-Limit Response

When a client exceeds its configured request allowance, the Gateway shall reject the request with:

```text
HTTP 429 — Too Many Requests
```

---

## FR-25: Administrative Rate-Limit Configuration

Administrators shall be able to modify applicable rate-limit policies without modifying the application source code.

---

# 14. Admin Requirements

The administrator shall have access to an administrative interface.

The initial dashboard should support:

* User management
* Content management
* Show management
* Booking monitoring
* Rate-limit configuration
* Traffic monitoring

Additional administrative functionality may be introduced later.

---

# 15. Monitoring Requirements

## FR-26: Application Metrics

The system shall expose metrics related to application behavior.

Potential metrics include:

* Total requests
* Request latency
* HTTP errors
* Rate-limit rejections
* Booking attempts
* Successful bookings
* Failed bookings
* Service health

---

## FR-27: Monitoring Dashboard

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

# 16. Logging Requirements

Services shall generate logs useful for debugging and operational monitoring.

Logs should contain relevant information such as:

* Timestamp
* Service name
* Request ID
* Endpoint
* HTTP status
* Processing duration
* Error information

Sensitive information such as passwords and secret keys must not be logged.

---

# 17. Asynchronous Communication Requirements

The system should support asynchronous communication for suitable workflows.

A message broker such as Kafka or RabbitMQ may be introduced.

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

---

# 18. Data Science Requirements

A separate data-science/ML component may be introduced after the core platform is functional.

The analytics layer may analyze:

* Booking trends
* Event popularity
* Traffic patterns
* Peak booking periods
* User behavior
* Demand patterns

Potential ML functionality:

* Demand forecasting
* Event demand prediction
* Recommendation systems
* Anomaly detection

The ML service will be separated from the transactional backend.

---

# 19. Non-Functional Requirements

## NFR-01: Scalability

The system should allow individual services to be scaled independently.

---

## NFR-02: Availability

Failure of one service should not unnecessarily bring down unrelated services.

---

## NFR-03: Performance

Frequently accessed operations such as rate-limit checks should have low latency.

Redis will be used for operations requiring fast in-memory access.

---

## NFR-04: Consistency

Booking operations must maintain consistent seat and booking states.

---

## NFR-05: Concurrency

The system must safely handle simultaneous booking attempts for the same seat.

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

---

## NFR-07: Maintainability

Services should have clearly defined responsibilities and interfaces.

---

## NFR-08: Observability

The system should provide:

* Logs
* Metrics
* Health checks
* Request tracing information

---

## NFR-09: Extensibility

The architecture should allow additional services and features to be introduced without redesigning the entire system.

---

# 20. Business Rules

Initial business rules include:

### BR-01

Only authenticated users can create bookings.

### BR-02

A seat can only be successfully booked once for a particular show.

### BR-03

Temporary seat holds expire after a configured period.

### BR-04

Expired holds return seats to the available state.

### BR-05

Rate limits are determined by configurable policies.

### BR-06

Administrators can modify rate-limit policies.

### BR-07

A user can access only their own booking information unless authorized as an administrator.

### BR-08

Payment must reach the required successful state before a booking is finalized, according to the implemented booking workflow.

---

# 21. Technology Requirements

The planned technology stack is:

| Component        | Technology            |
| ---------------- | --------------------- |
| Frontend         | React / Next.js       |
| Backend          | Java + Spring Boot    |
| API Gateway      | Spring Cloud Gateway  |
| Authentication   | Spring Security + JWT |
| Database         | PostgreSQL            |
| Fast state/cache | Redis                 |
| Messaging        | Kafka / RabbitMQ      |
| Monitoring       | Prometheus + Grafana  |
| ML               | Python + Scikit-learn |
| ML API           | FastAPI               |
| Containerization | Docker                |
| Version Control  | Git + GitHub          |
| API Testing      | Postman               |

The exact technology choice for messaging and frontend can be finalized before implementation.

---

# 22. Project Constraints

The project will initially be developed as a learning and portfolio project.

Therefore:

* Payment can initially be simulated.
* Deployment can initially be local.
* The number of services can be introduced progressively.
* Production-grade infrastructure can be added after the core functionality works.
* ML functionality will be introduced after transactional functionality is stable.

---

# 23. Development Phases

The project will follow this sequence:

```text
1. Requirements & Architecture
          ↓
2. Database Design + API Contracts
          ↓
3. Basic Frontend UI
          ↓
4. Authentication Backend
          ↓
5. Frontend ↔ Authentication
          ↓
6. API Gateway
          ↓
7. Catalog/Event/Show APIs
          ↓
8. Frontend ↔ APIs
          ↓
9. Booking + Seat Concurrency
          ↓
10. Redis Rate Limiting
          ↓
11. Dynamic Rate Limiting
          ↓
12. Admin Dashboard
          ↓
13. Monitoring
          ↓
14. Data Science / ML
          ↓
15. Docker + Deployment
```

---

# 24. Requirement Status

Current phase:

**Phase 1 — Requirements & Architecture**

Completed:

* Project scope
* User roles
* Functional requirements
* Non-functional requirements
* Business rules
* Technology requirements
* Development roadmap

Next phase:

**Phase 2 — Database Design + API Contracts**
## Core Data Entities

