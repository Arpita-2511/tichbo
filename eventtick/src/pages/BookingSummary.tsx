import { useState, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, MapPin, Calendar, Clock, Ticket, CreditCard, Smartphone, CheckCircle, Timer } from 'lucide-react';
import type { Content, Show, Venue, Seat, Booking } from '../types';
import { createBooking, createPayment, getBooking, ApiError, type BackendBookingResponse } from '../services/api';
import { useApp } from '../context/AppContext';

interface SummaryState {
  event: Content;
  show: Show;
  venue: Venue;
  seats: Seat[];
  ticketPrice: number;
  convenienceFee: number;
  totalAmount: number;
  holdExpiresAt?: string;
}

const paymentMethods = [
  { id: 'upi', label: 'UPI', icon: <Smartphone size={18} /> },
  { id: 'card', label: 'Credit / Debit Card', icon: <CreditCard size={18} /> },
  { id: 'netbanking', label: 'Net Banking', icon: <CreditCard size={18} /> },
];

// payment-service's own terminal, non-SUCCESS statuses, in user-safe
// language. CREATED/PENDING are not expected back from a single
// synchronous POST /api/payments response (MockPaymentProvider resolves
// immediately to SUCCESS or FAILED) but are covered for completeness.
const PAYMENT_FAILURE_MESSAGES: Partial<Record<string, string>> = {
  FAILED: 'Your payment could not be processed. Please try again.',
  EXPIRED: 'Your payment session expired. Please try again.',
  CANCELLED: 'Your payment was cancelled.',
};

function describeError(err: unknown): string {
  if (err instanceof ApiError) {
    switch (err.code) {
      case 'INVALID_SEAT_STATE':
        return 'Your seat hold has expired or is no longer valid. Please go back and select your seats again.';
      case 'DUPLICATE_PAYMENT_FOR_BOOKING':
        return 'A payment for this booking is already being processed. Please wait a moment and check your bookings.';
      default:
        return err.message;
    }
  }
  return 'Could not complete your booking. Please try again.';
}

