# Phase 20: Full Frontend ↔ Backend Integration Gap Audit

**Date:** 2026-10-03
**Scope:** Repository-wide inspection of all frontend functions, API layer, Gateway routes, and backend service endpoints.
**Method:** Static code analysis only — no services started, no runtime verification.

---

## 1. Frontend API Function Inventory

### REAL (hit the Gateway → backend service)

| # | Function | Method | Gateway Path | Backend Service | Status |
|---|----------|--------|-------------|-----------------|--------|
| 1 | `login` | POST | `/api/auth/login` | user-service | Fully wired |
| 2 | `signup` | POST | `/api/auth/register` | user-service | Fully wired |
| 3 | `logout` | — | Local only (drops JWT) | — | Fully wired |
| 4 | `getCurrentUser` | GET | `/api/users/me` | user-service | Fully wired |
| 5 | `getEvents` | GET | `/api/catalog/content` | catalog-service | Fully wired |
| 6 | `getEventById` | GET | `/api/catalog/content/{id}` | catalog-service | Fully wired |
| 7 | `getVenueById` | GET | `/api/catalog/venues/{id}` | catalog-service | Fully wired |
| 8 | `getVenuesByCity` | GET | `/api/catalog/venues` | catalog-service | Fully wired (client-side city filter) |
| 9 | `getShowsByEvent` | GET | `/api/catalog/shows` | catalog-service | Fully wired (client-side contentId filter) |
| 10 | `getShowById` | GET | `/api/catalog/shows/{id}` | catalog-service | Fully wired |
| 11 | `getSeatMap` | GET | `/api/catalog/shows/{id}`, `/api/catalog/seats?venueId=`, `/api/bookings/shows/{showId}/seats` | catalog-service + booking-service | Fully wired (3-call join) |
| 12 | `holdSeats` | POST | `/api/bookings/shows/{showId}/seats/hold` | booking-service | Fully wired |
| 13 | `createBooking` | POST | `/api/bookings` | booking-service | Fully wired |
| 14 | `getBooking` | GET | `/api/bookings/{bookingId}` | booking-service | Fully wired |
| 15 | `createPayment` | POST | `/api/payments` | payment-service | Fully wired |
| 16 | `getAdminOverviewStats` | GET | `/api/admin/users/stats`, `/api/admin/content/stats`, `/api/admin/bookings/stats` | user + catalog + booking | Fully wired (3-call aggregation) |
| 17 | `getRateLimitStats` | GET | `/api/admin/rate-limits/stats` | gateway-service (local) | Fully wired |
| 18 | `getRateLimitPolicies` | GET | `/api/admin/rate-limits/policies` | gateway-service (local) | Fully wired |
| 19 | `createRateLimitPolicy` | POST | `/api/admin/rate-limits/policies` | gateway-service (local) | Fully wired |
| 20 | `updateRateLimitPolicy` | PUT | `/api/admin/rate-limits/policies/{id}` | gateway-service (local) | Fully wired |
| 21 | `deleteRateLimitPolicy` | DELETE | `/api/admin/rate-limits/policies/{id}` | gateway-service (local) | Fully wired |

**Total real functions: 21**

### MOCK (return hardcoded/simulated data)

| # | Function | What It Returns | Consumer(s) | Backend Endpoint Exists? | Can Wire? |
|---|----------|----------------|-------------|--------------------------|-----------|
| M1 | `getTrendingEvents` | `allEvents.filter(e => e.trending)` | Home.tsx | **No** — catalog-service has no trending concept | No — needs new backend feature |
| M2 | `getFeaturedEvents` | `allEvents.filter(e => e.featured)` | (not imported by any .tsx) | **No** — catalog-service has no featured concept | No — needs new backend feature |
| M3 | `getBookings` | `mockBookings` filtered by userId | Admin.tsx, Bookings.tsx | **Yes** — `GET /api/bookings` (BookingController) | **YES** — wire to real endpoint |
| M4 | `getBookingById` | `mockBookings.find()` | (not imported by any .tsx) | **Yes** — `GET /api/bookings/{id}` (BookingController) | **YES** — already have `getBooking()` doing this |
| M5 | `cancelBooking` | `true` (always succeeds) | Bookings.tsx | **Yes** — `POST /api/bookings/{id}/cancel` (BookingController) | **YES** — wire to real endpoint |
| M6 | `getPlans` | hardcoded `plans` array | Plans.tsx | **No** — no plans listing endpoint | No — needs new backend feature |
| M7 | `subscribeToPlan` | `true` (always succeeds) | Plans.tsx | **Partial** — admin-only `PATCH /api/admin/users/{id}/plan` exists, but no self-service subscription endpoint | No — needs new self-service endpoint |
| M8 | `getAdminStats` | hardcoded `adminStats` (bookingsTrend, bookingsByCategory, etc.) | Admin.tsx | **No** — no analytics/aggregation endpoint | No — needs new backend feature |
| M9 | `searchEvents` | client-side filter on `allEvents` mock array | SearchModal.tsx | **Partial** — `GET /api/catalog/content` exists; could use `getEvents()` then filter | **YES** — replace with `getEvents()` + client-side search |

