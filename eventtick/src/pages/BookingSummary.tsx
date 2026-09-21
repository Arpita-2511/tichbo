import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, MapPin, Calendar, Clock, Ticket, CreditCard, Smartphone, CheckCircle } from 'lucide-react';
import type { Content, Show, Venue, Seat } from '../types';
import { createBooking } from '../services/api';

interface SummaryState {
  event: Content;
  show: Show;
  venue: Venue;
  seats: Seat[];
  ticketPrice: number;
  convenienceFee: number;
  totalAmount: number;
}

const paymentMethods = [
  { id: 'upi', label: 'UPI', icon: <Smartphone size={18} /> },
  { id: 'card', label: 'Credit / Debit Card', icon: <CreditCard size={18} /> },
  { id: 'netbanking', label: 'Net Banking', icon: <CreditCard size={18} /> },
];

export default function BookingSummary() {
  const location = useLocation();
  const navigate = useNavigate();
  const state = location.state as SummaryState | null;

  const [selectedPayment, setSelectedPayment] = useState('upi');
  const [loading, setLoading] = useState(false);
  const [agreed, setAgreed] = useState(false);

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
    setLoading(true);
    try {
      const booking = await createBooking({
        showId: show.id,
        contentId: event.id,
        venueId: venue.id,
        seats: seats.map(s => s.label),
        category: seats[0]?.category || 'REGULAR',
        ticketPrice,
        convenienceFee,
        totalAmount,
      });
      navigate(`/ticket/${booking.id}`, { state: { booking } });
    } catch {
      alert('Booking failed. Please try again.');
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen max-w-2xl mx-auto px-4 sm:px-6 py-8">
      {/* Back */}
      <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-text-secondary hover:text-text-primary mb-6 text-sm transition-colors">
        <ArrowLeft size={16} /> Back to seat selection
      </button>

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

      {/* CTA */}
      <button
        onClick={handleProceed}
        disabled={!agreed || loading}
        className={`w-full btn-primary text-base py-4 flex items-center justify-center gap-2 ${!agreed ? 'opacity-50 cursor-not-allowed' : ''}`}
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

