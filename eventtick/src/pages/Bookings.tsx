import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Ticket } from 'lucide-react';
import { getBookings, cancelBooking } from '../services/api';
import { useApp } from '../context/AppContext';
import type { Booking } from '../types';
import EmptyState from '../components/common/EmptyState';
import Loading from '../components/common/Loading';

const statusColors: Record<string, string> = {
  CONFIRMED: 'bg-success/20 text-success border-success/30',
  PENDING: 'bg-warning/20 text-warning border-warning/30',
  CANCELLED: 'bg-error/20 text-error border-error/30',
  FAILED: 'bg-error/20 text-error border-error/30',
};

export default function Bookings() {
  const { user, isLoggedIn } = useApp();
  const navigate = useNavigate();
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!isLoggedIn) {
      setLoading(false);
      return;
    }
    getBookings(user?.id).then(b => { setBookings(b); setLoading(false); });
  }, [isLoggedIn, user?.id]);

  const handleCancel = async (id: string) => {
    if (!window.confirm('Cancel this booking?')) return;
    if (await cancelBooking(id)) {
      setBookings(bs => bs.map(b => (b.id === id ? { ...b, status: 'CANCELLED' } : b)));
    }
  };

  if (!isLoggedIn) {
    return (
      <EmptyState
        icon={<Ticket size={24} />}
        title="Log in to see your bookings"
        description="Your booking history is only visible when you're signed in."
        action={<Link to="/login" className="btn-primary">Log In</Link>}
      />
    );
  }
  if (loading) return <Loading />;

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      <h1 className="section-title mb-6">My Bookings</h1>
      {bookings.length === 0 ? (
        <EmptyState icon={<Ticket size={24} />} title="No bookings yet" description="Book something and it will show up here." />
      ) : (
        <div className="space-y-3">
          {bookings.map(b => (
            <div key={b.id} className="card p-4 flex flex-col sm:flex-row sm:items-center gap-4">
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  <span className="font-mono text-xs text-accent-lighter">{b.bookingRef}</span>
                  <span className={`badge border text-[10px] ${statusColors[b.status]}`}>{b.status}</span>
                </div>
                <div className="text-text-primary font-semibold truncate">{b.content?.title ?? b.contentId}</div>
                <div className="text-text-muted text-xs mt-0.5">
                  {[b.venue?.name, b.show && `${b.show.date} • ${b.show.timeLabel}`].filter(Boolean).join(' • ')}
                </div>
                <div className="text-text-secondary text-xs font-mono mt-1">Seats: {b.seats.join(', ')}</div>
              </div>
              <div className="flex sm:flex-col items-center sm:items-end gap-3 sm:gap-2">
                <div className="text-text-primary font-bold">₹{b.totalAmount.toLocaleString('en-IN')}</div>
                <div className="flex gap-2">
                  {b.status === 'CONFIRMED' && (
                    <>
                      <button onClick={() => navigate(`/ticket/${b.id}`, { state: { booking: b } })} className="btn-secondary text-xs py-1.5 px-3">View Ticket</button>
                      <button onClick={() => handleCancel(b.id)} className="btn-ghost text-xs py-1.5 px-3 text-error">Cancel</button>
                    </>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
