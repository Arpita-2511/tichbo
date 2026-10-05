// ─────────────────────────────────────────────────────────────────────────────
// Eventtick API Service Layer
//
// REAL (go through the Gateway to a real backend service — see each
// function's own comment for which one and which endpoint):
//   login, signup, logout, getCurrentUser (Auth)
//   getSeatMap, holdSeats (Seats)
//   createBooking, getBooking (Bookings — see each one's own comment)
//   createPayment (Payments — see its own comment for exactly what it
//     does and does not do)
//   getEvents, getEventById, getVenueById, getVenuesByCity,
//     getShowsByEvent, getShowById (Catalog — this file's latest real
//     integration; see each one's own comment, especially for the fields
//     catalog-service's real data doesn't carry)
//   getBookings, cancelBooking (Bookings — user's own list + cancellation)
//   searchEvents (Search — real catalog with client-side filtering)
//   getAdminOverviewStats, getRateLimitStats, getAdminBookings (Admin)
// getTrendingEvents stays on mock data — catalog-service has no
// "trending" concept to source it from.
// getAdminStats, getPlans, subscribeToPlan still return mock data,
// until their own backend integration phases.
//
// All frontend components should ONLY call functions from this file,
// never fetch data directly.
//
// Future pattern:
//   return apiClient.get(`/events/${id}`)
//
// ─────────────────────────────────────────────────────────────────────────────

import type {
  Content, Venue, Show, Booking, User, Plan,
  AdminStats, AdminOverviewStats, RateLimitStatsResponse, SearchResult, EventFilters, ContentType,
  Seat, SeatRow, SeatSection, SeatCategory, SeatStatus, SubscriptionPlan
} from '../types';

import {
  allEvents, plans,
  adminStats
} from '../data/mockData';

// ─── API Client Configuration ─────────────────────────────────────────────────
// BASE_URL is the API Gateway's ORIGIN — scheme + host + port only. It must
// NOT include "/api": the request paths below already start with "/api/...",
// and the gateway forwards them to the services unchanged. Override with
// VITE_API_BASE_URL (e.g. when the gateway is deployed elsewhere); a
// trailing slash is tolerated.
const BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');

// Simulate network latency
const delay = (ms = 400) => new Promise(resolve => setTimeout(resolve, ms));

// Future: replace with real HTTP client
// const apiClient = axios.create({ baseURL: BASE_URL, headers: { Authorization: `Bearer ${token}` } });

export { BASE_URL };

// catalog-service's Content entity (database/migrations/0004) has no
// image/banner/rating/cast/trending/featured/price/city/etc. columns at
// all — only the fields below. Every other optional Content field (all of
// them except `image`, which is the type's one *required* field) is left
// undefined for real data; that's intentional, not an omission, and each
// consuming component already guards those fields as optional.
const PLACEHOLDER_IMAGE =
  'data:image/svg+xml;charset=UTF-8,%3Csvg xmlns="http://www.w3.org/2000/svg" width="400" height="600" viewBox="0 0 400 600"%3E%3Crect width="400" height="600" fill="%231e1e2e"/%3E%3Ctext x="200" y="300" font-family="sans-serif" font-size="20" fill="%236b7280" text-anchor="middle"%3ENo image%3C/text%3E%3C/svg%3E';

interface BackendContent {
  id: string;
  type: ContentType;
  title: string;
  description: string | null;
  language: string | null;
  duration: number | null;
  genre: string | null;
  releaseOrEventDate: string | null;
  createdAt: string;
  updatedAt: string;
}

function toContent(c: BackendContent): Content {
  return {
    id: c.id,
    type: c.type,
    title: c.title,
    description: c.description ?? '',
    image: PLACEHOLDER_IMAGE,
    language: c.language ?? undefined,
    duration: c.duration ?? undefined,
    genre: c.genre ?? undefined,
    releaseDate: c.releaseOrEventDate ?? undefined,
  };
}

// ─── Event / Content ─────────────────────────────────────────────────────────

/**
 * GET /api/catalog/content — catalog-service's full content list. There is
 * no server-side `type`/filter query param (ContentController.list() takes
 * none), so `type` and the rest of `filters` are applied client-side, same
 * as the mock implementation this replaces. Most `filters` fields
 * (city/genre/minRating/sport) match against Content properties the real
 * backend doesn't populate (see {@link toContent}) and are effectively
 * inert until/unless a future catalog phase adds them — no caller
 * currently passes `filters`, so this is not a behavior change today.
 */
