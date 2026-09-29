// ─────────────────────────────────────────────────────────────────────────────
// Eventtick API Service Layer
//
// Authentication (login / signup / logout / getCurrentUser) is REAL: it
// goes through the API Gateway to user-service (see "Auth" below). Everything else
// still returns mock data with simulated async delay, until its own
// backend integration phase.
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
  movies, sportsEvents, concerts, theatreEvents, generalEvents,
  allEvents, venues, shows, mockBookings, mockUser, plans,
  adminStats, rateLimitPolicies
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

// ─── Event / Content ─────────────────────────────────────────────────────────

export async function getEvents(
  type?: ContentType,
  filters?: EventFilters
): Promise<Content[]> {
  await delay();
  let data: Content[];
  switch (type) {
    case 'MOVIE':       data = [...movies]; break;
    case 'SPORTS_MATCH': data = [...sportsEvents]; break;
    case 'CONCERT':     data = [...concerts]; break;
    case 'THEATRE':     data = [...theatreEvents]; break;
    case 'EVENT':       data = [...generalEvents]; break;
    default:            data = [...allEvents];
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

export async function getEventById(id: string): Promise<Content | null> {
  await delay(300);
  return allEvents.find(e => e.id === id) || null;
}

export async function getTrendingEvents(): Promise<Content[]> {
  await delay(350);
  return allEvents.filter(e => e.trending).slice(0, 8);
}

export async function getFeaturedEvents(): Promise<Content[]> {
  await delay(300);
  return allEvents.filter(e => e.featured).slice(0, 5);
}

// ─── Venues ──────────────────────────────────────────────────────────────────

export async function getVenueById(id: string): Promise<Venue | null> {
  await delay(200);
  return venues.find(v => v.id === id) || null;
}

export async function getVenuesByCity(city: string): Promise<Venue[]> {
  await delay(200);
  return venues.filter(v => v.city === city);
}

// ─── Shows ───────────────────────────────────────────────────────────────────

export async function getShowsByEvent(contentId: string): Promise<Show[]> {
  await delay(350);
  return shows.filter(s => s.contentId === contentId);
}

export async function getShowById(showId: string): Promise<Show | null> {
  await delay(200);
  return shows.find(s => s.id === showId) || null;
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

export interface CreateBookingInput {
  showId: string;
  contentId: string;
  venueId: string;
  seats: string[];
  category: string;
  ticketPrice: number;
  convenienceFee: number;
  totalAmount: number;
}

export async function createBooking(data: CreateBookingInput): Promise<Booking> {
  await delay(600);
  const newBooking: Booking = {
    id: `b${Date.now()}`,
    bookingRef: `EVT-${new Date().getFullYear()}-${Math.floor(Math.random() * 900000 + 100000)}`,
    userId: mockUser.id,
    ...data,
    category: data.category as import('../types').SeatCategory,
    status: 'CONFIRMED',
    createdAt: new Date().toISOString(),
    content: allEvents.find(e => e.id === data.contentId),
    venue: venues.find(v => v.id === data.venueId),
    show: shows.find(s => s.id === data.showId),
  };
  return newBooking;
}

export async function getBookings(userId?: string): Promise<Booking[]> {
  await delay(400);
  return mockBookings.filter(b => !userId || b.userId === userId);
}

export async function getBookingById(id: string): Promise<Booking | null> {
  await delay(300);
  return mockBookings.find(b => b.id === id) || null;
}

export async function cancelBooking(_id: string): Promise<boolean> {
  await delay(500);
  return true;
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
  options: { method?: 'GET' | 'POST'; body?: unknown; auth?: boolean } = {},
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

export async function getRateLimitPolicies() {
  await delay(300);
  return rateLimitPolicies;
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

export async function searchEvents(query: string): Promise<SearchResult[]> {
  await delay(300);
  if (!query.trim()) return [];
  const q = query.toLowerCase();
  return allEvents
    .filter(e =>
      e.title.toLowerCase().includes(q) ||
      (e.artist || '').toLowerCase().includes(q) ||
      (e.genre || '').toLowerCase().includes(q) ||
      (e.teams || []).some(t => t.toLowerCase().includes(q))
    )
    .slice(0, 12)
    .map(e => ({
      id: e.id,
      type: e.type,
      title: e.title,
      subtitle: [e.city, e.genre, e.sport].filter(Boolean).join(' • '),
      image: e.image,
    }));
}

