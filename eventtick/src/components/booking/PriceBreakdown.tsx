
import { Ticket, ChevronRight } from 'lucide-react';
import type { Seat, Content, Show, Venue } from '../../types';

interface PriceBreakdownProps {
  selectedSeats: Seat[];
  content?: Content | null;
  show?: Show | null;
  venue?: Venue | null;
  onContinue?: () => void;
  isLoading?: boolean;
}

const CONVENIENCE_FEE_RATE = 0.05; // 5%

export function calculatePricing(seats: Seat[]) {
  const ticketTotal = seats.reduce((sum, s) => sum + s.price, 0);
  const convenienceFee = Math.round(ticketTotal * CONVENIENCE_FEE_RATE);
  const total = ticketTotal + convenienceFee;
  return { ticketTotal, convenienceFee, total };
}

export default function PriceBreakdown({
  selectedSeats, content, show, venue, onContinue, isLoading
}: PriceBreakdownProps) {
  const { ticketTotal, convenienceFee, total } = calculatePricing(selectedSeats);
  const hasSeats = selectedSeats.length > 0;

  return (
    <div className="bg-bg-secondary border border-border rounded-xl p-4 space-y-4 sticky top-20">
      {/* Header */}
      <div className="flex items-center gap-2">
        <Ticket size={16} className="text-accent-lighter" />
        <h3 className="font-semibold text-text-primary text-sm">Booking Summary</h3>
      </div>

      {/* Event info */}
      {content && (
        <div className="border-b border-border pb-3">
          <p className="text-text-primary font-medium text-sm">{content.title}</p>
          {venue && <p className="text-text-muted text-xs mt-0.5">{venue.name}, {venue.city}</p>}
          {show && <p className="text-text-muted text-xs">{show.date} • {show.timeLabel}</p>}
        </div>
      )}

      {/* Selected seats */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <span className="text-text-muted text-xs">Selected Seats</span>
          <span className="text-text-secondary text-xs">{selectedSeats.length} ticket{selectedSeats.length !== 1 ? 's' : ''}</span>
        </div>
        {hasSeats ? (
          <div className="flex flex-wrap gap-1.5">
            {selectedSeats.map(s => (
              <span key={s.id} className="bg-accent/20 text-accent-lighter border border-accent/30 text-xs px-2 py-0.5 rounded-md font-mono">
                {s.label}
              </span>
            ))}
          </div>
        ) : (
          <p className="text-text-muted text-xs italic">No seats selected</p>
        )}
      </div>

      {/* Price breakdown */}
      {hasSeats && (
        <div className="space-y-2 border-t border-border pt-3">
          <div className="flex justify-between text-sm">
            <span className="text-text-secondary">
              Ticket Price × {selectedSeats.length}
            </span>
            <span className="text-text-primary">₹{ticketTotal.toLocaleString('en-IN')}</span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-text-secondary">Convenience Fee</span>
            <span className="text-text-primary">₹{convenienceFee.toLocaleString('en-IN')}</span>
          </div>
          <div className="flex justify-between font-bold border-t border-border pt-2">
            <span className="text-text-primary">Total</span>
            <span className="text-accent-lighter text-lg">₹{total.toLocaleString('en-IN')}</span>
          </div>
        </div>
      )}

      {/* CTA */}
      <button
        onClick={onContinue}
        disabled={!hasSeats || isLoading}
        className={`w-full btn-primary flex items-center justify-center gap-2 ${!hasSeats ? 'opacity-50 cursor-not-allowed' : ''}`}
      >
        {isLoading ? (
          <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
        ) : (
          <>
            {hasSeats ? 'Continue' : 'Select Seats'}
            {hasSeats && <ChevronRight size={15} />}
          </>
        )}
      </button>

      {hasSeats && (
        <p className="text-text-muted text-[10px] text-center leading-relaxed">
          Selected seats are held for 10 minutes. Complete your booking before the timer expires.
        </p>
      )}
    </div>
  );
}