export async function getEvents(
  type?: ContentType,
  filters?: EventFilters
): Promise<Content[]> {
  const content = await request<BackendContent[]>('/api/catalog/content', { auth: true });
  let data = content.map(toContent);

  if (type) {
    data = data.filter(e => e.type === type);
  }
  if (filters?.city && filters.city !== 'All Cities') {
    data = data.filter(e => e.city === filters.city);
  }
  if (filters?.genre) {
    data = data.filter(e => e.genre?.toLowerCase().includes(filters.genre!.toLowerCase()));
  }
  if (filters?.language) {
    data = data.filter(e => e.language?.toLowerCase() === filters.language!.toLowerCase());
  }
  if (filters?.minRating) {
    data = data.filter(e => (e.rating || 0) >= filters.minRating!);
  }
  if (filters?.sport) {
    data = data.filter(e => e.sport?.toLowerCase() === filters.sport!.toLowerCase());
  }

  return data;
}

/** GET /api/catalog/content/{id}. Returns null on 404, same as the mock it replaces. */
export async function getEventById(id: string): Promise<Content | null> {
  try {
    return toContent(await request<BackendContent>(`/api/catalog/content/${id}`, { auth: true }));
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null;
    throw err;
  }
}

// catalog-service's Content has no "trending"/"featured" concept at all —
// there is no real signal to source this from (and no endpoint to invent),
// so these two stay on mock data until/unless a future phase defines one.
// Phase 2's own inspection requirement ("if actually supported") concluded
// it is not; see this integration's own report for the full reasoning.
export async function getTrendingEvents(): Promise<Content[]> {
  await delay(350);
  return allEvents.filter(e => e.trending).slice(0, 8);
}


// ─── Venues ──────────────────────────────────────────────────────────────────

// catalog-service's Venue entity (database/migrations/0005) has no state/
// pincode/mapUrl/amenities/totalCapacity columns — those Venue fields stay
// undefined for real data (all already optional on the frontend type).
interface BackendVenueResponse {
  id: string;
  name: string;
  address: string;
  city: string;
  createdAt: string;
  updatedAt: string;
}

function toVenue(v: BackendVenueResponse): Venue {
  return { id: v.id, name: v.name, address: v.address, city: v.city };
}

/** GET /api/catalog/venues/{id}. Returns null on 404, same as the mock it replaces. */
export async function getVenueById(id: string): Promise<Venue | null> {
  try {
    return toVenue(await request<BackendVenueResponse>(`/api/catalog/venues/${id}`, { auth: true }));
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null;
    throw err;
  }
}

/**
 * VenueController.list() has no `city` query param either — closest
 * supported API is the full list, filtered client-side (exact match, same
 * as the mock it replaces).
 */
export async function getVenuesByCity(city: string): Promise<Venue[]> {
  const list = await request<BackendVenueResponse[]>('/api/catalog/venues', { auth: true });
  return list.filter(v => v.city === city).map(toVenue);
}

// ─── Shows ───────────────────────────────────────────────────────────────────

// catalog-service's Show entity (database/migrations/0007) has no
// language/format/seatsAvailable/totalSeats columns — per-show seat
// counts are booking-service's show_seats, a different service's data
// this response doesn't carry. Those Show fields stay undefined for real
// data (all already optional on the frontend type, already guarded by
// every consumer). `available` is a direct, honest mapping of the real
// `status` column, not an invented heuristic.
interface BackendShowResponse {
  id: string;
  contentId: string;
  venueId: string;
  startTime: string;
  endTime: string;
  status: 'SCHEDULED' | 'CANCELLED' | 'COMPLETED';
  createdAt: string;
  updatedAt: string;
}

function toShow(s: BackendShowResponse): Show {
  return {
    id: s.id,
    contentId: s.contentId,
    venueId: s.venueId,
    startTime: s.startTime,
    endTime: s.endTime,
    date: s.startTime.slice(0, 10),
    timeLabel: new Date(s.startTime).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' }),
    available: s.status === 'SCHEDULED',
  };
}

/**
 * ShowController.list() has no `contentId` query param (its own comment
 * says so explicitly — {@code ShowService.listByContent} exists but isn't
 * wired to any endpoint) — closest supported API is the full show list,
 * filtered client-side. Not paginated; fine for this project's seed-data
 * scale, same ceiling every other unpaginated list call here already has.
 */
