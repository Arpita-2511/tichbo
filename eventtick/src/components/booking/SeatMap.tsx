
import type { SeatSection, Seat as SeatType } from '../../types';
import Seat from './Seat';

interface SeatMapProps {
  sections: SeatSection[];
  selectedSeats: SeatType[];
  onSeatToggle: (seat: SeatType) => void;
  maxSeats?: number;
}

const categoryLabels = {
  VIP: { label: 'VIP', color: 'text-yellow-400', dot: 'bg-yellow-500' },
  PREMIUM: { label: 'Premium', color: 'text-accent-lighter', dot: 'bg-accent' },
  REGULAR: { label: 'Regular', color: 'text-blue-400', dot: 'bg-blue-500' },
};

export default function SeatMap({ sections, selectedSeats, onSeatToggle, maxSeats = 10 }: SeatMapProps) {
  const selectedIds = new Set(selectedSeats.map(s => s.id));

  const isMaxReached = selectedSeats.length >= maxSeats;

  return (
    <div className="space-y-6">
      {/* Screen */}
      <div className="relative flex flex-col items-center mb-2">
        <div className="w-3/4 h-1.5 bg-gradient-to-r from-transparent via-accent/60 to-transparent rounded-full" />
        <div className="w-3/4 h-8 bg-gradient-to-b from-accent/10 to-transparent" style={{ clipPath: 'polygon(5% 0, 95% 0, 100% 100%, 0 100%)' }} />
        <span className="text-text-muted text-xs mt-1 tracking-widest uppercase">Screen</span>
      </div>

      {/* Sections */}
      {sections.map(section => {
        const cat = categoryLabels[section.category];
        return (
          <div key={section.category} className="space-y-1">
            {/* Section header */}
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <span className={`font-semibold text-sm ${cat.color}`}>{cat.label}</span>
                <span className="text-text-muted text-xs">— ₹{section.price.toLocaleString('en-IN')}</span>
              </div>
            </div>

            {/* Rows */}
            <div className="space-y-1.5 overflow-x-auto pb-1">
              {section.rows.map(row => (
                <div key={row.row} className="flex items-center gap-2 min-w-max mx-auto w-fit">
                  <span className="text-text-muted text-xs w-5 text-center font-mono">{row.row}</span>
                  <div className="flex gap-1">
                    {row.seats.slice(0, 6).map(seat => (
                      <Seat
                        key={seat.id}
                        seat={seat}
                        isSelected={selectedIds.has(seat.id)}
                        onSelect={() => {
                          if (!selectedIds.has(seat.id) && isMaxReached) return;
                          onSeatToggle(seat);
                        }}
                      />
                    ))}
                    {/* Aisle gap */}
                    <div className="w-4" />
                    {row.seats.slice(6).map(seat => (
                      <Seat
                        key={seat.id}
                        seat={seat}
                        isSelected={selectedIds.has(seat.id)}
                        onSelect={() => {
                          if (!selectedIds.has(seat.id) && isMaxReached) return;
                          onSeatToggle(seat);
                        }}
                      />
                    ))}
                  </div>
                  <span className="text-text-muted text-xs w-5 text-center font-mono">{row.row}</span>
                </div>
              ))}
            </div>
          </div>
        );
      })}

      {/* Legend */}
      <div className="flex flex-wrap items-center gap-4 pt-4 border-t border-border justify-center">
        {[
          { dot: 'bg-blue-500/30 border border-blue-500/50', label: 'Available' },
          { dot: 'bg-accent', label: 'Selected' },
          { dot: 'bg-bg-tertiary border border-border', label: 'Booked' },
        ].map(item => (
          <div key={item.label} className="flex items-center gap-2 text-xs text-text-muted">
            <div className={`w-5 h-5 rounded-t-md ${item.dot}`} />
            {item.label}
          </div>
        ))}
      </div>

      {isMaxReached && (
        <p className="text-warning text-xs text-center">Maximum {maxSeats} seats can be selected per booking.</p>
      )}
    </div>
  );
}

