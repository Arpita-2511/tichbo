
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Download, Share2, ArrowLeft, CheckCircle, Ticket, MapPin, Calendar, Clock } from 'lucide-react';
import { getBooking, getShowById, getEventById, getVenueById, ApiError, type BackendBookingResponse } from '../services/api';
import type { Content, Venue, Show } from '../types';
import Loading from '../components/common/Loading';

function MockQR() {
  const pattern = [
    [1,1,1,0,1,0,1,1,1],
    [1,0,1,0,0,0,1,0,1],
    [1,0,1,0,1,0,1,0,1],
    [1,1,1,0,0,0,1,1,1],
    [0,0,0,1,0,1,0,0,0],
    [1,1,0,0,1,0,0,1,1],
    [1,1,1,0,0,0,1,0,1],
    [1,0,1,0,0,0,1,1,0],
    [1,1,1,0,1,0,1,1,1],
  ];
  return (
    <div className="inline-grid gap-0.5" style={{ gridTemplateColumns: `repeat(9, 1fr)` }}>
      {pattern.flat().map((cell, i) => (
        <div key={i} className={`w-3 h-3 rounded-sm ${cell ? 'bg-text-primary' : 'bg-transparent'}`} />
      ))}
    </div>
  );
}

export default function TicketPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [booking, setBooking] = useState<BackendBookingResponse | null>(null);
  const [show, setShow] = useState<Show | null>(null);
  const [content, setContent] = useState<Content | null>(null);
  const [venue, setVenue] = useState<Venue | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) {
      setError('No booking ID provided.');
      setLoading(false);
      return;
    }
    setError(null);
    getBooking(id)
      .then(async b => {
        setBooking(b);
        const s = await getShowById(b.showId);
        setShow(s);
        if (s) {
          const [c, v] = await Promise.all([
            getEventById(s.contentId),
            getVenueById(s.venueId),
          ]);
          setContent(c);
          setVenue(v);
        }
        setLoading(false);
      })
      .catch(err => {
        setError(err instanceof ApiError ? err.message : 'Could not load ticket.');
        setLoading(false);
      });
  }, [id]);

  if (loading) return <Loading />;

  if (error || !booking) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <p className="text-text-muted mb-4">{error || 'Ticket not found.'}</p>
          <button onClick={() => navigate('/bookings')} className="btn-primary">My Bookings</button>
        </div>
      </div>
    );
  }

  const bookingRef = booking.bookingId.slice(0, 8).toUpperCase();
  const seatCount = booking.seats.length;

  return (
    <div className="min-h-screen max-w-lg mx-auto px-4 py-8">
      <button onClick={() => navigate('/bookings')} className="flex items-center gap-2 text-text-secondary hover:text-text-primary mb-6 text-sm transition-colors">
        <ArrowLeft size={16} /> My Bookings
      </button>

      <div className="text-center mb-6">
        <div className="w-14 h-14 bg-success/20 border border-success/30 rounded-full flex items-center justify-center mx-auto mb-3">
          <CheckCircle size={28} className="text-success" />
        </div>
        <h1 className="text-xl font-bold text-text-primary">
          {booking.status === 'CONFIRMED' ? 'Booking Confirmed!' : `Booking ${booking.status}`}
        </h1>
        <p className="text-text-muted text-sm mt-1">Your ticket has been sent to your email</p>
      </div>

      <div className="bg-bg-secondary border border-border rounded-2xl overflow-hidden shadow-2xl">
        <div className="bg-accent-gradient p-5 relative">
          <div className="flex items-center gap-2 mb-1">
            <Ticket size={16} className="text-white/80" />
            <span className="text-white/80 text-xs font-semibold tracking-widest uppercase">Eventtick</span>
          </div>
          <h2 className="text-white text-xl font-black">{content?.title || `Booking ${bookingRef}`}</h2>
          {content?.genre && <p className="text-white/70 text-sm">{content.genre}</p>}
        </div>

        <div className="relative h-5 bg-bg-secondary">
          <div className="absolute left-0 right-0 top-0 border-t border-dashed border-border" />
          <div className="absolute -left-3 top-1/2 -translate-y-1/2 w-6 h-6 rounded-full bg-bg-primary border border-border" />
          <div className="absolute -right-3 top-1/2 -translate-y-1/2 w-6 h-6 rounded-full bg-bg-primary border border-border" />
        </div>

        <div className="p-5 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <p className="text-text-muted text-xs uppercase tracking-wide mb-1">
                {show ? 'Show Date' : 'Booked On'}
              </p>
              <div className="flex items-center gap-1.5 text-text-primary text-sm font-medium">
                <Calendar size={13} className="text-accent-lighter" />
                {show
                  ? new Date(show.date).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
                  : booking.createdAt
                    ? new Date(booking.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
                    : '–'}
              </div>
            </div>
            <div>
              <p className="text-text-muted text-xs uppercase tracking-wide mb-1">
                {show ? 'Show Time' : 'Booking Time'}
              </p>
              <div className="flex items-center gap-1.5 text-text-primary text-sm font-medium">
                <Clock size={13} className="text-accent-lighter" />
                {show?.timeLabel
                  || (booking.createdAt
                    ? new Date(booking.createdAt).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })
                    : '–')}
              </div>
            </div>
            <div>
              <p className="text-text-muted text-xs uppercase tracking-wide mb-1">Venue</p>
              <div className="flex items-center gap-1.5 text-text-primary text-sm font-medium">
                <MapPin size={13} className="text-accent-lighter" />
                <span className="line-clamp-1">{venue?.name || '–'}</span>
              </div>
            </div>
            <div>
              <p className="text-text-muted text-xs uppercase tracking-wide mb-1">City</p>
              <p className="text-text-primary text-sm font-medium">{venue?.city || '–'}</p>
            </div>
          </div>

          <div>
            <p className="text-text-muted text-xs uppercase tracking-wide mb-2">
              {seatCount === 1 ? '1 Seat' : `${seatCount} Seats`}
            </p>
          </div>

          <div className="flex justify-between items-center">
            <div>
              <p className="text-text-muted text-xs uppercase tracking-wide mb-0.5">Total Paid</p>
              <p className="text-text-primary font-bold text-lg">₹{booking.totalAmount.toLocaleString('en-IN')}</p>
            </div>
            <div className="text-right">
              <p className="text-text-muted text-xs uppercase tracking-wide mb-0.5">Status</p>
              <span className={`badge border ${booking.status === 'CONFIRMED' ? 'bg-success/20 text-success border-success/30' : 'bg-error/20 text-error border-error/30'}`}>
                {booking.status}
              </span>
            </div>
          </div>
        </div>

        <div className="relative h-5 bg-bg-secondary">
          <div className="border-t border-dashed border-border absolute top-0 left-0 right-0" />
          <div className="absolute -left-3 top-1/2 -translate-y-1/2 w-6 h-6 rounded-full bg-bg-primary border border-border" />
          <div className="absolute -right-3 top-1/2 -translate-y-1/2 w-6 h-6 rounded-full bg-bg-primary border border-border" />
        </div>

        <div className="p-5 flex flex-col items-center gap-3">
          <div className="p-3 bg-white rounded-xl">
            <MockQR />
          </div>
          <div className="text-center">
            <p className="text-text-muted text-xs mb-0.5">Booking ID</p>
            <p className="font-mono text-text-primary font-bold tracking-wider text-sm">{bookingRef}</p>
          </div>
          <p className="text-text-muted text-xs text-center">Present this QR code at the venue entrance</p>
        </div>
      </div>

      <div className="flex gap-3 mt-5">
        <button className="flex-1 btn-primary flex items-center justify-center gap-2">
          <Download size={15} /> Download
        </button>
        <button className="flex-1 btn-secondary flex items-center justify-center gap-2">
          <Share2 size={15} /> Share
        </button>
      </div>
    </div>
  );
}