export async function getShowsByEvent(contentId: string): Promise<Show[]> {
  const list = await request<BackendShowResponse[]>('/api/catalog/shows', { auth: true });
  return list.filter(s => s.contentId === contentId).map(toShow);
}

/**
 * GET /api/catalog/shows/{id}. Returns null on 404, same as the mock it
 * replaces. Deliberately separate from {@link getSeatMap}'s own internal
 * `BackendShow`/request call against this same endpoint (it only needs
 * `venueId`) — left as-is rather than refactored to share this, to avoid
 * touching that already-working code for this integration.
 */
export async function getShowById(showId: string): Promise<Show | null> {
  try {
    return toShow(await request<BackendShowResponse>(`/api/catalog/shows/${showId}`, { auth: true }));
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null;
    throw err;
  }
}

// ─── Seats ───────────────────────────────────────────────────────────────────

// The one field getSeatMap needs from GET /api/catalog/shows/{id} (catalog
// ShowResponse) — which venue this show is in.
interface BackendShow { venueId: string; }

// catalog-service's SeatResponse (GET /api/catalog/seats?venueId=) — the
// physical, venue-owned seat: section/row/number/type. Does not vary per
// show, and is never invented from the booking-service response.
interface BackendCatalogSeat {
  id: string;
  venueId: string;
  section: string;
  row: string;
  seatNumber: number;
  seatType: 'STANDARD' | 'PREMIUM' | 'VIP';
}

// booking-service's SeatMapResponse (GET /api/bookings/shows/{showId}/seats)
// — show-specific availability and price. showSeatId (not seatId) becomes
// Seat.id: it's the id a future hold/booking call needs (createBooking is
// still mock — see this file's own header comment — but the seat map must
// already expose the right id for when that's wired up).
interface BackendShowSeat {
  showSeatId: string;
  seatId: string;
  status: 'AVAILABLE' | 'HELD' | 'BOOKED';
  price: number;
}
interface BackendSeatMapResponse { showId: string; seats: BackendShowSeat[] }

function toSeatCategory(seatType: BackendCatalogSeat['seatType']): SeatCategory {
  switch (seatType) {
    case 'VIP': return 'VIP';
    case 'PREMIUM': return 'PREMIUM';
    case 'STANDARD': return 'REGULAR';
  }
}

// HELD is another customer's in-progress hold, not this browser's own local
// selection — 'SELECTED' is frontend-only UI state that SeatMap.tsx itself
// manages and is never produced here. From this browser's point of view a
// HELD seat is exactly as unavailable as a BOOKED one.
function toSeatStatus(status: BackendShowSeat['status']): SeatStatus {
  switch (status) {
    case 'AVAILABLE': return 'AVAILABLE';
    case 'BOOKED': return 'BOOKED';
    case 'HELD': return 'UNAVAILABLE';
  }
}

/**
 * The real seat map (catalog-service's physical seat metadata joined with
 * booking-service's show-specific availability/pricing) — replaces the
 * mock {@code generateSeatSections}.
 *
 * booking-service's {@code show_seats} is the authoritative driving set
 * (see docs/architecture.md's ownership model): every entry comes from
 * {@code seatMap.seats}, looked up against its catalog seat by
 * {@code catalogSeat.id === showSeat.seatId} — never by array position or
 * by row/number, and never the reverse (iterating catalog seats would let
 * a seat with no show-seat record for this show render as if it existed).
 * A show-seat with no matching catalog record (a data inconsistency, not
 * expected in practice) is skipped rather than rendered with fabricated
 * row/number/category — there is nothing to invent it from.
 */
export async function getSeatMap(showId: string): Promise<SeatSection[]> {
  const show = await request<BackendShow>(`/api/catalog/shows/${showId}`, { auth: true });

  const [catalogSeats, seatMap] = await Promise.all([
    request<BackendCatalogSeat[]>(`/api/catalog/seats?venueId=${show.venueId}`, { auth: true }),
    request<BackendSeatMapResponse>(`/api/bookings/shows/${showId}/seats`, { auth: true }),
  ]);
  const catalogSeatById = new Map(catalogSeats.map(s => [s.id, s]));

  const seats: Seat[] = [];
  for (const showSeat of seatMap.seats) {
    const catalogSeat = catalogSeatById.get(showSeat.seatId);
    if (!catalogSeat) continue;

    seats.push({
      id: showSeat.showSeatId,
      showId,
      row: catalogSeat.row,
      number: catalogSeat.seatNumber,
      label: `${catalogSeat.row}${catalogSeat.seatNumber}`,
      category: toSeatCategory(catalogSeat.seatType),
      price: showSeat.price,
      status: toSeatStatus(showSeat.status),
    });
  }

  return groupIntoSections(seats);
}