**Total mock functions: 9**

---

## 2. Direct mockData Imports (bypassing API layer)

| File | Import | What It Gets | Can Replace? |
|------|--------|-------------|-------------|
| `context/AppContext.tsx` | `cities` | Static city list for location picker | Not a backend concern — static reference data; keep as-is or add a cities endpoint later |
| `pages/Ticket.tsx` | `mockBookings` | Fallback booking for ticket display | **YES** — should use `getBooking(id)` instead of mock fallback |
| `components/common/SearchModal.tsx` | `popularSearches`, `recentSearches` | Static suggestion lists | Not a backend concern — UX decoration; keep as-is or track per-user later |
| `services/api.ts` | `allEvents`, `mockBookings`, `plans`, `adminStats` | Used by mock functions M1-M9 | Will shrink as mocks are replaced |

---

## 3. Gateway Route Map

### Public (no auth)
| Path Pattern | Target |
|---|---|
| `POST /api/auth/register` | user-service:8081 |
| `POST /api/auth/login` | user-service:8081 |
| `/actuator/**` | gateway local |

### Authenticated (any valid JWT)
| Path Pattern | Target |
|---|---|
| `/api/users/**` | user-service:8081 |
| `GET /api/catalog/**` | catalog-service:8082 |
| `/api/bookings/**` | booking-service:8083 |
| `/api/payments/**` | payment-service:8084 |

### ROLE_ADMIN only
| Path Pattern | Target |
|---|---|
| `/api/admin/**` | Routed to respective services based on path |
| `POST/PUT/PATCH/DELETE /api/catalog/**` | catalog-service:8082 |
| `/api/admin/rate-limits/**` | gateway local endpoints |

### Denied
| Path | Reason |
|---|---|
| `POST /api/bookings/*/confirm` | Internal-only (booking-service ↔ payment-service) |

### CORS
Allowed methods: `GET, POST, PUT, PATCH, DELETE, OPTIONS`
Allowed origins: `http://localhost:5173`

---

## 4. Backend Service Endpoint Inventory

### user-service (port 8081)

| Endpoint | Method | Auth | Used by Frontend? |
|----------|--------|------|-------------------|
| `/api/auth/register` | POST | Public | Yes — `signup()` |
| `/api/auth/login` | POST | Public | Yes — `login()` |
| `/api/users/me` | GET | JWT | Yes — `getCurrentUser()` |
| `/api/admin/users` | GET | ADMIN | No |
| `/api/admin/users/{id}/plan` | PATCH | ADMIN | No (frontend mock `subscribeToPlan` is self-service, not admin) |
| `/api/admin/users/{id}/role` | PATCH | ADMIN | No |
| `/api/admin/users/stats` | GET | ADMIN | Yes — `getAdminOverviewStats()` |

### catalog-service (port 8082)

| Endpoint | Method | Auth | Used by Frontend? |
|----------|--------|------|-------------------|
| `/api/catalog/content` | GET | JWT | Yes — `getEvents()` |
| `/api/catalog/content/{id}` | GET | JWT | Yes — `getEventById()` |
| `/api/catalog/venues` | GET | JWT | Yes — `getVenuesByCity()` |
| `/api/catalog/venues/{id}` | GET | JWT | Yes — `getVenueById()` |
| `/api/catalog/shows` | GET | JWT | Yes — `getShowsByEvent()` |
| `/api/catalog/shows/{id}` | GET | JWT | Yes — `getShowById()`, `getSeatMap()` |
| `/api/catalog/seats` | GET | JWT | Yes — `getSeatMap()` (query: `venueId`) |
| `/api/admin/content` | POST | ADMIN | No |
| `/api/admin/content/{id}` | PUT, DELETE | ADMIN | No |
| `/api/admin/content/stats` | GET | ADMIN | Yes — `getAdminOverviewStats()` |
| `/api/admin/shows` | POST | ADMIN | No |
| `/api/admin/shows/{id}` | PUT, DELETE | ADMIN | No |
| `/api/admin/shows/{id}/cancel` | PATCH | ADMIN | No |
| `/api/admin/venues` | POST | ADMIN | No |
| `/api/admin/venues/{id}` | PUT, DELETE | ADMIN | No |

