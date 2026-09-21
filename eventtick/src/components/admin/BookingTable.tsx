
import type { Booking } from '../../types';

interface BookingTableProps {
  bookings: Booking[];
}

const statusColors: Record<string, string> = {
  CONFIRMED: 'bg-success/20 text-success border-success/30',
  PENDING: 'bg-warning/20 text-warning border-warning/30',
  CANCELLED: 'bg-error/20 text-error border-error/30',
  FAILED: 'bg-error/20 text-error border-error/30',
};

export default function BookingTable({ bookings }: BookingTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border">
            {['Booking Ref', 'Event', 'Seats', 'Amount', 'Status', 'Date'].map(h => (
              <th key={h} className="text-left text-text-muted text-xs font-medium py-3 px-4 whitespace-nowrap">{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {bookings.map(b => (
            <tr key={b.id} className="border-b border-border/50 hover:bg-bg-tertiary transition-colors">
              <td className="py-3 px-4 font-mono text-xs text-accent-lighter">{b.bookingRef}</td>
              <td className="py-3 px-4">
                <div className="text-text-primary font-medium truncate max-w-[180px]">{b.content?.title || b.contentId}</div>
                <div className="text-text-muted text-xs">{b.venue?.city}</div>
              </td>
              <td className="py-3 px-4 text-text-secondary font-mono text-xs">{b.seats.join(', ')}</td>
              <td className="py-3 px-4 text-text-primary font-semibold">₹{b.totalAmount.toLocaleString('en-IN')}</td>
              <td className="py-3 px-4">
                <span className={`badge border text-[10px] ${statusColors[b.status]}`}>{b.status}</span>
              </td>
              <td className="py-3 px-4 text-text-muted text-xs whitespace-nowrap">
                {new Date(b.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {bookings.length === 0 && (
        <div className="text-center py-10 text-text-muted text-sm">No bookings found.</div>
      )}
    </div>
  );
}