// category -> row -> seats (row order stable within a category: VIP, then
// PREMIUM, then REGULAR — the same fixed priority order the previous mock
// generator used, not alphabetical/backend order). Seats within a row are
// sorted by seat number ascending — never relying on backend ordering.
//
// A section's `price` is the lowest price among its own seats: SeatMap.tsx
// only ever displays it as a single "— ₹N" label beside the category
// heading (never as a total), so a real per-category price spread is not
// silently discarded — every seat still carries and is billed at its own
// exact `price` — but the label needs one representative number, and the
// conventional, honest choice for that is "starting from".
function groupIntoSections(seats: Seat[]): SeatSection[] {
  const CATEGORY_ORDER: SeatCategory[] = ['VIP', 'PREMIUM', 'REGULAR'];

  const byCategory = new Map<SeatCategory, Map<string, Seat[]>>();
  for (const seat of seats) {
    let rows = byCategory.get(seat.category);
    if (!rows) { rows = new Map(); byCategory.set(seat.category, rows); }
    let rowSeats = rows.get(seat.row);
    if (!rowSeats) { rowSeats = []; rows.set(seat.row, rowSeats); }
    rowSeats.push(seat);
  }

  const sections: SeatSection[] = [];
  for (const category of CATEGORY_ORDER) {
    const rowsByLabel = byCategory.get(category);
    if (!rowsByLabel) continue;

    let minPrice = Infinity;
    const seatRows: SeatRow[] = [...rowsByLabel.keys()].sort().map(row => {
      const rowSeats = rowsByLabel.get(row)!.sort((a, b) => a.number - b.number);
      for (const s of rowSeats) minPrice = Math.min(minPrice, s.price);
      return { row, category, seats: rowSeats };
    });

    sections.push({ category, price: minPrice, rows: seatRows });
  }
  return sections;
}

// The booking service's own hold response (SeatMapResponse's shape,
// reused — see SeatMapItemDto): the seats that actually ended up HELD.
export interface HoldSeatsInput {
  userId: string;
  showSeatIds: string[];
}
export interface HeldSeat { showSeatId: string; seatId: string; status: 'HELD'; price: number }
export interface HoldSeatsResponse { showId: string; heldSeats: HeldSeat[] }

/**
 * POST /api/bookings/shows/{showId}/seats/hold — AVAILABLE -> HELD for the
 * given show-seats. `data.showSeatIds` are {@link Seat.id} values from
 * {@link getSeatMap} (which already maps Seat.id to the booking-service's
 * own showSeatId — see that function's own comments), not catalog seat ids.
 *
 * Throws {@link ApiError} on failure (e.g. 409 if a seat is no longer
 * AVAILABLE) — the caller decides how to present that, same convention as
 * every other real call in this file.
 */
export async function holdSeats(showId: string, data: HoldSeatsInput): Promise<HoldSeatsResponse> {
  return request<HoldSeatsResponse>(`/api/bookings/shows/${showId}/seats/hold`, {
    method: 'POST',
    auth: true,
    body: data,
  });
}

// ─── Bookings ────────────────────────────────────────────────────────────────

// booking-service's own CreateBookingRequest (POST /api/bookings) —
// userId/showId/showSeatIds only. The backend computes totalAmount itself
// server-side, from each show-seat's own authoritative price; it does not
// accept (and never did accept) contentId/venueId/category/ticketPrice/
// convenienceFee/totalAmount — those were this file's own earlier
// mock-only invention and are never sent to the real endpoint.
// showSeatIds are the same Seat.id values holdSeats already used — the
// seats must already be HELD (by this same user) for this call to succeed.
export interface CreateBookingInput {
  userId: string;
  showId: string;
  showSeatIds: string[];
}

