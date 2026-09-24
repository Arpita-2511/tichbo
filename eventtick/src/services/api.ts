// ─────────────────────────────────────────────────────────────────────────────
// Eventtick API Service Layer
//
// Authentication (login / signup / logout / getCurrentUser) is REAL: it
// talks to user-service over HTTP (see "Auth" below). Everything else
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
  AdminStats, SearchResult, EventFilters, ContentType,
  SeatSection, SubscriptionPlan
} from '../types';

import {
  movies, sportsEvents, concerts, theatreEvents, generalEvents,
  allEvents, venues, shows, mockBookings, mockUser, plans,
  adminStats, generateSeatSections, rateLimitPolicies
} from '../data/mockData';

// ─── API Client Configuration ─────────────────────────────────────────────────
// When connecting to the real backend, update BASE_URL and add auth headers.

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

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

export async function getSeatMap(showId: string): Promise<SeatSection[]> {
  await delay(500);  // seat maps may take longer in real system
  return generateSeatSections(showId);
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

// Talks to user-service directly. There is no API Gateway yet; once there is,
// this collapses into BASE_URL. Override with VITE_USER_SERVICE_URL.
const USER_SERVICE_URL = import.meta.env.VITE_USER_SERVICE_URL || 'http://localhost:8081';

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
    response = await fetch(`${USER_SERVICE_URL}${path}`, {
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

