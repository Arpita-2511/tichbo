# Tichboo / Eventtick — API Contracts

This document did not exist before Phase 5 (Authentication Backend). It
currently covers **only the User Service's authentication endpoints**.
Catalog Service and Booking Service already have full REST APIs
implemented (see their controllers and `GlobalExceptionHandler` classes
under `backend/catalog-service` and `backend/booking-service`), but
documenting those retroactively is out of scope for this pass — this file
should grow to cover them later rather than attempting that here.

All endpoints below are implemented by `user-service` (port `8081`) and are
reached through the API Gateway on port `8080`, which forwards
`/api/auth/**` and `/api/users/**` to it unchanged — so the same paths work
against either `http://localhost:8080` (what the frontend uses) or
`http://localhost:8081` (direct, e.g. for debugging).

---

## Error response shape

Every error from `user-service` — from `GlobalExceptionHandler` or from
Spring Security's entry point/access-denied handler — uses the same body:

```json
{
  "status": 401,
  "error": "INVALID_CREDENTIALS",
  "message": "Invalid email or password.",
  "timestamp": "2026-09-23T10:15:30Z"
}
```

| `error` code | HTTP status | Cause |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Failed `@Valid` request-body validation, or a malformed path/query value |
| `UNAUTHENTICATED` | 401 | Missing, malformed, expired, or otherwise invalid JWT on a protected route |
| `INVALID_CREDENTIALS` | 401 | Login: unknown email, or wrong password — same code and message for both, deliberately (see below) |
| `FORBIDDEN` | 403 | Authenticated, but lacking the required role (not currently reachable — no role-restricted endpoint exists yet beyond "any authenticated user") |
| `DUPLICATE_EMAIL` | 409 | Registration: email already in use |
| `DATA_INTEGRITY_CONFLICT` | 409 | Fallback for a database constraint violation the service-layer pre-check didn't catch (e.g. a genuine race on `uq_users_email`) |
| `USER_NOT_FOUND` | 404 | Defensive only — a valid JWT names a user id that no longer exists |

---

## `POST /api/auth/register`

**Authentication:** none (public — `/api/auth/**` is `permitAll()`).

**Request body:**
```json
{
  "name": "Ada Lovelace",
  "email": "ada@example.com",
  "password": "correct-password"
}
```