// booking-service's own BookingSeatDto / BookingResponse (POST/GET
// /api/bookings...). status is PENDING immediately after creation —
// payment-service is what later moves a booking to CONFIRMED; this call
// never does that itself (docs/architecture.md's payment/booking
// synchronous boundary — out of scope for this integration). createdAt/
// updatedAt are genuinely null on this exact response (those columns are
// database-generated and not re-read after insert — the backend's own
// documented behavior, not a bug to work around).
export interface BackendBookingSeat { bookingSeatId: string; showSeatId: string; priceAtBooking: number }
export interface BackendBookingResponse {
  bookingId: string;
  userId: string;
  showId: string;
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'FAILED';
  totalAmount: number;
  seats: BackendBookingSeat[];
  createdAt: string | null;
  updatedAt: string | null;
}

/**
 * POST /api/bookings — creates a real booking from show-seats that must
 * already be HELD by this same user (see {@link holdSeats}, called
 * earlier in the flow). Throws {@link ApiError} on failure — notably
 * `409 INVALID_SEAT_STATE` if a seat is no longer HELD (e.g. the hold was
 * lost between holding and confirming), `403 FORBIDDEN` if `userId`
 * doesn't match the caller's own JWT, `400 SEAT_SHOW_MISMATCH`/
 * `VALIDATION_ERROR` for a malformed request. The caller decides how to
 * present each of these, same convention as every other real call here.
 */
export async function createBooking(data: CreateBookingInput): Promise<BackendBookingResponse> {
  return request<BackendBookingResponse>('/api/bookings', {
    method: 'POST',
    auth: true,
    body: data,
  });
}

/**
 * GET /api/bookings/{bookingId} — the same real {@link BackendBookingResponse}
 * {@link createBooking} returns, re-fetched fresh. Used to check a booking's
 * current `status` after a payment, since payment success confirms the
 * booking synchronously on the backend (see {@link createPayment}'s own
 * comment) but that confirmation can occasionally still be in flight when
 * the payment call returns. Throws {@link ApiError} on failure (404 if the
 * booking doesn't exist, 403 if it belongs to another user).
 */
export async function getBooking(bookingId: string): Promise<BackendBookingResponse> {
  return request<BackendBookingResponse>(`/api/bookings/${bookingId}`, { auth: true });
}

function toBaseBooking(b: BackendBookingResponse): Booking {
  const seatCount = b.seats.length;
  return {
    id: b.bookingId,
    bookingRef: b.bookingId.slice(0, 8).toUpperCase(),
    userId: b.userId,
    showId: b.showId,
    contentId: '',
    venueId: '',
    seats: [seatCount === 1 ? '1 Seat' : `${seatCount} Seats`],
    category: 'REGULAR',
    ticketPrice: b.totalAmount,
    convenienceFee: 0,
    totalAmount: b.totalAmount,
    status: b.status,
    createdAt: b.createdAt ?? new Date().toISOString(),
  };
}

async function enrichBookings(raw: BackendBookingResponse[]): Promise<Booking[]> {
  const bookings = raw.map(toBaseBooking);
  if (bookings.length === 0) return bookings;

  const uniqueShowIds = [...new Set(raw.map(b => b.showId))];

  const shows = await Promise.all(
    uniqueShowIds.map(id => getShowById(id).catch(() => null))
  );
  const showMap = new Map(
    shows.filter((s): s is Show => s !== null).map(s => [s.id, s])
  );

  const contentIds = [...new Set(
    [...showMap.values()].map(s => s.contentId).filter(Boolean)
  )];
  const venueIds = [...new Set(
    [...showMap.values()].map(s => s.venueId).filter(Boolean)
  )];

  const [contents, venues] = await Promise.all([
    Promise.all(contentIds.map(id => getEventById(id).catch(() => null))),
    Promise.all(venueIds.map(id => getVenueById(id).catch(() => null))),
  ]);
  const contentMap = new Map(
    contents.filter((c): c is Content => c !== null).map(c => [c.id, c])
  );
  const venueMap = new Map(
    venues.filter((v): v is Venue => v !== null).map(v => [v.id, v])
  );

  for (let i = 0; i < bookings.length; i++) {
    const show = showMap.get(raw[i].showId);
    if (!show) continue;
    bookings[i].show = show;
    bookings[i].contentId = show.contentId;
    bookings[i].venueId = show.venueId;
    const content = contentMap.get(show.contentId);
    if (content) bookings[i].content = content;
    const venue = venueMap.get(show.venueId);
    if (venue) bookings[i].venue = venue;
  }

  return bookings;
}

