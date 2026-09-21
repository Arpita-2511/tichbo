import { useEffect, useState } from 'react';
import { useParams, useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, Timer } from 'lucide-react';
import { getSeatMap, getEventById, getShowById, getVenueById } from '../services/api';
import type { Content, Show, Venue, Seat, SeatSection } from '../types';
import SeatMap from '../components/booking/SeatMap';
import PriceBreakdown, { calculatePricing } from '../components/booking/PriceBreakdown';
import Loading from '../components/common/Loading';

export default function SeatSelection() {
  const { showId } = useParams<{ showId: string }>();
  const location = useLocation();
  const navigate = useNavigate();

  // Prefer state passed from EventDetails, fall back to fetching
  const stateData = location.state as { event?: Content; show?: Show; venue?: Venue } | null;

  const [event, setEvent] = useState<Content | null>(stateData?.event || null);
  const [show, setShow] = useState<Show | null>(stateData?.show || null);
  const [venue, setVenue] = useState<Venue | null>(stateData?.venue || null);
  const [sections, setSections] = useState<SeatSection[]>([]);
  const [selectedSeats, setSelectedSeats] = useState<Seat[]>([]);
  const [loading, setLoading] = useState(true);
  const [timer, setTimer] = useState(600); // 10 min seat hold
  const [continuing, setContinuing] = useState(false);

  useEffect(() => {
    if (!showId) return;
    const loadData = async () => {
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
      setLoading(false);
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
    setContinuing(true);
    const { ticketTotal, convenienceFee, total } = calculatePricing(selectedSeats);
    // Pass booking details via location state (no URL params for sensitive data)
    navigate(`/booking/new/summary`, {
      state: {
        event, show, venue,
        seats: selectedSeats,
        ticketPrice: ticketTotal,
        convenienceFee,
        totalAmount: total,
      }
    });
  };

  const formatTimer = (s: number) => `${Math.floor(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}`;

  if (loading) return <div className="min-h-screen flex items-center justify-center"><Loading /></div>;

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

