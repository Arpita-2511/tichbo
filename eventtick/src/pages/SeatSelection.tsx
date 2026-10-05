import { useEffect, useState } from 'react';
import { useParams, useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, Timer, AlertTriangle } from 'lucide-react';
import { getSeatMap, getEventById, getShowById, getVenueById, holdSeats, ApiError } from '../services/api';
import { useApp } from '../context/AppContext';
import type { Content, Show, Venue, Seat, SeatSection } from '../types';
import SeatMap from '../components/booking/SeatMap';
import PriceBreakdown, { calculatePricing } from '../components/booking/PriceBreakdown';
import Loading from '../components/common/Loading';

export default function SeatSelection() {
  const { showId } = useParams<{ showId: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useApp();

  // Prefer state passed from EventDetails, fall back to fetching
  const stateData = location.state as { event?: Content; show?: Show; venue?: Venue } | null;

  const [event, setEvent] = useState<Content | null>(stateData?.event || null);
  const [show, setShow] = useState<Show | null>(stateData?.show || null);
  const [venue, setVenue] = useState<Venue | null>(stateData?.venue || null);
  const [sections, setSections] = useState<SeatSection[]>([]);
  const [selectedSeats, setSelectedSeats] = useState<Seat[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  // Visual countdown during seat selection (before any hold exists).
  // The real backend hold happens on "Continue" click (handleContinue),
  // and its real expiration is shown in BookingSummary via holdExpiresAt.
  const [timer, setTimer] = useState(600);
  const [continuing, setContinuing] = useState(false);
  const [holdError, setHoldError] = useState<string | null>(null);

  useEffect(() => {
    if (!showId) return;
    const loadData = async () => {
      try {
        const [seatSections, fetchedShow] = await Promise.all([
          getSeatMap(showId),
          !show ? getShowById(showId) : Promise.resolve(show),
        ]);
        setSections(seatSections);
        if (fetchedShow && !show) {
          setShow(fetchedShow);
          const [ev, vn] = await Promise.all([
            !event ? getEventById(fetchedShow.contentId) : Promise.resolve(event),
            !venue ? getVenueById(fetchedShow.venueId) : Promise.resolve(venue),
          ]);
          setEvent(ev);
          setVenue(vn);
        }
      } catch (err) {
        setLoadError(err instanceof ApiError ? err.message : 'Could not load this show. Please try again.');
      } finally {
        setLoading(false);
      }
    };
    loadData();
  }, [showId]);

  // Seat lock countdown
  useEffect(() => {
    const interval = setInterval(() => setTimer(t => Math.max(0, t - 1)), 1000);
    return () => clearInterval(interval);
  }, []);

  const handleSeatToggle = (seat: Seat) => {
    setSelectedSeats(prev =>
      prev.find(s => s.id === seat.id)
        ? prev.filter(s => s.id !== seat.id)
        : [...prev, seat]
    );
  };

  const handleContinue = async () => {
    if (!selectedSeats.length || !event || !show || !venue) return;
    if (!showId) return;
    if (!user) return; // not logged in — the route/header already gate this in practice

    setHoldError(null);
    setContinuing(true);
    try {
      const showSeatIds = selectedSeats.map(seat => seat.id);
      const holdResponse = await holdSeats(showId, { userId: user.id, showSeatIds });

      // Only navigate once the backend has actually confirmed the hold.
      const { ticketTotal, convenienceFee, total } = calculatePricing(selectedSeats);
      // Pass booking details via location state (no URL params for sensitive data)
      navigate(`/booking/new/summary`, {
        state: {
          event, show, venue,
          seats: selectedSeats,
          ticketPrice: ticketTotal,
          convenienceFee,
          totalAmount: total,
          holdExpiresAt: holdResponse.holdExpiresAt,
        }
      });
    } catch (err) {
      // 409 INVALID_SEAT_STATE means a selected seat stopped being
      // AVAILABLE between the seat map loading and Continue being
      // clicked — the backend's own message names the show-seat by id,
      // which isn't something to show a customer, so it's replaced with
      // a plain-language one. Any other ApiError's own message is already
      // written to be user-safe (see ApiError's own contract in api.ts).
      const message = err instanceof ApiError && err.code === 'INVALID_SEAT_STATE'
        ? 'One or more selected seats are no longer available. Please select again.'
        : err instanceof ApiError
          ? err.message
          : 'Could not hold your seats. Please try again.';
      setHoldError(message);
      setSelectedSeats([]);
      // The failed hold means at least one seat's real availability has
      // moved on from what's currently shown — refresh from the backend
      // rather than leaving a stale map displayed.
      try {
        setSections(await getSeatMap(showId));
      } catch {
        // Best-effort refresh; the error banner above already tells the
        // user what to do (select again) even if this particular refetch
        // fails too.
      }
    } finally {
      setContinuing(false);
    }
  };

  const formatTimer = (s: number) => `${Math.floor(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}`;

  if (loading) return <div className="min-h-screen flex items-center justify-center"><Loading /></div>;
  if (loadError) return (
    <div className="min-h-screen flex flex-col items-center justify-center gap-3 text-text-muted px-4 text-center">
      <AlertTriangle size={24} className="text-warning" />
      <p>{loadError}</p>
      <button onClick={() => navigate(-1)} className="text-accent">Go Back</button>
    </div>
  );

  return (
    <div className="min-h-screen">
      {/* Top bar */}
      <div className="sticky top-16 z-40 bg-bg-secondary border-b border-border">
        <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-3 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button onClick={() => navigate(-1)} className="p-1.5 rounded-lg hover:bg-bg-tertiary text-text-secondary transition-colors">
              <ArrowLeft size={18} />
            </button>
            <div>
              <p className="text-text-primary font-semibold text-sm">{event?.title}</p>
              <p className="text-text-muted text-xs">{venue?.name} • {show?.date} • {show?.timeLabel}</p>
            </div>
          </div>
          {selectedSeats.length > 0 && (
            <div className="flex items-center gap-2 text-warning text-xs">
              <Timer size={13} className="animate-pulse" />
              <span>Seats held: {formatTimer(timer)}</span>
            </div>
          )}
        </div>
      </div>

      <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-6">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Seat Map */}
          <div className="lg:col-span-2">
            <div className="bg-bg-secondary border border-border rounded-xl p-4 sm:p-6 overflow-x-auto">
              <h2 className="text-text-primary font-semibold mb-6 text-center">Select Your Seats</h2>
              {holdError && <p className="text-error text-sm text-center mb-4">{holdError}</p>}
              <SeatMap
                sections={sections}
                selectedSeats={selectedSeats}
                onSeatToggle={handleSeatToggle}
                maxSeats={10}
              />
            </div>
          </div>

          {/* Booking summary — desktop */}
          <div className="hidden lg:block">
            <PriceBreakdown
              selectedSeats={selectedSeats}
              content={event}
              show={show}
              venue={venue}
              onContinue={handleContinue}
              isLoading={continuing}
            />
          </div>
        </div>
      </div>

      {/* Mobile bottom summary */}
      {selectedSeats.length > 0 && (
        <div className="lg:hidden fixed bottom-0 left-0 right-0 bg-bg-secondary border-t border-border p-4 z-40">
          <div className="flex items-center justify-between max-w-[1400px] mx-auto">
            <div>
              <p className="text-text-primary font-bold text-lg">
                ₹{calculatePricing(selectedSeats).total.toLocaleString('en-IN')}
              </p>
              <p className="text-text-muted text-xs">{selectedSeats.length} seat{selectedSeats.length !== 1 ? 's' : ''} selected</p>
            </div>
            <button
              onClick={handleContinue}
              disabled={continuing}
              className="btn-primary flex items-center gap-2"
            >
              {continuing ? <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : 'Continue'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