export async function getBookings(): Promise<Booking[]> {
  const list = await request<BackendBookingResponse[]>('/api/bookings', { auth: true });
  return enrichBookings(list);
}

/**
 * POST /api/bookings/{id}/cancel — cancels a booking the caller owns.
 * Returns the raw backend response so the caller can merge the updated
 * status into an already-enriched booking without losing show/content/venue
 * data. Throws ApiError on failure (404/403/409).
 */
export async function cancelBooking(id: string): Promise<BackendBookingResponse> {
  return request<BackendBookingResponse>(`/api/bookings/${id}/cancel`, {
    method: 'POST',
    auth: true,
  });
}

// ─── Payments ────────────────────────────────────────────────────────────────

// payment-service's own CreatePaymentRequest (POST /api/payments) —
// bookingId and idempotencyKey only. amount/currency/userId/provider are
// deliberately never sent: amount is computed server-side from the
// booking's own totalAmount, userId comes from the caller's JWT, and
// provider is a fixed server-side configuration (CreatePaymentRequest's own
// Javadoc). There is no "amount" field to get wrong on this end.
export interface CreatePaymentInput {
  bookingId: string;
  idempotencyKey: string;
}

export type PaymentStatus = 'CREATED' | 'PENDING' | 'SUCCESS' | 'FAILED' | 'EXPIRED' | 'CANCELLED';