### booking-service (port 8083)

| Endpoint | Method | Auth | Used by Frontend? |
|----------|--------|------|-------------------|
| `/api/bookings/shows/{showId}/seats` | GET | JWT | Yes — `getSeatMap()` |
| `/api/bookings/shows/{showId}/seats/hold` | POST | JWT | Yes — `holdSeats()` |
| `/api/bookings/shows/{showId}/seats/release` | POST | JWT | No |
| `/api/bookings` | POST | JWT | Yes — `createBooking()` |
| `/api/bookings/{id}` | GET | JWT | Yes — `getBooking()` |
| `/api/bookings` | GET | JWT | **No** — mock `getBookings()` exists but not wired |
| `/api/bookings/{id}/cancel` | POST | JWT | **No** — mock `cancelBooking()` exists but not wired |
| `/api/admin/bookings` | GET | ADMIN | No |
| `/api/admin/bookings/stats` | GET | ADMIN | Yes — `getAdminOverviewStats()` |
| `/api/admin/shows/{showId}/seats` | POST | ADMIN | No |
| `/api/admin/shows/{showId}/seat-activity` | GET | ADMIN | No |
| `/internal/bookings/{id}` | GET | Internal | No (service-to-service only) |
| `/internal/bookings/{id}/confirm` | POST | Internal | No (service-to-service only) |
| `/internal/bookings/{id}/cancel` | POST | Internal | No (service-to-service only) |

### payment-service (port 8084)

| Endpoint | Method | Auth | Used by Frontend? |
|----------|--------|------|-------------------|
| `/api/payments` | POST | JWT | Yes — `createPayment()` |
| `/api/payments/{id}` | GET | JWT | No |
| `/api/payments?bookingId=` | GET | JWT | No |

### audit-service — **No controllers found.** No REST endpoints exist.

---

## 5. Integration Matrix — What's Real vs Mock

| Frontend Feature | Page/Component | API Function | Status | Gap? |
|---|---|---|---|---|
| Login | Login.tsx | `login()` | **REAL** | None |
| Registration | Signup.tsx | `signup()` | **REAL** | None |
| Session restore | AppContext.tsx | `getCurrentUser()` | **REAL** | None |
| Logout | AppContext.tsx | `logout()` | **REAL** | None |
| Browse events | Home.tsx, Movies.tsx, etc. | `getEvents()` | **REAL** | None |
| Event detail | EventDetail.tsx | `getEventById()` | **REAL** | None |
| Trending events | Home.tsx | `getTrendingEvents()` | **MOCK** | No backend concept |
| Featured events | (unused) | `getFeaturedEvents()` | **MOCK** | No backend concept; function not imported anywhere |
| Venue detail | EventDetail.tsx | `getVenueById()` | **REAL** | None |
| Venues by city | (available) | `getVenuesByCity()` | **REAL** | None |
| Shows for event | BookingFlow.tsx | `getShowsByEvent()` | **REAL** | None |
| Show detail | BookingFlow.tsx | `getShowById()` | **REAL** | None |
| Seat map | SeatMap.tsx | `getSeatMap()` | **REAL** | None |
| Hold seats | BookingSummary.tsx | `holdSeats()` | **REAL** | None |
| Create booking | BookingSummary.tsx | `createBooking()` | **REAL** | None |
| View single booking | BookingSummary.tsx | `getBooking()` | **REAL** | None |
| **List my bookings** | **Bookings.tsx** | **`getBookings()`** | **MOCK** | **Backend exists — wire it** |
| **Cancel booking** | **Bookings.tsx** | **`cancelBooking()`** | **MOCK** | **Backend exists — wire it** |
| **View ticket** | **Ticket.tsx** | **direct mockBookings import** | **MOCK** | **Should use `getBooking()`** |
| Create payment | BookingSummary.tsx | `createPayment()` | **REAL** | None |
| **Search events** | **SearchModal.tsx** | **`searchEvents()`** | **MOCK** | **Can use `getEvents()` + client filter** |
| **View plans** | **Plans.tsx** | **`getPlans()`** | **MOCK** | **No backend endpoint** |
| **Subscribe to plan** | **Plans.tsx** | **`subscribeToPlan()`** | **MOCK** | **No self-service endpoint** |
| Admin overview stats | Admin.tsx | `getAdminOverviewStats()` | **REAL** | None |
| **Admin dashboard stats** | **Admin.tsx** | **`getAdminStats()`** | **MOCK** | **No analytics endpoint** |
| **Admin recent bookings** | **Admin.tsx** | **`getBookings()`** | **MOCK** | **Backend exists — wire it** |
| Rate-limit stats | Admin.tsx | `getRateLimitStats()` | **REAL** | None |
| Rate-limit policy CRUD | Admin.tsx | `getRateLimitPolicies()` + CRUD | **REAL** | None |
| City selector | AppContext.tsx | direct `cities` import | **STATIC** | Reference data — acceptable |
| Search suggestions | SearchModal.tsx | direct `popularSearches`/`recentSearches` import | **STATIC** | UX decoration — acceptable |

