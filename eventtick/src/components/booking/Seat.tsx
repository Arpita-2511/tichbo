
import type { Seat as SeatType } from '../../types';

interface SeatProps {
  seat: SeatType;
  isSelected: boolean;
  onSelect: (seat: SeatType) => void;
}

const categoryColors = {
  VIP: {
    available: 'bg-yellow-500/20 border-yellow-500/50 text-yellow-400 hover:bg-yellow-500 hover:text-black hover:border-yellow-500',
    selected: 'bg-yellow-500 border-yellow-400 text-black',
    booked: 'bg-bg-tertiary border-border text-border-light cursor-not-allowed',
    unavailable: 'bg-bg-tertiary border-border text-border-light cursor-not-allowed opacity-50',
  },
  PREMIUM: {
    available: 'bg-accent/20 border-accent/50 text-accent-lighter hover:bg-accent hover:text-white hover:border-accent',
    selected: 'bg-accent border-accent text-white',
    booked: 'bg-bg-tertiary border-border text-border-light cursor-not-allowed',
    unavailable: 'bg-bg-tertiary border-border text-border-light cursor-not-allowed opacity-50',
  },
  REGULAR: {
    available: 'bg-blue-500/20 border-blue-500/50 text-blue-400 hover:bg-blue-500 hover:text-white hover:border-blue-500',
    selected: 'bg-blue-500 border-blue-400 text-white',
    booked: 'bg-bg-tertiary border-border text-border-light cursor-not-allowed',
    unavailable: 'bg-bg-tertiary border-border text-border-light cursor-not-allowed opacity-50',
  },
};

export default function Seat({ seat, isSelected, onSelect }: SeatProps) {
  const isClickable = seat.status === 'AVAILABLE';
  const statusKey = isSelected ? 'selected' : seat.status.toLowerCase() as keyof typeof categoryColors['VIP'];
  const colors = categoryColors[seat.category]?.[statusKey] || categoryColors.REGULAR.available;

  return (
    <button
      disabled={!isClickable}
      onClick={() => isClickable && onSelect(seat)}
      title={`${seat.label} — ₹${seat.price.toLocaleString('en-IN')} (${seat.status})`}
      className={`
        w-7 h-7 text-[9px] font-bold rounded-t-lg border transition-all duration-150
        ${colors}
        ${isClickable ? 'cursor-pointer active:scale-95' : ''}
        ${isSelected ? 'scale-110 shadow-lg shadow-accent/30' : ''}
      `}
    >
      {seat.number}
    </button>
  );
}