// payment-service's own PaymentResponse.
export interface BackendPaymentResponse {
  id: string;
  bookingId: string;
  userId: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  provider: string;
  providerReference: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

/**
 * POST /api/payments — charges for an already-created booking (see
 * {@link createBooking}, called earlier in the flow; a payment can never be
 * created before its booking exists). On `status === 'SUCCESS'`,
 * payment-service confirms the booking with a direct, synchronous call to
 * booking-service's internal confirm endpoint *before this call returns* —
 * not through Kafka. (The service does also publish a `PaymentSucceeded`
 * Kafka event, but that is a separate, audit-only side channel consumed by
 * audit-service; it has no bearing on booking confirmation.) That
 * synchronous confirm can rarely still be in flight by the time this
 * resolves (e.g. a transient blip, retried by the backend's own
 * reconciliation sweep) — callers should re-fetch the booking with
 * {@link getBooking} rather than assume it is already CONFIRMED.
 *
 * `idempotencyKey` must stay the same across retries of the *same* logical
 * payment attempt (see its caller in BookingSummary.tsx for how a stable
 * one is derived) — reusing it safely replays the same attempt instead of
 * double-charging; a new booking naturally gets a new key.
 *
 * Throws {@link ApiError} on failure — notably `409 IDEMPOTENCY_KEY_CONFLICT`
 * (the key was already used for a different booking) and
 * `409 DUPLICATE_PAYMENT_FOR_BOOKING` (this booking already has a live
 * payment). The caller decides how to present each of these, same
 * convention as every other real call here.
 */
export async function createPayment(data: CreatePaymentInput): Promise<BackendPaymentResponse> {
  return request<BackendPaymentResponse>('/api/payments', {
    method: 'POST',
    auth: true,
    body: data,
  });
}

// ─── Auth ────────────────────────────────────────────────────────────────────

// Auth requests go to the API Gateway (BASE_URL, :8080), which routes
// /api/auth/** and /api/users/** to user-service. The frontend never calls a
// backend service directly.

// The JWT lives in localStorage — simple and fine for a local project. The
// trade-off: any script running on the page can read it (XSS). Passwords are
// never stored.
const TOKEN_KEY = 'eventtick.accessToken';

function getStoredToken(): string | null {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
}
function storeToken(token: string): void {
  try { localStorage.setItem(TOKEN_KEY, token); } catch { /* storage blocked: stay logged in for this page load only */ }
}
function clearToken(): void {
  try { localStorage.removeItem(TOKEN_KEY); } catch { /* nothing to clear */ }
}

/**
 * Thrown for any failed backend call. `message` is always safe to show to
 * the user: it is either the backend's own (deliberately generic) error
 * message for a 4xx, or a fixed message for network/server failures — never
 * a stack trace, and never anything containing a token or password.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  constructor(status: number, message: string, code?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

// Shape of the backend's ErrorResponse: { status, error, message, timestamp }
interface BackendError { error?: string; message?: string }

async function request<T>(
  path: string,
  options: { method?: 'GET' | 'POST' | 'PUT' | 'DELETE'; body?: unknown; auth?: boolean } = {},
): Promise<T> {
  const headers: Record<string, string> = {};
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  if (options.auth) {
    const token = getStoredToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }

  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.', 'NETWORK_ERROR');
  }

  if (!response.ok) {
    const err: BackendError | null = await response.json().catch(() => null);
    // 4xx: the backend's message is written to be user-safe (e.g. "Invalid
    // email or password."). 5xx: never show server detail.
    const message = response.status < 500 && err?.message
      ? err.message
      : response.status >= 500
        ? 'Something went wrong on our side. Please try again later.'
        : `Request failed (${response.status}).`;
    throw new ApiError(response.status, message, err?.error);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

// The backend's user shape (UserResponse). Only login and /me responses are
// mapped: registration's response has null createdAt/updatedAt and isn't used.
interface BackendUser {
  id: string;
  name: string;
  email: string;
  role: 'CUSTOMER' | 'ADMIN';
  planId: string;
  planName: string;
  createdAt: string;
}

const KNOWN_PLANS: readonly SubscriptionPlan[] = ['FREE', 'PRO', 'PREMIUM', 'VIP'];

// Backend plan names are "Free" / "Pro" / "Premium"; the frontend's
// SubscriptionPlan is upper case. Display only — never used for access control.
function toSubscriptionPlan(planName: string): SubscriptionPlan {
  const upper = planName.toUpperCase() as SubscriptionPlan;
  return KNOWN_PLANS.includes(upper) ? upper : 'FREE';
}

function toUser(b: BackendUser): User {
  return {
    id: b.id,
    name: b.name,
    email: b.email,
    plan: toSubscriptionPlan(b.planName),
    role: b.role,
    createdAt: b.createdAt,
  };
}

export interface LoginInput { email: string; password: string; }
export interface SignupInput { name: string; email: string; password: string; }

export async function login(data: LoginInput): Promise<User> {
  const res = await request<{ accessToken: string; user: BackendUser }>(
    '/api/auth/login', { method: 'POST', body: data });
  storeToken(res.accessToken);
  return toUser(res.user);
}

/**
 * Registration returns no token, so a successful signup is followed by an
 * immediate login with the same credentials — the caller gets a logged-in
 * user, same as `login`.
 */
export async function signup(data: SignupInput): Promise<User> {
  await request('/api/auth/register', {
    method: 'POST',
    body: { name: data.name, email: data.email, password: data.password },
  });
  try {
    return await login({ email: data.email, password: data.password });
  } catch {
    throw new ApiError(0, 'Your account was created, but signing in failed. Please log in.', 'LOGIN_AFTER_SIGNUP_FAILED');
  }
}

/** Stateless JWT: there is no backend logout endpoint — dropping the token is the logout. */
export async function logout(): Promise<void> {
  clearToken();
}

/**
 * Restores the session from a stored token. Never throws: no token, an
 * expired/invalid token (401 — cleared so it isn't retried forever), or an
 * unreachable server all resolve to `null`. A network failure deliberately
 * keeps the token, so a temporarily-down backend doesn't log the user out.
 */
export async function getCurrentUser(): Promise<User | null> {
  if (!getStoredToken()) return null;
  try {
    return toUser(await request<BackendUser>('/api/users/me', { auth: true }));
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) clearToken();
    return null;
  }
}

// ─── Plans ───────────────────────────────────────────────────────────────────

export async function getPlans(): Promise<Plan[]> {
  await delay(300);
  return plans;
}

export async function subscribeToPlan(_planId: string): Promise<boolean> {
  await delay(500);
  return true;
}

// ─── Admin ───────────────────────────────────────────────────────────────────

export async function getAdminStats(): Promise<AdminStats> {
  await delay(400);
  return adminStats;
}

/**
 * GET /api/admin/bookings — paginated admin booking list. The backend
 * returns a Spring Page<BookingResponse>; we extract the content array.
 */
interface SpringPage<T> { content: T[]; totalElements: number; totalPages: number; number: number; size: number }