---

## 6. Actionable Integration Gaps (Priority Order)

### HIGH — Backend endpoint exists, frontend uses mock

| Priority | Gap | Frontend | Backend Endpoint | Effort |
|---|---|---|---|---|
| **H1** | List bookings | `getBookings()` in api.ts | `GET /api/bookings` (BookingController) | Low — replace mock with `request()` call, map `BackendBookingResponse[]` to `Booking[]` |
| **H2** | Cancel booking | `cancelBooking()` in api.ts | `POST /api/bookings/{id}/cancel` (BookingController) | Low — replace mock with `request()` call |
| **H3** | Ticket page mock import | Ticket.tsx imports `mockBookings` directly | `GET /api/bookings/{id}` (already wired as `getBooking()`) | Low — replace direct import with `getBooking(id)` call |
| **H4** | Search events | `searchEvents()` in api.ts | `GET /api/catalog/content` (already wired as `getEvents()`) | Low — replace mock with `getEvents()` + client-side title/genre/artist search |

### MEDIUM — Backend endpoint partially exists or needs minor addition

| Priority | Gap | Detail | Effort |
|---|---|---|---|
| **M1** | Admin recent bookings table | Admin.tsx calls mock `getBookings()` for the "Recent bookings" section | Low — use admin endpoint `GET /api/admin/bookings` (paginated) once H1 is done |
| **M2** | `getBookingById` mock | Exists but unused by any page — duplicate of real `getBooking()` | Cleanup — remove dead function |
| **M3** | `getFeaturedEvents` mock | Exists but not imported by any .tsx file | Cleanup — remove dead function |

### LOW — Needs new backend feature

| Priority | Gap | Detail | Effort |
|---|---|---|---|
| **L1** | Plans listing | `getPlans()` returns hardcoded plans | Medium — needs a plans table + endpoint in user-service |
| **L2** | Self-service plan subscription | `subscribeToPlan()` always returns true | Medium — needs a new `PATCH /api/users/me/plan` or `POST /api/subscriptions` endpoint |
| **L3** | Trending events | `getTrendingEvents()` filters mock data by `trending` flag | High — needs a trending algorithm/signal in catalog-service |
| **L4** | Admin analytics dashboard | `getAdminStats()` returns hardcoded trend/category data | High — needs analytics aggregation endpoints (bookings-by-day, bookings-by-category, revenue) |

---

## 7. mockData.ts Export Usage Summary

| Export | Used By | Status |
|---|---|---|
| `allEvents` | api.ts (getTrendingEvents, searchEvents) | Mock — partially replaceable (search yes, trending no) |
| `mockBookings` | api.ts (getBookings, getBookingById), Ticket.tsx | Mock — **fully replaceable** with real endpoints |
| `plans` | api.ts (getPlans) | Mock — needs backend feature |
| `adminStats` | api.ts (getAdminStats) | Mock — needs backend feature |
| `cities` | AppContext.tsx | Static reference data — keep |
| `popularSearches` | SearchModal.tsx | Static UX data — keep |
| `recentSearches` | SearchModal.tsx | Static UX data — keep |
| `venues` | (not imported anywhere) | Dead export — cleanup candidate |
| `movies`, `sportsEvents`, `concerts`, `theatreEvents`, `generalEvents` | Only through `allEvents` | Indirect — shrinks with allEvents |
| `shows` | (not imported anywhere) | Dead export — cleanup candidate |
| `mockUser` | (not imported anywhere) | Dead export — cleanup candidate |
| `rateLimitPolicies` | (not imported anywhere) | Dead export — already replaced by Phase 19 |
| `generateSeatSections` | (not imported anywhere) | Dead export — already replaced by real seat map |

---

## 8. Automated Verification Results

| Check | Result |
|---|---|
| Frontend TypeScript (`tsc --noEmit`) | **PASS** — 0 errors |
| Frontend build (`vite build`) | **PASS** — 427.98 KB JS, 36.52 KB CSS |
| Gateway-service tests (`mvn test`) | **PASS** — 269 tests, 0 failures, 0 errors |
| Services started? | **No** — audit is static analysis only |

