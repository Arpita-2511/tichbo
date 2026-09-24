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