export async function getAdminBookings(): Promise<Booking[]> {
  const page = await request<SpringPage<BackendBookingResponse>>(
    '/api/admin/bookings?size=20&sort=createdAt,desc', { auth: true });
  return enrichBookings(page.content);
}

// Phase 19: real dynamic rate-limit policy CRUD — replaces the mock
// implementation. These hit the Gateway's own admin endpoints, not a
// downstream service.

export interface DynamicPolicyResponse {
  id: string;
  category: string;
  tier: string;
  replenishRate: number;
  burstCapacity: number;
  requestedTokens: number;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export async function getRateLimitPolicies(): Promise<DynamicPolicyResponse[]> {
  return request<DynamicPolicyResponse[]>('/api/admin/rate-limits/policies', { auth: true });
}

export async function createRateLimitPolicy(policy: {
  category: string; tier: string;
  replenishRate: number; burstCapacity: number; requestedTokens: number;
}): Promise<DynamicPolicyResponse> {
  return request<DynamicPolicyResponse>('/api/admin/rate-limits/policies', {
    method: 'POST', body: policy, auth: true,
  });
}

export async function updateRateLimitPolicy(id: string, policy: {
  replenishRate: number; burstCapacity: number; requestedTokens: number; enabled: boolean;
}): Promise<DynamicPolicyResponse> {
  return request<DynamicPolicyResponse>(`/api/admin/rate-limits/policies/${id}`, {
    method: 'PUT', body: policy, auth: true,
  });
}

export async function deleteRateLimitPolicy(id: string): Promise<void> {
  return request<void>(`/api/admin/rate-limits/policies/${id}`, {
    method: 'DELETE', auth: true,
  });
}

// FR-40 (Phase 13): the real Gateway Rate-Limit Visibility endpoint — a
// LOCAL Gateway endpoint (not proxied to a downstream service), still
// reached the same way as every other admin call: through the Gateway
// (BASE_URL), with the caller's JWT (auth: true). Kept separate from the
// mock getRateLimitPolicies() above, the same pattern
// getAdminOverviewStats() already established alongside the mock
// getAdminStats().
export async function getRateLimitStats(): Promise<RateLimitStatsResponse> {
  return request<RateLimitStatsResponse>('/api/admin/rate-limits/stats', { auth: true });
}

// FR-36 (Phase 13): real Admin Overview statistics — three independent
// backend calls, each to the service that owns that figure, combined
// client-side. No aggregation endpoint/admin-service: this is exactly
// what the backend's own FR-36 requirement ("each figure sourced from the
// service that owns it, never duplicated") pushes the composition to.
interface UserStatsResponse { totalUsers: number }
interface CatalogStatsResponse { totalContent: number; totalShows: number; totalVenues: number }
interface BookingStatsResponse { totalBookings: number }

export async function getAdminOverviewStats(): Promise<AdminOverviewStats> {
  const [userStats, catalogStats, bookingStats] = await Promise.all([
    request<UserStatsResponse>('/api/admin/users/stats', { auth: true }),
    request<CatalogStatsResponse>('/api/admin/content/stats', { auth: true }),
    request<BookingStatsResponse>('/api/admin/bookings/stats', { auth: true }),
  ]);
  return {
    totalUsers: userStats.totalUsers,
    totalContent: catalogStats.totalContent,
    totalShows: catalogStats.totalShows,
    totalVenues: catalogStats.totalVenues,
    totalBookings: bookingStats.totalBookings,
  };
}

// ─── Search ──────────────────────────────────────────────────────────────────

/**
 * Search real catalog content (GET /api/catalog/content) with client-side
 * filtering on title, genre, and language — the only text fields
 * catalog-service's Content entity actually carries.
 */
export async function searchEvents(query: string): Promise<SearchResult[]> {
  if (!query.trim()) return [];
  const q = query.toLowerCase();
  const content = await request<BackendContent[]>('/api/catalog/content', { auth: true });
  return content
    .filter(c =>
      c.title.toLowerCase().includes(q) ||
      (c.genre ?? '').toLowerCase().includes(q) ||
      (c.language ?? '').toLowerCase().includes(q) ||
      (c.description ?? '').toLowerCase().includes(q)
    )
    .slice(0, 12)
    .map(c => ({
      id: c.id,
      type: c.type,
      title: c.title,
      subtitle: [c.genre, c.language].filter(Boolean).join(' • '),
      image: PLACEHOLDER_IMAGE,
    }));
}