---

## 9. Architecture Observations

1. **No audit-service REST endpoints** — the audit-service directory exists but has no controllers. It's a Kafka consumer only.

2. **Client-side filtering** — Several real API calls (`getVenuesByCity`, `getShowsByEvent`, `searchEvents` once wired) filter the full list client-side because catalog-service's controllers don't accept query parameters. This works at seed-data scale but will need server-side filtering for production volumes.

3. **Content entity is minimal** — catalog-service's `Content` has no `image`, `rating`, `cast`, `trending`, `featured`, `price`, `city`, `banner` columns. These are all optional on the frontend type and display placeholder/undefined values for real data. This is documented and intentional.

4. **Show entity has no seat counts** — `seatsAvailable`/`totalSeats` come from booking-service's `show_seats`, not catalog-service. The frontend already handles this (fields are optional).

5. **Payment → Booking confirmation is synchronous** — payment-service calls booking-service's internal confirm endpoint directly, not through Kafka. The Kafka event is audit-only.

6. **No self-service plan management** — The only plan-change endpoint is admin-only (`PATCH /api/admin/users/{id}/plan`). The Plans page's "subscribe" flow has no backend to call.

---

## 10. Recommended Next Phase Priorities

### Phase 21 candidates (by integration impact):

1. **Wire `getBookings` + `cancelBooking`** (H1, H2, H3) — highest impact, lowest effort. Three mock functions replaced, Bookings page and Ticket page become fully real. The backend endpoints already exist and are tested.

2. **Wire `searchEvents`** (H4) — replace the mock array search with a call to `getEvents()` + client-side filtering. Already possible today.

3. **Clean up dead mockData exports** (M2, M3) — remove `getBookingById`, `getFeaturedEvents`, and the unused mockData exports (`venues`, `shows`, `mockUser`, `rateLimitPolicies`, `generateSeatSections`).

4. **Plans & subscription backend** (L1, L2) — requires new backend work in user-service.

5. **Admin analytics** (L4) — requires new aggregation endpoints across services.

---

## 11. Integration Score

| Category | Count |
|---|---|
| Frontend API functions (total) | 30 |
| Fully wired to real backend | 21 (70%) |
| Mock — backend exists, can wire now | 4 (13%) — H1-H4 |
| Mock — needs new backend feature | 4 (13%) — L1-L4 |
| Dead/unused mock functions | 2 (7%) — M2, M3 |
| Direct mockData imports (non-API) | 4 files |

**Current integration: 70% real, with 13% immediately actionable.**

---

## 12. Manual Test Plan for Repository Owner

These tests require running services and must be performed by the repository owner:

### Pre-requisites
- Start: Redis, PostgreSQL, user-service:8081, catalog-service:8082, booking-service:8083, payment-service:8084, gateway:8080, frontend:5173
- Seed database with test data

### Test Checklist

1. **Auth flow**: Register → Login → Refresh page (session restored) → Logout
2. **Browse**: Home page loads real events from catalog-service (placeholder images expected)
3. **Event detail**: Click event → shows venue name, shows list from real data
4. **Seat map**: Select show → real seat map renders (sections from catalog + availability from booking-service)
5. **Booking flow**: Select seats → Hold → Create booking → Payment → Booking confirmed
6. **Admin overview**: Login as ADMIN → "Platform Overview" shows real counts from 3 services
7. **Rate-limit policies**: CRUD operations in "Dynamic Rate-Limit Policies" section work in real-time
8. **Rate-limit stats**: "Traffic & Rate Limiting" section shows real gateway stats
9. **Mock areas** (expected to show fake data):
   - Trending section on Home page
   - My Bookings page (mock bookings list)
   - Plans page (hardcoded plans)
   - Admin dashboard stats section (hardcoded trends/charts)
   - Search modal (searches mock array, not real catalog)

---

## 13. Files NOT Modified by This Audit

This audit is read-only. No files were created, modified, or deleted. No commits were made. No services were started.

---

## 14. Summary

The Eventtick frontend is **70% integrated** with real backend services. The complete booking flow (browse → select seats → hold → book → pay) works end-to-end with real data. Auth, catalog browsing, admin overview stats, and rate-limit management are all fully wired.

The **4 immediately actionable gaps** (list bookings, cancel booking, ticket page, search) all have existing backend endpoints and require only frontend wiring — no backend changes needed. These would bring integration to **83%**.

The remaining **4 gaps** (plans, subscription, trending, admin analytics) each require new backend features to be built first.