**Validation:**
- `name`: required, non-blank.
- `email`: required, must be a syntactically valid email address.
- `password`: required, minimum 8 characters (matches the frontend's existing rule; no character-class requirements).

**Business rules:**
- Email is normalized to lowercase before storage/lookup — `users.email` has no DB-level case constraint, so the application (this endpoint) is the one place that must enforce it.
- Duplicate email → `409 DUPLICATE_EMAIL`.
- Role is always `CUSTOMER` — not client-settable.
- Plan is always the seeded "Free" plan, looked up by name — not client-settable. If "Free" doesn't exist in the database (a deployment/seed-data problem, not a client error), the request fails with an unhandled `500` rather than a misleading 4xx.

**Success response — `201 Created`:**
```json
{
  "id": "b3f1...",
  "name": "Ada Lovelace",
  "email": "ada@example.com",
  "role": "CUSTOMER",
  "planId": "1111...",
  "planName": "Free",
  "createdAt": "2026-09-23T10:15:30Z",
  "updatedAt": "2026-09-23T10:15:30Z"
}
```
`createdAt`/`updatedAt` are `null` in *this* response only: they are database-generated (column default + trigger), so the freshly saved in-memory user doesn't have them yet. `GET /api/users/me` and login return real values.

No `Location` header — there is no `GET /api/users/{id}` endpoint yet to point at (only `GET /api/users/me`).

Never returned: `password`, `passwordHash`, or any other internal field.

**Error responses:** `400 VALIDATION_ERROR`, `409 DUPLICATE_EMAIL`, `409 DATA_INTEGRITY_CONFLICT` (rare race case).

---

## `POST /api/auth/login`

**Authentication:** none (public).

**Request body:**
```json
{ "email": "ada@example.com", "password": "correct-password" }
```

**Validation:** `email` required and syntactically valid; `password` required (no length check on login — that's a registration-time rule, not a login one).

**Business rules:** email is lowercased before lookup. An unknown email and a correct-email-wrong-password both produce the exact same `401 INVALID_CREDENTIALS` response — deliberately indistinguishable, so a caller can't use this endpoint to enumerate which emails are registered or how close a guessed password was.

**Success response — `200 OK`:**
```json
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "user": { "...": "same shape as register's response" }
}
```

**Error responses:** `400 VALIDATION_ERROR`, `401 INVALID_CREDENTIALS`.

---

## `GET /api/users/me`

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** any authenticated user (no role restriction). Identity comes **only** from the validated JWT's `sub` claim, resolved server-side — never from a path or query parameter, so a client cannot view another user's profile by supplying a different id anywhere in the request.

**Request body:** none.

**Success response — `200 OK`:** same `UserResponse` shape as registration's response body.

**Error responses:** `401 UNAUTHENTICATED` (missing/malformed/expired/invalid-signature token), `404 USER_NOT_FOUND` (defensive only — see the error-code table above).

---

## `GET /api/admin/users`

Phase 13.4 — the first admin operation. A paginated list of all users, for the Admin Dashboard's user-management view (`docs/architecture.md` §45.2/§45.3, `docs/requirements.md` FR-37).

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway** (`/api/admin/** -> hasAuthority("ROLE_ADMIN")`, Phase 13.3) — this service performs no role check of its own for this endpoint, the same "authorize once, at the edge" boundary every other admin-only path in this project uses. Called directly against `user-service` (bypassing the Gateway), any authenticated user — any role — can reach it; only requests through the Gateway are actually restricted to `ADMIN`.

**Request body:** none.

**Pagination — standard Spring `Pageable` query parameters:**

| Parameter | Default | Meaning |
|---|---|---|
| `page` | `0` | Zero-indexed page number |
| `size` | `20` | Users per page |
| `sort` | `createdAt,desc` | Newest first; overridable, e.g. `?sort=name,asc` |

**Success response — `200 OK`:** a Spring `Page<UserResponse>` — `content` is a list of the same `UserResponse` shape as registration/`/me` (`id`, `name`, `email`, `role`, `planId`, `planName`, `createdAt`, `updatedAt` — never `password`/`passwordHash`), plus standard pagination metadata:

```json
{
  "content": [
    { "id": "...", "name": "Ada Lovelace", "email": "ada@example.com", "role": "CUSTOMER",
      "planId": "1111...", "planName": "Free", "createdAt": "...", "updatedAt": "..." }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

No search or filtering yet — only pagination/sorting. No single-user admin lookup (`GET /api/admin/users/{id}`) yet — list only.

**Error responses:** `401 UNAUTHENTICATED` (missing/malformed/expired/invalid-signature token, from this service); `403 FORBIDDEN` (authenticated but not `ADMIN` — from the **Gateway**, not this service, since the check happens there; see above).

---

## `PATCH /api/admin/users/{userId}/plan`

Phase 13, FR-37 — the first admin User write operation. Changes a user's subscription plan by reassigning `users.plan_id`. No billing, proration, or business-tier rules — the target plan only needs to already exist.

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway** (`/api/admin/** -> hasAuthority("ROLE_ADMIN")`, Phase 13.3) — this service performs no role check of its own, the same boundary `GET /api/admin/users` above uses.

**Path variable:** `userId` — a UUID.

**Request body:**

```json
{ "planId": "22222222-2222-2222-2222-222222222222" }
```

**Success response — `200 OK`:** the existing `UserResponse` shape, with `planId`/`planName` reflecting the new plan:

```json
{
  "id": "...", "name": "Ada Lovelace", "email": "ada@example.com", "role": "CUSTOMER",
  "planId": "22222222-2222-2222-2222-222222222222", "planName": "Pro",
  "createdAt": "...", "updatedAt": "..."
}
```

**Error responses:** `400 VALIDATION_ERROR` (missing/malformed `planId`, or a malformed `userId` path value); `404 USER_NOT_FOUND` (`userId` doesn't exist); `404 PLAN_NOT_FOUND` (`planId` doesn't exist); `401 UNAUTHENTICATED`/`403 FORBIDDEN` (from the Gateway — see above).

---

## `PATCH /api/admin/users/{userId}/role`

Phase 13, FR-37 — the second admin User write operation. Changes a user's role to `CUSTOMER` or `ADMIN`.

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway**, same boundary as above. **Additionally, an administrator cannot change their own role** — the acting admin's identity is taken from their own validated JWT (never the request body or the `userId` path value), the same mechanism `GET /api/users/me` already uses; changing any *other* user's role in either direction (`CUSTOMER -> ADMIN`, `ADMIN -> CUSTOMER`) is allowed. There is no "last ADMIN" protection — not specified by FR-37.

**Path variable:** `userId` — a UUID.

**Request body:**

```json
{ "role": "ADMIN" }
```

Only the existing `UserRole` values (`CUSTOMER`, `ADMIN`) are accepted; anything else is a validation error.

**Success response — `200 OK`:** the existing `UserResponse` shape, with `role` reflecting the change.

**Error responses:** `400 VALIDATION_ERROR` (`role` missing, or not one of `CUSTOMER`/`ADMIN`, or a malformed `userId` path value); `404 USER_NOT_FOUND` (`userId` doesn't exist); `409 SELF_ROLE_MODIFICATION_NOT_ALLOWED` (`userId` is the acting admin's own id); `401 UNAUTHENTICATED`/`403 FORBIDDEN` (from the Gateway — see above).

No new response DTO for either endpoint — both reuse the existing `UserResponse` exactly as `GET /api/admin/users`/`GET /api/users/me` already do, via two new `UserService` methods (`changePlan`, `changeRole`) and a separate `AdminUserController`.

---

## `GET /api/admin/users/stats`

Phase 13, FR-36 — the first Admin Overview/Statistics operation. A live count, sourced directly from `user-service` (the owner of `users`) on every call — never cached or duplicated, per FR-36's own requirement that each figure come from the service that owns the underlying data.

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway**, same boundary as every other admin-only path above.

**Request:** no parameters, no body.

**Success response — `200 OK`:**

```json
{ "totalUsers": 42 }
```

**Error responses:** `401 UNAUTHENTICATED`/`403 FORBIDDEN` (from the Gateway — see above). No `400`/`404` — this endpoint takes no input that could be malformed or missing.

No new repository query — `totalUsers` is `UserRepository.count()` (inherited from `JpaRepository`), via a new `UserService.countAll()` and a new `UserStatsResponse` record. This is only the user-count slice of FR-36; content/show/venue counts (catalog-service), booking counts (booking-service), and rate-limit/traffic activity (FR-40, gateway-service) are separate, not-yet-implemented slices.

---

## `POST /api/admin/content`

Phase 13.5.2 — the first catalog admin operation. **Owned and implemented by `catalog-service` (port `8082`)** — every other endpoint on this page is `user-service`'s; this is the first `catalog-service` entry, added here as a deliberate, narrow exception (see this file's opening note — the rest of `catalog-service`'s existing, already-implemented REST API is still undocumented and out of scope for this pass).

Creates a piece of Content (the generic bookable entity — a movie, sports match, concert, etc.), for the Admin Dashboard's event/show-management view (`docs/architecture.md` §45.2/§45.3, `docs/requirements.md` FR-38).

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway** (`/api/admin/** -> hasAuthority("ROLE_ADMIN")`, Phase 13.3) — `catalog-service` has no Spring Security dependency and performs no role check of its own, the same "authorize once, at the edge" boundary `GET /api/admin/users` above uses. Called directly against `catalog-service` (bypassing the Gateway), this endpoint accepts any request the same way the existing, non-admin `POST /api/catalog/content` already does — a known, pre-existing condition (`catalog-service` has *no* independent authorization on *any* of its endpoints today, admin or not) that Phase 13.5.2 does not change.

**Request body:** the existing `ContentRequest` — identical to `POST /api/catalog/content`'s:

```json
{
  "type": "MOVIE",
  "title": "Inception",
  "description": "A mind-bending heist.",
  "language": "English",
  "duration": 148,
  "genre": "Sci-Fi",
  "releaseOrEventDate": "2010-07-16"
}
```

`type` and `title` are required (`@NotNull`/`@NotBlank`); `duration`, if present, must be positive. Everything else is optional.

**Success response — `201 Created`:** the existing `ContentResponse` shape — same as `POST /api/catalog/content`'s response, and the same `Location` header convention, pointing at the resource's one canonical (non-admin) URI:

```
Location: /api/catalog/content/{id}
```
```json
{
  "id": "...", "type": "MOVIE", "title": "Inception", "description": "A mind-bending heist.",
  "language": "English", "duration": 148, "genre": "Sci-Fi", "releaseOrEventDate": "2010-07-16",
  "createdAt": "...", "updatedAt": "..."
}
```

**Error responses:** `400 VALIDATION_ERROR` (bean validation, or a service-layer rule such as a non-positive duration — the existing `GlobalExceptionHandler`, unchanged); `401 UNAUTHENTICATED` (missing/invalid token, from the Gateway); `403 FORBIDDEN` (authenticated but not `ADMIN`, from the **Gateway**, not this service — see above).

No `AdminContentRequest`/`AdminContentResponse` — this endpoint reuses `ContentRequest`/`ContentResponse`/`ContentService.create` exactly as implemented for the existing `POST /api/catalog/content`; only the path (and, in the running system, the Gateway's authorization rule for it) differs.

---

## `PUT /api/admin/content/{id}`

FR-38 — the second Content admin operation, alongside `POST /api/admin/content` above (same controller, `AdminContentController`).

Full replace of every mutable Content field — identical behavior to `PUT /api/catalog/content/{id}`, reused unchanged via `ContentService.update`.

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway**, same boundary as `POST /api/admin/content` above.

**Path variable:** `id` — a UUID.

**Request body:** the existing `ContentRequest` — identical shape to `POST /api/admin/content`'s.

**Success response — `200 OK`:** the existing `ContentResponse` shape, reflecting the update.

**Error responses:** `400 VALIDATION_ERROR` (bean validation, a malformed `id`, or a service-layer rule such as a non-positive duration); `404 ENTITY_NOT_FOUND` (`id` doesn't exist — the existing `CatalogEntityNotFoundException`/`GlobalExceptionHandler`, unchanged); `401 UNAUTHENTICATED`/`403 FORBIDDEN` (from the Gateway — see above).

## `DELETE /api/admin/content/{id}`

FR-38 — the third Content admin operation. A genuine hard delete — identical behavior to `DELETE /api/catalog/content/{id}`, reused unchanged via `ContentService.delete`. There is no soft-delete/status alternative: `Content` has no status column.

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway**, same boundary as above.

**Path variable:** `id` — a UUID.

**Request body:** none.

**Success response — `204 No Content`.**

**Error responses:** `400 VALIDATION_ERROR` (malformed `id`); `404 ENTITY_NOT_FOUND` (`id` doesn't exist); `409 DATA_INTEGRITY_CONFLICT` (the Content is still referenced by a Show — `fk_shows_content ON DELETE RESTRICT` — not pre-checked, surfaced via the existing `DataIntegrityViolationException` handler); `401 UNAUTHENTICATED`/`403 FORBIDDEN` (from the Gateway — see above).

No new request/response DTO or exception type for either endpoint — both reuse `ContentRequest`/`ContentResponse`/`ContentService.update`/`ContentService.delete` and the existing exception handling exactly as already implemented for `PUT`/`DELETE /api/catalog/content/{id}`.

**Gateway routing note:** `PUT`/`DELETE /api/admin/content/{id}` are served by a separate explicit route, `admin-content-by-id` (`Path=/api/admin/content/{id}`), alongside the existing `admin-content` route (`Path=/api/admin/content`, no `{id}` segment) — Spring Cloud Gateway `Path` predicates match a fixed number of segments, so the collection-level `POST` and the by-id `PUT`/`DELETE` need two distinct routes even though they're all logically "the same resource."

---

## `POST /api/admin/shows`

FR-38 — the second admin Show operation, alongside `PATCH /api/admin/shows/{id}/cancel` below (same controller, `AdminShowController`).

Creates a Show (a scheduled occurrence of a piece of Content at a Venue). Identical behavior to `POST /api/catalog/shows`, reused unchanged via `ShowService.create` — a new show always starts `SCHEDULED`, not a caller-supplied value.

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway** (`/api/admin/** -> hasAuthority("ROLE_ADMIN")`, Phase 13.3) — `catalog-service` has no Spring Security dependency and performs no role check of its own, the same boundary `POST /api/admin/content` above uses.

**Request body:** the existing `ShowCreateRequest` — identical to `POST /api/catalog/shows`'s:

```json
{
  "contentId": "...",
  "venueId": "...",
  "startTime": "2026-10-01T18:00:00Z",
  "endTime": "2026-10-01T20:30:00Z"
}
```

All four fields are required (`@NotNull`); `endTime` must be after `startTime`.

**Success response — `201 Created`:** the existing `ShowResponse` shape — same as `POST /api/catalog/shows`'s response, and the same `Location` header convention, pointing at the resource's one canonical (non-admin) URI:

```
Location: /api/catalog/shows/{id}
```
```json
{
  "id": "...", "contentId": "...", "venueId": "...",
  "startTime": "2026-10-01T18:00:00Z", "endTime": "2026-10-01T20:30:00Z",
  "status": "SCHEDULED", "createdAt": "...", "updatedAt": "..."
}
```

**Error responses:** `400 VALIDATION_ERROR` (bean validation, a malformed `contentId`/`venueId`, or a service-layer rule such as `endTime` not after `startTime`); `404 ENTITY_NOT_FOUND` (`contentId` or `venueId` doesn't reference an existing Content/Venue — the existing `CatalogEntityNotFoundException`/`GlobalExceptionHandler`, unchanged); `401 UNAUTHENTICATED` (missing/invalid token, from the Gateway); `403 FORBIDDEN` (authenticated but not `ADMIN`, from the **Gateway** — see above).

No `AdminShowCreateRequest` — this endpoint reuses `ShowCreateRequest`/`ShowResponse`/`ShowService.create` exactly as implemented for the existing `POST /api/catalog/shows`; only the path (and the Gateway's authorization rule for it) differs.

**Gateway routing note:** served by a separate explicit route, `admin-shows` (`Path=/api/admin/shows`, no `{id}` segment) — distinct from `admin-show-cancel` (`Path=/api/admin/shows/{id}/cancel`) below, which has a different, longer path shape.

---

## `PUT /api/admin/shows/{id}`

FR-38 — the third admin Show operation, alongside `POST /api/admin/shows` above and `PATCH /api/admin/shows/{id}/cancel` below (same controller, `AdminShowController`).

Full replace of every mutable Show field, including `status` — identical behavior to `PUT /api/catalog/shows/{id}`, reused unchanged via `ShowService.update`. No new transition rule: this endpoint can already set any `status` value today (including `CANCELLED`), exactly as the existing customer-facing route already could.

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway**, same boundary as `POST /api/admin/shows` above.

**Path variable:** `id` — a UUID.

**Request body:** the existing `ShowUpdateRequest` — identical shape to `PUT /api/catalog/shows/{id}`'s:

```json
{
  "contentId": "...",
  "venueId": "...",
  "startTime": "2026-10-01T18:00:00Z",
  "endTime": "2026-10-01T21:00:00Z",
  "status": "SCHEDULED"
}
```

All five fields are required (`@NotNull`); `endTime` must be after `startTime`.

**Success response — `200 OK`:** the existing `ShowResponse` shape, reflecting the update.

**Error responses:** `400 VALIDATION_ERROR` (bean validation, a malformed `id`/`contentId`/`venueId`, or `endTime` not after `startTime`); `404 ENTITY_NOT_FOUND` (`id`, `contentId`, or `venueId` doesn't exist — the existing `CatalogEntityNotFoundException`/`GlobalExceptionHandler`, unchanged); `401 UNAUTHENTICATED`/`403 FORBIDDEN` (from the Gateway — see above).

No new request/response DTO or exception type — this endpoint reuses `ShowUpdateRequest`/`ShowResponse`/`ShowService.update` exactly as already implemented for `PUT /api/catalog/shows/{id}`.

**Gateway routing note:** served by a separate explicit route, `admin-show-by-id` (`Path=/api/admin/shows/{id}`) — distinct from the collection-level `admin-shows` (no `{id}` segment) and from `admin-show-cancel` (a longer, `.../cancel`-suffixed path); `{id}` matches exactly one path segment, so this never matches `.../cancel`.

---

## `DELETE /api/admin/shows/{id}`

FR-38 — the fourth admin Show operation, alongside `POST /api/admin/shows`, `PUT /api/admin/shows/{id}` above, and `PATCH /api/admin/shows/{id}/cancel` below (same controller, `AdminShowController`). A genuine hard delete — identical behavior to `DELETE /api/catalog/shows/{id}`, reused unchanged via `ShowService.delete`. There is no soft-delete/status alternative introduced; the existing database FK behavior is unchanged.

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway**, same boundary as above.

**Path variable:** `id` — a UUID.

**Request body:** none.

**Success response — `204 No Content`.**

**Error responses:** `400 VALIDATION_ERROR` (malformed `id`); `404 ENTITY_NOT_FOUND` (`id` doesn't exist); `409 DATA_INTEGRITY_CONFLICT` (the Show is still referenced by booking-service's `show_seats`/`bookings` — both `ON DELETE RESTRICT` — not pre-checked, surfaced via the existing `DataIntegrityViolationException` handler); `401 UNAUTHENTICATED`/`403 FORBIDDEN` (from the Gateway — see above).

No new request/response DTO or exception type — this endpoint reuses `ShowService.delete` and the existing exception handling exactly as already implemented for `DELETE /api/catalog/shows/{id}`.

**Gateway routing note:** **no new route was needed.** `DELETE /api/admin/shows/{id}` is served by the same `admin-show-by-id` route `PUT /api/admin/shows/{id}` already uses above — Spring Cloud Gateway `Path` predicates match regardless of HTTP method, and no route in this project uses a `Method=` predicate, so one route already covers both verbs (the same reasoning that already let `admin-content-by-id` serve both `PUT` and `DELETE /api/admin/content/{id}`).

---

## `PATCH /api/admin/shows/{id}/cancel`

Phase 13.6.2/13.6.3 — the first admin Show operation. **Owned and implemented by `catalog-service`** (port `8082`), the second `catalog-service` entry on this page (see the note on `POST /api/admin/content` above — this file otherwise still only covers `user-service`).

Cancels a Show — sets **only** its `status` to `CANCELLED`, leaving `content`, `venue`, `startTime`, and `endTime` untouched. A dedicated action, not a shorthand for the existing `PUT /api/catalog/shows/{id}` (a full replace of every mutable field, `status` included) — cancelling never risks accidentally changing anything else about the show. For the Admin Dashboard's event/show-management view (`docs/architecture.md` §45.2/§45.3, `docs/requirements.md` FR-38).

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway** (`/api/admin/** -> hasAuthority("ROLE_ADMIN")`, Phase 13.3) — `catalog-service` has no Spring Security dependency and performs no role check of its own, the same boundary `POST /api/admin/content` above uses. The same pre-existing, disclosed condition applies: called directly against `catalog-service` (bypassing the Gateway), this accepts any request the same way the existing `PUT /api/catalog/shows/{id}` already does (which can already set `status=CANCELLED` today, with no role check either).

**Request body:** none.

**Cancellation semantics — intentionally permissive in this phase:** any current status transitions to `CANCELLED`, including `SCHEDULED -> CANCELLED`, `COMPLETED -> CANCELLED`, and `CANCELLED -> CANCELLED` (a harmless no-op). There is **no** `409`-style transition rule yet (e.g. rejecting cancellation of an already-`COMPLETED` show) — a deliberate, deferred decision, not an oversight.

**Success response — `200 OK`:** the existing `ShowResponse` shape, with `status` now `CANCELLED` and every other field exactly as it was before the call:

```json
{
  "id": "...", "contentId": "...", "venueId": "...",
  "startTime": "2026-10-01T18:00:00Z", "endTime": "2026-10-01T20:30:00Z",
  "status": "CANCELLED", "createdAt": "...", "updatedAt": "..."
}
```

**Error responses:** `404 ENTITY_NOT_FOUND` (unknown id, the existing `CatalogEntityNotFoundException`/`GlobalExceptionHandler`, unchanged); `401 UNAUTHENTICATED` (missing/invalid token, from the Gateway); `403 FORBIDDEN` (authenticated but not `ADMIN`, from the **Gateway** — see above).

No new request/response DTO and no new exception type — this endpoint reuses `ShowResponse` and the existing not-found handling exactly as already implemented for `GET`/`PUT /api/catalog/shows/{id}`.

The Gateway's CORS configuration (`spring.cloud.gateway.globalcors`) allows `GET`, `POST`, `PATCH`, `OPTIONS` — this endpoint works correctly from a browser-based admin client, not just direct/server-to-server calls.

---

## `POST /api/admin/venues`

FR-38 — the first admin Venue operation. **Owned and implemented by `catalog-service`** (port `8082`), alongside the Content and Show admin operations above (see the note on `POST /api/admin/content` above — this file otherwise still only covers `user-service`).

Creates a Venue (the physical location where a Show takes place). For the Admin Dashboard's event/show-management view (`docs/architecture.md` §45.2/§45.3, `docs/requirements.md` FR-38).

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway** (`/api/admin/** -> hasAuthority("ROLE_ADMIN")`, Phase 13.3) — `catalog-service` has no Spring Security dependency and performs no role check of its own, the same boundary `POST /api/admin/content`/`POST /api/admin/shows` above use.

**Request body:** the existing `VenueRequest` — identical to `POST /api/catalog/venues`'s:

```json
{
  "name": "PVR XYZ",
  "address": "123 Main Street",
  "city": "Springfield"
}
```

`name`, `address`, and `city` are all required (`@NotBlank`).

**Success response — `201 Created`:** the existing `VenueResponse` shape — same as `POST /api/catalog/venues`'s response, and the same `Location` header convention, pointing at the resource's one canonical (non-admin) URI:

```
Location: /api/catalog/venues/{id}
```
```json
{
  "id": "...", "name": "PVR XYZ", "address": "123 Main Street", "city": "Springfield",
  "createdAt": "...", "updatedAt": "..."
}
```

**Error responses:** `400 VALIDATION_ERROR` (bean validation — a blank `name`/`address`/`city`); `401 UNAUTHENTICATED` (missing/invalid token, from the Gateway); `403 FORBIDDEN` (authenticated but not `ADMIN`, from the **Gateway**, not this service — see above). **No `409` conflict behavior:** `venues` has no unique constraint of any kind (unlike, say, `users.email`), so there is no existing conflict response to preserve or document here.

No `AdminVenueRequest`/`AdminVenueResponse` — this endpoint reuses `VenueRequest`/`VenueResponse`/`VenueService.create` exactly as implemented for the existing `POST /api/catalog/venues`; only the path (and the Gateway's authorization rule for it) differs.

**Gateway routing note:** served by a separate explicit route, `admin-venues` (`Path=/api/admin/venues`) — collection-level only for this phase; a venue delete admin route does not exist yet.

---

## `PUT /api/admin/venues/{id}`

FR-38 — the second admin Venue operation, alongside `POST /api/admin/venues` above (same controller, `AdminVenueController`).

Full replace of every mutable Venue field — identical behavior to `PUT /api/catalog/venues/{id}`, reused unchanged via `VenueService.update`.

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway**, same boundary as `POST /api/admin/venues` above.

**Path variable:** `id` — a UUID.

**Request body:** the existing `VenueRequest` — identical shape to `POST /api/admin/venues`'s.

**Success response — `200 OK`:** the existing `VenueResponse` shape, reflecting the update.

**Error responses:** `400 VALIDATION_ERROR` (bean validation, or a malformed `id`); `404 ENTITY_NOT_FOUND` (`id` doesn't exist — the existing `CatalogEntityNotFoundException`/`GlobalExceptionHandler`, unchanged); `401 UNAUTHENTICATED`/`403 FORBIDDEN` (from the Gateway — see above). No `409` conflict behavior, same reasoning as `POST /api/admin/venues` above.

No new request/response DTO or exception type — this endpoint reuses `VenueRequest`/`VenueResponse`/`VenueService.update` exactly as already implemented for `PUT /api/catalog/venues/{id}`.

**Gateway routing note:** served by a separate explicit route, `admin-venue-by-id` (`Path=/api/admin/venues/{id}`), alongside the existing `admin-venues` route (`Path=/api/admin/venues`, no `{id}` segment).

---

## `DELETE /api/admin/venues/{id}`

FR-38 — the third admin Venue operation, alongside `POST /api/admin/venues` and `PUT /api/admin/venues/{id}` above (same controller, `AdminVenueController`). A genuine hard delete — identical behavior to `DELETE /api/catalog/venues/{id}`, reused unchanged via `VenueService.delete`. There is no soft-delete/status alternative: `Venue` has no status column, and none was introduced.

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway**, same boundary as above.

**Path variable:** `id` — a UUID.

**Request body:** none.

**Success response — `204 No Content`.**

**Error responses:** `400 VALIDATION_ERROR` (malformed `id`); `404 ENTITY_NOT_FOUND` (`id` doesn't exist); `409 DATA_INTEGRITY_CONFLICT` (the Venue is still referenced by a Seat or Show — `fk_seats_venue`/`fk_shows_venue`, both `ON DELETE RESTRICT` — not pre-checked, surfaced via the existing `DataIntegrityViolationException` handler); `401 UNAUTHENTICATED`/`403 FORBIDDEN` (from the Gateway — see above).

No new request/response DTO or exception type — this endpoint reuses `VenueService.delete` and the existing exception handling exactly as already implemented for `DELETE /api/catalog/venues/{id}`.

**Gateway routing note:** **no new route was needed.** `DELETE /api/admin/venues/{id}` is served by the same `admin-venue-by-id` route `PUT /api/admin/venues/{id}` already uses above — Spring Cloud Gateway `Path` predicates match regardless of HTTP method, and no route in this project uses a `Method=` predicate, so one route already covers both verbs (the same reasoning that already let `admin-content-by-id`/`admin-show-by-id` each serve two verbs).

---

## `GET /api/admin/content/stats`

Phase 13, FR-36 — **the catalog-service portion of FR-36 (Admin Overview and Statistics)**, alongside `GET /api/admin/users/stats` (user-service, documented above). One combined endpoint for Content, Show, and Venue counts, all three owned by `catalog-service` — a live read on every call, never cached or duplicated, per FR-36's own requirement that each figure be sourced from the service that owns the underlying data.

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway**, same boundary as every other admin-only path above.

**Request:** no parameters, no body.

**Success response — `200 OK`:**

```json
{
  "totalContent": 5,
  "totalShows": 12,
  "totalVenues": 3
}
```

**Error responses:** `401 UNAUTHENTICATED`/`403 FORBIDDEN` (from the Gateway — see above). No `400`/`404` — this endpoint takes no input that could be malformed or missing.

No new repository query — each figure is `{Content,Show,Venue}Repository.count()` (inherited from `JpaRepository`), via three new one-line `countAll()` methods (on `ContentService`, `ShowService`, `VenueService`) combined by a new `CatalogStatsResponse` record and a separate `AdminCatalogStatsController`. Content/show/venue is the full catalog-service slice of FR-36's "at minimum" list; booking counts (booking-service) and rate-limit/traffic activity (FR-40, gateway-service) remain separate, not-yet-implemented slices.

**Gateway routing note:** served by a separate explicit route, `admin-content-stats` (`Path=/api/admin/content/stats`), declared **before** `admin-content-by-id` (`Path=/api/admin/content/{id}`) in `application.yml` — that route's `{id}` template variable would otherwise also literally match the segment `stats`, and Spring Cloud Gateway matches routes in list order (first match wins).

---

## `GET /api/admin/bookings`

Phase 13.7.1 — the first admin Booking operation. **Owned and implemented by `booking-service`** (port `8083`), the first `booking-service` entry on this page (see the note on `POST /api/admin/content` above — this file otherwise still only covers `user-service`, and `booking-service`'s existing, already-implemented customer-facing REST API — seat map, hold/release, create/confirm/cancel booking, per-user booking list — remains undocumented here, unchanged, and out of scope for this pass).

Admin-only, **read-only** booking monitoring across every user — lists every booking in the system, not scoped to a single `userId` the way `GET /api/bookings?userId=` is. For the Admin Dashboard's booking-management view (`docs/architecture.md` §45.2/§45.3, `docs/requirements.md` FR-39). No write path: this phase adds no cancel, confirm, or seat-activity endpoint.

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway** (`/api/admin/** -> hasAuthority("ROLE_ADMIN")`, Phase 13.3) — `booking-service` has no Spring Security dependency and performs no role check of its own, the same boundary `POST /api/admin/content`/`PATCH /api/admin/shows/{id}/cancel` above use.

**Query parameters:**

| Parameter | Default | Notes |
|---|---|---|
| `page` | `0` | zero-indexed |
| `size` | `20` | |
| `sort` | `createdAt,desc` | any `Booking` property, e.g. `sort=totalAmount,asc`; repeatable for multi-field sort |

**Success response — `200 OK`:** Spring's normal `Page` response — no custom pagination wrapper — with `content` made of the existing `BookingResponse` shape (same as `POST /api/bookings`/`GET /api/bookings/{bookingId}`'s response):

```json
{
  "content": [
    {
      "bookingId": "...", "userId": "...", "showId": "...",
      "status": "CONFIRMED", "totalAmount": 50.00,
      "seats": [ { "bookingSeatId": "...", "showSeatId": "...", "priceAtBooking": 25.00 } ],
      "createdAt": "...", "updatedAt": "..."
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

**Error responses:** `401 UNAUTHENTICATED` (missing/invalid token, from the Gateway); `403 FORBIDDEN` (authenticated but not `ADMIN`, from the **Gateway** — see above).

No new request/response DTO — this endpoint reuses the existing `BookingResponse`/`BookingSeatDto` exactly as already implemented, via a new `BookingService.listAll(Pageable)` (plain `BookingRepository.findAll(Pageable)`) and a separate `AdminBookingController`.

---

## `GET /api/admin/shows/{showId}/seat-activity`

Phase 13.7.2 — the second admin Booking operation. **Owned and implemented by `booking-service`** (port `8083`), alongside `GET /api/admin/bookings` above.

Admin-only, **read-only** view of a show's current seat/hold/booking state — which `show_seats` rows exist for the show and each one's `AVAILABLE`/`HELD`/`BOOKED` status and price. For the Admin Dashboard's booking-management view (`docs/architecture.md` §45.2/§45.3, `docs/requirements.md` FR-39). Reuses the exact same query the customer-facing `GET /api/bookings/shows/{showId}/seats` already uses (`ShowSeatQueryService.getSeatMap`) and its response shape — no new repository query, no new DTO.

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway** (`/api/admin/** -> hasAuthority("ROLE_ADMIN")`, Phase 13.3) — `booking-service` has no Spring Security dependency and performs no role check of its own, the same boundary `GET /api/admin/bookings` above uses.

**Path variable:** `showId` — a UUID.

**Request body:** none.

**Success response — `200 OK`:** the existing `SeatMapResponse`/`SeatMapItemDto` shape — same as `GET /api/bookings/shows/{showId}/seats`'s response:

```json
{
  "showId": "...",
  "seats": [
    { "showSeatId": "...", "seatId": "...", "status": "HELD", "price": 25.00 },
    { "showSeatId": "...", "seatId": "...", "status": "BOOKED", "price": 25.00 }
  ]
}
```

This shape does not include which booking (if any) currently holds a seat, or seat labels such as section/row/number — the underlying `show_seats` row carries no booking reference, and seat labels live in `catalog-service`'s `seats` table; the existing query does not fetch either, and this phase does not invent new fields beyond what it already provides.

**A show with no `show_seats` rows (unknown or not-yet-seeded `showId`) returns `200 OK` with an empty `seats` list, not `404`** — this matches the existing `GET /api/bookings/shows/{showId}/seats` behavior exactly; the underlying query has no not-found case to report.

**Error responses:** `400 VALIDATION_ERROR` (`showId` isn't a well-formed UUID, the existing `MethodArgumentTypeMismatchException` handling); `401 UNAUTHENTICATED` (missing/invalid token, from the Gateway); `403 FORBIDDEN` (authenticated but not `ADMIN`, from the **Gateway** — see above).

No new request/response DTO — this endpoint reuses the existing `SeatMapResponse`/`SeatMapItemDto`/`ShowSeatQueryService.getSeatMap` exactly as already implemented for `GET /api/bookings/shows/{showId}/seats`, via a separate `AdminShowSeatActivityController`.

---

## `GET /api/admin/bookings/stats`

Phase 13, FR-36 — **the booking-service portion of FR-36 (Admin Overview and Statistics)**, alongside `GET /api/admin/users/stats` (user-service) and `GET /api/admin/content/stats` (catalog-service). A live count of all bookings, owned by `booking-service` — read fresh on every call, never cached or duplicated, per FR-36's own requirement that each figure be sourced from the service that owns the underlying data.

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced **only at the API Gateway**, same boundary as `GET /api/admin/bookings` above.

**Request:** no parameters, no body.

**Success response — `200 OK`:**

```json
{ "totalBookings": 17 }
```

**Error responses:** `401 UNAUTHENTICATED`/`403 FORBIDDEN` (from the Gateway — see above). No `400`/`404` — this endpoint takes no input that could be malformed or missing.

No new repository query — `totalBookings` is `BookingRepository.count()` (inherited from `JpaRepository`), via a new one-line `BookingService.countAll()` and a new `BookingStatsResponse` record, exposed from the existing `AdminBookingController`. This completes FR-36's "counts of users, content/shows, and bookings" — see `GET /api/admin/rate-limits/stats` below for FR-40, the current traffic/rate-limit activity piece.

---

## `GET /api/admin/rate-limits/stats`

Phase 13, FR-40 (Admin Rate-Limit Visibility) — **owned and served directly by `gateway-service`** (port `8080`), the first endpoint gateway-service serves locally rather than proxying to a downstream service. There is deliberately **no `application.yml` route** for this path: `AdminRateLimitController` answers the request itself; nothing is forwarded anywhere.

**Authentication:** required — `Authorization: Bearer <accessToken>`.

**Authorization:** `role=ADMIN`. Enforced by the **same existing** `/api/admin/** -> hasAuthority("ROLE_ADMIN")` rule in `GatewaySecurityConfig` (Phase 13.3) — that rule already applies to any request the reactive `SecurityWebFilterChain` sees, whether it is ultimately served by a proxied route or, as here, a local `@RestController`. No new security configuration was added.

**Request:** no parameters, no body.

**Success response — `200 OK`:**

```json
{
  "policies": {
    "AUTH":    { "PUBLIC":  { "replenishRate": 2,  "burstCapacity": 5,   "requestedTokens": 1 } },
    "CATALOG": { "FREE": { "replenishRate": 8, "burstCapacity": 16, "requestedTokens": 1 }, "PRO": {...}, "PREMIUM": {...}, "ADMIN": {...} },
    "BOOKING": { "FREE": {...}, "PRO": {...}, "PREMIUM": {...}, "ADMIN": {...} },
    "USER":    { "FREE": {...}, "PRO": {...}, "PREMIUM": {...}, "ADMIN": {...} }
  },
  "fallback": { "replenishRate": 2, "burstCapacity": 5, "requestedTokens": 1 },
  "activity": {
    "redisAvailable": true,
    "byPolicy": {
      "AUTH:PUBLIC":  { "allowed": 120, "rejected": 3 },
      "CATALOG:FREE": { "allowed": 540, "rejected": 0 },
      "FALLBACK":     { "allowed": 12,  "rejected": 1 }
    }
  }
}
```

**`policies`/`fallback` — the active policy matrix.** Read live from the existing `RateLimitPolicyProperties` bean (already bound from `eventtick.rate-limit.*` in `application.yml` at startup, Phase 12) — nothing in this response is hardcoded; every key/tier/number reflects whatever is currently configured. **Read-only**: this endpoint cannot edit policies at runtime, consistent with FR-40 ("shall **not** allow editing... Policies remain configuration-driven... a restart to change"); FR-31 remains explicitly out of scope.

**`activity` — current rate-limit activity.** Counted since this gateway instance's counters were initialized — **not** a sliding time window, **not** historical analytics, **not** a permanent audit record. Counters reset on every gateway restart; there is no TTL/time-bucketing. `byPolicy` is keyed by the same `CATEGORY:TIER` id the rate limiter itself resolves per request (plus `FALLBACK` for unrecognized paths/gaps in the matrix) — every configured policy id appears, even ones that haven't seen any traffic yet (at `0`/`0`).

*Source*: `RateLimitingGlobalFilter.respond()` — the single global filter that already sees every rate-limit decision — calls `RateLimitActivityRecorder.recordAllowed`/`recordRejected` for every request, purely as an observational side effect; it never influences the actual `isAllowed`/`429` decision, which is unchanged.

*Redis namespace*: a **completely separate** key prefix, `gateway:admin:rate-limit-activity:{policyId}:{allowed|rejected}`, incremented via plain Redis `INCR` through the existing `ReactiveStringRedisTemplate` bean (already auto-configured by the existing `spring-boot-starter-data-redis-reactive` dependency — no new dependency was added). This is deliberately **not** a read of `RedisRateLimiter`'s own internal token-bucket keys (`request_rate_limiter.{id}.tokens`/`.timestamp`) — those are an undocumented Spring Cloud Gateway implementation detail, and coupling to them would be unsafe; the two namespaces never overlap.

**If Redis is unreachable:** the endpoint still returns **`200 OK`**, never a `5xx`. `policies`/`fallback` are returned correctly regardless (they come from in-memory config, not Redis). `activity.redisAvailable` is `false` and `activity.byPolicy` is an empty object. Recording (the write side, in `RateLimitingGlobalFilter`) is fire-and-forget and non-blocking — a Redis failure there is logged and swallowed, and can never cause the *original* request (the one being rate-limited) to fail; this is the same fail-open philosophy `RedisRateLimiter` itself already uses, extended to the new counters.

**Error responses:** `401 UNAUTHENTICATED`/`403 FORBIDDEN` (from the Gateway — see above). No `400`/`404` — this endpoint takes no input that could be malformed or missing, and never 5xxs on a Redis outage (see above).

**What the Gateway does *not* store:** the new counters hold only a policy id (`CATEGORY:TIER`, a gateway-internal classification label) and two integers — never a `userId`, `bookingId`, or any other business/domain identifier. This keeps the Gateway an operational component, not a business-data store, consistent with `docs/architecture.md` §33 ("the Gateway does not contain business logic"), now extended to "the Gateway does not accumulate business data" for FR-40.

No new request/response DTO beyond `RateLimitStatsResponse`/`RateLimitPolicyDto`/`RateLimitActivityDto`/`RateLimitActivitySection` (all new, gateway-service-only) — the existing `RateLimitPolicyProperties`/`RateLimitingGlobalFilter` are otherwise unchanged.

---

## CORS (browser clients)

Added in Phase 6 so the frontend (Vite dev server, `http://localhost:5173`) could call `user-service` from the browser — different origins, so without this the browser's preflight `OPTIONS` request is rejected before the real request is ever sent. **Since Phase 7.2 the frontend calls the API Gateway instead, and the gateway's own CORS config is what the browser actually hits** (see `backend/gateway-service/README.md`). This `user-service` config is kept as defense-in-depth for direct calls; the gateway removes the resulting duplicate `Access-Control-Allow-Origin` header on proxied responses.

- Allowed origins come from `cors.allowed-origins` (env: `CORS_ALLOWED_ORIGINS`, comma-separated; default `http://localhost:5173,http://127.0.0.1:5173`). Never `*`. If Vite falls back to another port (e.g. 5174), set the variable to match.
- Only `GET`, `POST`, `OPTIONS`; only the `Authorization` and `Content-Type` request headers; no credentials/cookies (auth is the `Authorization: Bearer` header).
- Applies to `/api/**`. Error responses (e.g. 401) carry the CORS headers too, so the browser lets the frontend read the error body.
- An origin not on the list gets `403` on preflight.

Once an API Gateway fronts these services, CORS should be configured there instead of per service.

---

## Frontend integration (Phase 6)

`eventtick/src/services/api.ts` calls these endpoints through the API Gateway. The base URL is the gateway's origin only, without `/api` (the request paths already start with `/api/...`): `VITE_API_BASE_URL`, default `http://localhost:8080`.

- **Signup:** `POST /api/auth/register`, then an automatic `POST /api/auth/login` with the same credentials (registration returns no token). Only `name`, `email`, `password` are sent — there is no `phone` field in the backend contract.
- **Login:** the returned `accessToken` is kept in `localStorage` (`eventtick.accessToken`). Passwords are never stored.
- **Session restore on page load:** `GET /api/users/me` with the stored token. A `401` clears the token; a network failure keeps it.
- **Logout:** removes the token locally — there is no logout endpoint (stateless JWT).
- **Plan display:** the backend's `planName` (`Free` / `Pro` / `Premium`) is upper-cased into the frontend's `SubscriptionPlan`. Display only, never used for access control.

---

## JWT structure

Issued by `user-service`, HMAC-SHA256 signed. Claims:

| Claim | Meaning |
|---|---|
| `sub` | User id (UUID, as a string) |
| `email` | The user's (lowercased) email |
| `role` | `CUSTOMER` or `ADMIN` |
| `plan` | The user's current plan name |
| `iss` | Configured issuer (`jwt.issuer`, default `eventtick-user-service`) |
| `aud` | Configured audience (`jwt.audience`, default `eventtick-clients`) |
| `iat` / `exp` | Issued-at / expiry (`jwt.expiration-minutes`, default 60 minutes) |

No `nbf` claim — not needed for this simple case. No sensitive data (password hash, etc.) in any claim. Validation checks signature, issuer, audience, and expiration; a mismatch on any of them makes the token invalid, with no distinction surfaced to the caller about which check failed.

**Configuration** (`backend/user-service/src/main/resources/application.yml`), all overridable via environment variables:

```
JWT_SECRET             (required for anything beyond local dev — see below)
JWT_ISSUER              (default: eventtick-user-service)
JWT_AUDIENCE            (default: eventtick-clients)
JWT_EXPIRATION_MINUTES  (default: 60)
```

`JWT_SECRET`'s fallback value in `application.yml` is a clearly-labeled, non-secret placeholder for local development only (long enough to satisfy HS256's minimum 32-byte key requirement) — it must be overridden by a real environment variable before any shared or production deployment. It is not committed as a real secret anywhere.

---

## Refresh tokens — deferred

Not implemented in this phase. No refresh-token table/entity/migration exists.

**Why:** the User Service didn't have any authentication at all before this phase — access-token-only JWT auth is already a complete, independently useful, testable increment (register → login → authenticated request all work end to end). Refresh tokens add real design surface of their own (storage — a new table and migration; rotation on use vs. static reuse; revocation on logout; handling a stolen refresh token) that deserves a focused pass, not a same-day addition bolted onto an already-large phase. Deferring it doesn't block anything else — the frontend can integrate against access-token auth now and gain refresh tokens later without a breaking change to `/api/auth/login`'s response shape (a `refreshToken` field can be added to `AuthResponse` additively).

**When implemented:** a new migration (`0011_...`, per the existing numbering convention — never rewriting an applied one) would add a refresh-token table; `AuthService` would issue both tokens at login; a new `POST /api/auth/refresh` endpoint would validate the refresh token and issue a new access token without requiring the password again.