export default function BookingSummary() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useApp();
  const state = location.state as SummaryState | null;

  const [selectedPayment, setSelectedPayment] = useState('upi');
  const [loading, setLoading] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Real countdown based on backend hold expiration
  const [holdSecondsLeft, setHoldSecondsLeft] = useState<number | null>(() => {
    if (!state?.holdExpiresAt) return null;
    return Math.max(0, Math.floor((new Date(state.holdExpiresAt).getTime() - Date.now()) / 1000));
  });

  useEffect(() => {
    if (holdSecondsLeft === null) return;
    if (holdSecondsLeft <= 0) return;
    const interval = setInterval(() => {
      setHoldSecondsLeft(prev => {
        if (prev === null || prev <= 0) return 0;
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [holdSecondsLeft !== null && holdSecondsLeft > 0]);

  const holdExpired = holdSecondsLeft !== null && holdSecondsLeft <= 0;
  const formatTimer = (s: number) => `${Math.floor(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}`;

  if (!state) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center gap-4">
        <p className="text-text-muted">No booking data found.</p>
        <button onClick={() => navigate('/')} className="btn-primary">Go Home</button>
      </div>
    );
  }

  const { event, show, venue, seats, ticketPrice, convenienceFee, totalAmount } = state;

  const handleProceed = async () => {
    if (!agreed) return;
    if (!user) return; // not logged in — the route/header already gate this in practice

    setError(null);
    setLoading(true);
    try {
      // POST /api/bookings — real booking-service call. The seats here
      // must already be HELD by this same user (SeatSelection's own real
      // holdSeats call, earlier in this flow) — this does not itself hold
      // anything. See createBooking's own comment in api.ts for the exact
      // request/response contract. status comes back PENDING; payment is
      // what moves it to CONFIRMED, below.
      const bookingResponse = await createBooking({
        userId: user.id,
        showId: show.id,
        showSeatIds: seats.map(s => s.id),
      });

      // Idempotency key is derived from the real booking id, not randomly
      // generated and stored in component state — so it is naturally
      // stable across re-renders, double-clicks (blocked anyway by
      // `loading` disabling this button) and remounts, and a retry of
      // this exact payment attempt always reuses it. A genuinely new
      // attempt only ever happens for a new booking (a new hold, after
      // going back to seat selection), which gets its own new key for
      // free. See createPayment's own comment in api.ts.
      const idempotencyKey = `booking-${bookingResponse.bookingId}-payment`;

      // POST /api/payments — real payment-service call, strictly after the
      // booking above exists. Only bookingId + idempotencyKey are sent:
      // amount/currency/userId/provider are never supplied by the
      // frontend (see createPayment's own comment) — the backend computes
      // and owns all of them.
      const paymentResponse = await createPayment({
        bookingId: bookingResponse.bookingId,
        idempotencyKey,
      });

      if (paymentResponse.status !== 'SUCCESS') {
        setError(PAYMENT_FAILURE_MESSAGES[paymentResponse.status] ?? 'Your payment was not completed. Please try again.');
        setLoading(false);
        return;
      }

      // A SUCCESS payment response means payment-service already confirmed
      // the booking via a direct, synchronous call to booking-service
      // (not Kafka — see createPayment's own comment) before this request
      // returned. That confirmation can rarely still be catching up (a
      // transient blip, retried by the backend's own reconciliation
      // sweep), so re-fetch a few times, bounded — never forever — rather
      // than assume CONFIRMED. If the booking still reads PENDING after
      // this, it is shown honestly as PENDING (see Ticket.tsx) rather than
      // faked as CONFIRMED.
      let latestBooking: BackendBookingResponse = bookingResponse;
      for (let attempt = 0; attempt < 3; attempt++) {
        try {
          latestBooking = await getBooking(bookingResponse.bookingId);
        } catch {
          // Best-effort refresh; the payment already succeeded, so fall
          // back to the last known booking state rather than losing it.
          break;
        }
        if (latestBooking.status !== 'PENDING') break;
        if (attempt < 2) await new Promise(resolve => setTimeout(resolve, 1000));
      }

      // Build the Booking object the rest of the frontend (the Ticket
      // page) expects, from the REAL backend response — id, userId,
      // showId, status, and totalAmount all come from `latestBooking`,
      // never fabricated.
      //
      // `totalAmount` below is the backend's own authoritative sum of the
      // booked seats' real prices. It does not include `convenienceFee` (a
      // frontend-only concept the real booking model has no field for), so
      // it may legitimately read slightly lower than the "Confirm & Pay"
      // total shown on this page.
      const booking: Booking = {
        id: latestBooking.bookingId,
        // No separate human-readable booking reference exists on the
        // backend yet — reuse the real booking id rather than invent one.
        bookingRef: latestBooking.bookingId,
        userId: latestBooking.userId,
        showId: latestBooking.showId,
        contentId: event.id,
        venueId: venue.id,
        seats: seats.map(s => s.label),
        category: seats[0]?.category || 'REGULAR',
        ticketPrice,
        convenienceFee,
        totalAmount: latestBooking.totalAmount,
        status: latestBooking.status,
        // createdAt is genuinely null on the create-booking response (see
        // createBooking's own comment) and may still be null here if the
        // bounded refresh above never got a fresher read — the frontend
        // Booking type requires a string, so this falls back to "now".
        createdAt: latestBooking.createdAt ?? new Date().toISOString(),
        content: event,
        venue,
        show,
      };
      navigate(`/ticket/${booking.id}`, { state: { booking } });
    } catch (err) {
      setError(describeError(err));
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen max-w-2xl mx-auto px-4 sm:px-6 py-8">
      {/* Back */}
      <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-text-secondary hover:text-text-primary mb-6 text-sm transition-colors">
        <ArrowLeft size={16} /> Back to seat selection
      </button>

      {holdSecondsLeft !== null && (
        <div className={`flex items-center gap-2 mb-4 px-3 py-2 rounded-lg text-sm ${
          holdExpired
            ? 'bg-error/10 text-error border border-error/30'
            : holdSecondsLeft <= 60
              ? 'bg-warning/10 text-warning border border-warning/30'
              : 'bg-accent/10 text-accent-lighter border border-accent/30'
        }`}>
          <Timer size={14} className={holdExpired ? '' : 'animate-pulse'} />
          {holdExpired
            ? 'Your seat hold has expired. Please go back and select seats again.'
            : `Seat hold expires in ${formatTimer(holdSecondsLeft)}`}
        </div>
      )}

      <h1 className="text-2xl font-bold text-text-primary mb-6">Booking Summary</h1>

      {/* Event card */}
      <div className="card mb-5 overflow-hidden">
        <div className="flex gap-0">
          <img src={event.image} alt={event.title} className="w-28 h-28 object-cover flex-shrink-0" />
          <div className="p-4 flex-1">
            <h2 className="text-text-primary font-bold">{event.title}</h2>
            <div className="space-y-1 mt-2">
              <div className="flex items-center gap-2 text-text-muted text-xs">
                <MapPin size={12} /> {venue.name}, {venue.city}
              </div>
              <div className="flex items-center gap-2 text-text-muted text-xs">
                <Calendar size={12} />
                {new Date(show.date).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'long', year: 'numeric' })}
              </div>
              <div className="flex items-center gap-2 text-text-muted text-xs">
                <Clock size={12} /> {show.timeLabel}
              </div>
            </div>
          </div>
        </div>

        {/* Dashed divider */}
        <div className="border-t border-dashed border-border mx-4" />

        {/* Seats */}
        <div className="px-4 py-3 flex items-start gap-3">
          <Ticket size={14} className="text-accent-lighter mt-0.5 flex-shrink-0" />
          <div>
            <p className="text-text-muted text-xs mb-1">Selected Seats ({seats.length})</p>
            <div className="flex flex-wrap gap-1.5">
              {seats.map(s => (
                <span key={s.id} className="font-mono text-xs bg-accent/20 text-accent-lighter border border-accent/30 px-2 py-0.5 rounded-md">
                  {s.label}
                </span>
              ))}
            </div>
            <p className="text-text-muted text-xs mt-1">{seats[0]?.category} • {seats[0]?.price && `₹${seats[0].price.toLocaleString('en-IN')} each`}</p>
          </div>
        </div>
      </div>

      {/* Price breakdown */}
      <div className="bg-bg-secondary border border-border rounded-xl p-5 mb-5">
        <h3 className="text-text-primary font-semibold mb-4">Price Breakdown</h3>
        <div className="space-y-2 text-sm">
          <div className="flex justify-between">
            <span className="text-text-secondary">Ticket Price × {seats.length}</span>
            <span className="text-text-primary">₹{ticketPrice.toLocaleString('en-IN')}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-text-secondary">Convenience Fee</span>
            <span className="text-text-primary">₹{convenienceFee.toLocaleString('en-IN')}</span>
          </div>
          <div className="border-t border-dashed border-border pt-2 flex justify-between font-bold">
            <span className="text-text-primary">Total Amount</span>
            <span className="text-accent-lighter text-lg">₹{totalAmount.toLocaleString('en-IN')}</span>
          </div>
        </div>
      </div>

      {/* Payment method */}
      <div className="bg-bg-secondary border border-border rounded-xl p-5 mb-5">
        <h3 className="text-text-primary font-semibold mb-4">Payment Method</h3>
        <div className="space-y-2">
          {paymentMethods.map(pm => (
            <label
              key={pm.id}
              className={`flex items-center gap-3 p-3 rounded-xl border cursor-pointer transition-colors ${
                selectedPayment === pm.id
                  ? 'border-accent bg-accent/10'
                  : 'border-border bg-bg-tertiary hover:border-border-light'
              }`}
            >
              <input
                type="radio"
                value={pm.id}
                checked={selectedPayment === pm.id}
                onChange={() => setSelectedPayment(pm.id)}
                className="accent-accent"
              />
              <span className={selectedPayment === pm.id ? 'text-accent-lighter' : 'text-text-muted'}>
                {pm.icon}
              </span>
              <span className={`text-sm font-medium ${selectedPayment === pm.id ? 'text-accent-lighter' : 'text-text-secondary'}`}>
                {pm.label}
              </span>
            </label>
          ))}
        </div>
      </div>

      {/* Terms checkbox */}
      <label className="flex items-start gap-3 mb-6 cursor-pointer">
        <input
          type="checkbox"
          checked={agreed}
          onChange={e => setAgreed(e.target.checked)}
          className="mt-0.5 accent-accent"
        />
        <span className="text-text-muted text-xs leading-relaxed">
          I agree to the <a href="#" className="text-accent-lighter hover:underline">Terms & Conditions</a> and <a href="#" className="text-accent-lighter hover:underline">Cancellation Policy</a>. I understand that tickets once booked cannot be refunded.
        </span>
      </label>

      {error && <p className="text-error text-sm text-center mb-4">{error}</p>}

      {/* CTA */}
      <button
        onClick={handleProceed}
        disabled={!agreed || loading || holdExpired}
        className={`w-full btn-primary text-base py-4 flex items-center justify-center gap-2 ${!agreed || holdExpired ? 'opacity-50 cursor-not-allowed' : ''}`}
      >
        {loading ? (
          <><div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" /> Processing...</>
        ) : (
          <><CheckCircle size={18} /> Confirm & Pay ₹{totalAmount.toLocaleString('en-IN')}</>
        )}
      </button>

      <p className="text-text-muted text-xs text-center mt-3">
        Secured by 256-bit SSL encryption. Your payment details are never stored.
      </p>
    </div>
  );
}

