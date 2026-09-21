import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Users, Ticket, IndianRupee, CalendarDays, ShieldAlert } from 'lucide-react';
import { getAdminStats, getRateLimitPolicies, getBookings } from '../services/api';
import { useApp } from '../context/AppContext';
import type { AdminStats, Booking, RateLimitPolicy } from '../types';
import StatCard from '../components/admin/StatCard';
import BookingTable from '../components/admin/BookingTable';
import RateLimitTable from '../components/admin/RateLimitTable';
import EmptyState from '../components/common/EmptyState';
import Loading from '../components/common/Loading';

export default function Admin() {
  const { user } = useApp();
  const [stats, setStats] = useState<AdminStats | null>(null);
  const [policies, setPolicies] = useState<RateLimitPolicy[]>([]);
  const [bookings, setBookings] = useState<Booking[]>([]);

  useEffect(() => {
    getAdminStats().then(setStats);
    getRateLimitPolicies().then(setPolicies);
    getBookings().then(setBookings);
  }, []);

  // TODO: the User type has no role yet; once the backend returns ADMIN/CUSTOMER, gate on it.
  if (!user) {
    return (
      <EmptyState
        icon={<ShieldAlert size={24} />}
        title="Admin access only"
        description="Log in with an administrator account to continue."
        action={<Link to="/login" className="btn-primary">Log In</Link>}
      />
    );
  }
  if (!stats) return <Loading />;

  const maxBookings = Math.max(...stats.bookingsTrend.map(d => d.count));

  return (
    <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-8 space-y-8">
      <h1 className="section-title">Admin Dashboard</h1>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard title="Total Users" value={stats.totalUsers.toLocaleString('en-IN')} icon={<Users size={18} />} color="accent" />
        <StatCard title="Total Bookings" value={stats.totalBookings.toLocaleString('en-IN')} icon={<Ticket size={18} />} />
        <StatCard title="Today's Revenue" value={`₹${stats.todayRevenue.toLocaleString('en-IN')}`} icon={<IndianRupee size={18} />} color="success" />
        <StatCard title="Active Events" value={stats.activeEvents} icon={<CalendarDays size={18} />} color="warning" />
      </div>

      <div className="grid lg:grid-cols-2 gap-6">
        <section className="card p-5">
          <h2 className="text-text-primary font-semibold mb-4">Bookings (last 8 days)</h2>
          <div className="flex items-end gap-2 h-40">
            {stats.bookingsTrend.map(d => (
              <div key={d.date} className="flex-1 flex flex-col items-center gap-1">
                <div className="w-full bg-accent rounded-t" style={{ height: `${(d.count / maxBookings) * 100}%` }} title={`${d.count} bookings`} />
                <span className="text-[10px] text-text-muted">{d.date.split(' ')[1]}</span>
              </div>
            ))}
          </div>
        </section>
        <section className="card p-5">
          <h2 className="text-text-primary font-semibold mb-4">Bookings by category</h2>
          <div className="space-y-3">
            {stats.bookingsByCategory.map(c => (
              <div key={c.category}>
                <div className="flex justify-between text-xs text-text-secondary mb-1">
                  <span>{c.category}</span><span>{c.count.toLocaleString('en-IN')}</span>
                </div>
                <div className="h-2 bg-bg-tertiary rounded-full">
                  <div className="h-2 bg-accent rounded-full" style={{ width: `${(c.count / stats.totalBookings) * 100}%` }} />
                </div>
              </div>
            ))}
          </div>
        </section>
      </div>

      <section className="card p-5">
        <h2 className="text-text-primary font-semibold mb-2">Recent bookings</h2>
        <BookingTable bookings={bookings} />
      </section>

      <section className="card p-5">
        <h2 className="text-text-primary font-semibold mb-2">Rate-limit policies</h2>
        <RateLimitTable policies={policies} />
      </section>
    </div>
  );
}
