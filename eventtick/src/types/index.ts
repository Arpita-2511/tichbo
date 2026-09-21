// ─────────────────────────────────────────────────────────────────────────────
// Eventtick — TypeScript type definitions
// These types mirror the backend domain models so the API layer is type-safe.
// ─────────────────────────────────────────────────────────────────────────────

export type ContentType = 'MOVIE' | 'SPORTS_MATCH' | 'CONCERT' | 'THEATRE' | 'EVENT';

export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'FAILED';

export type SeatStatus = 'AVAILABLE' | 'SELECTED' | 'BOOKED' | 'UNAVAILABLE';

export type SeatCategory = 'VIP' | 'PREMIUM' | 'REGULAR';

export type SubscriptionPlan = 'FREE' | 'PREMIUM' | 'VIP';

// ─── Content / Event ─────────────────────────────────────────────────────────

export interface Content {
  id: string;
  type: ContentType;
  title: string;
  description: string;
  image: string;
  bannerImage?: string;
  language?: string;
  duration?: number;           // minutes
  genre?: string;
  rating?: number;             // 0–10
  ratingCount?: number;
  certificate?: string;        // U, UA, A
  cast?: string[];
  director?: string;
  releaseDate?: string;        // ISO date
  isReleased?: boolean;
  sport?: string;
  teams?: string[];
  artist?: string;
  venue?: string;
  tags?: string[];
  trending?: boolean;
  featured?: boolean;
  price?: number;              // starting price in INR
  city?: string;
}

// ─── Venue ───────────────────────────────────────────────────────────────────

export interface Venue {
  id: string;
  name: string;
  address: string;
  city: string;
  state: string;
  pincode: string;
  mapUrl?: string;
  amenities?: string[];
  totalCapacity?: number;
}

// ─── Show ────────────────────────────────────────────────────────────────────

export interface Show {
  id: string;
  contentId: string;
  venueId: string;
  startTime: string;           // ISO datetime
  endTime: string;
  date: string;                // YYYY-MM-DD
  timeLabel: string;           // e.g. "7:30 PM"
  language?: string;
  format?: string;             // 2D, 3D, IMAX, 4DX
  available: boolean;
  seatsAvailable?: number;
  totalSeats?: number;
}

// ─── Seat ────────────────────────────────────────────────────────────────────

export interface Seat {
  id: string;
  showId: string;
  row: string;
  number: number;
  label: string;               // e.g. "A4"
  category: SeatCategory;
  price: number;
  status: SeatStatus;
}

export interface SeatRow {
  row: string;
  category: SeatCategory;
  seats: Seat[];
}

export interface SeatSection {
  category: SeatCategory;
  price: number;
  rows: SeatRow[];
}

// ─── Booking ─────────────────────────────────────────────────────────────────

export interface Booking {
  id: string;
  bookingRef: string;
  userId: string;
  showId: string;
  contentId: string;
  venueId: string;
  seats: string[];             // seat labels e.g. ["A4", "A5"]
  category: SeatCategory;
  ticketPrice: number;
  convenienceFee: number;
  totalAmount: number;
  status: BookingStatus;
  createdAt: string;
  content?: Content;
  venue?: Venue;
  show?: Show;
}

// ─── User ────────────────────────────────────────────────────────────────────

export interface User {
  id: string;
  name: string;
  email: string;
  phone?: string;
  city?: string;
  avatar?: string;
  plan: SubscriptionPlan;
  createdAt: string;
  savedEvents?: string[];
}

// ─── Subscription Plan ───────────────────────────────────────────────────────

export interface Plan {
  id: SubscriptionPlan;
  name: string;
  price: number;
  period: string;
  features: string[];
  limits: RateLimitPolicy[];
  highlighted?: boolean;
}

// ─── Rate Limiting ───────────────────────────────────────────────────────────

export interface RateLimitPolicy {
  plan: SubscriptionPlan;
  route: string;
  limit: number;
  window: string;
}

// ─── Admin ───────────────────────────────────────────────────────────────────

export interface AdminStats {
  totalUsers: number;
  totalBookings: number;
  todayRevenue: number;
  activeEvents: number;
  bookingsTrend: { date: string; count: number }[];
  revenueTrend: { date: string; amount: number }[];
  bookingsByCategory: { category: string; count: number }[];
}

// ─── Search ──────────────────────────────────────────────────────────────────

export interface SearchResult {
  id: string;
  type: ContentType;
  title: string;
  subtitle: string;
  image: string;
}

// ─── Filters ─────────────────────────────────────────────────────────────────

export interface EventFilters {
  language?: string;
  genre?: string;
  format?: string;
  minRating?: number;
  date?: string;
  city?: string;
  sport?: string;
  minPrice?: number;
  maxPrice?: number;
}

