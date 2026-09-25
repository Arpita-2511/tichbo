import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Users, Ticket, IndianRupee, CalendarDays, ShieldAlert, Film, MapPin, Activity, Database, CheckCircle2, XCircle } from 'lucide-react';
import { getAdminStats, getAdminOverviewStats, getRateLimitPolicies, getRateLimitStats, getBookings, ApiError } from '../services/api';
import { useApp } from '../context/AppContext';
import type { AdminStats, AdminOverviewStats, Booking, RateLimitPolicy, RateLimitStatsResponse } from '../types';
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

  // FR-36: the real Admin Overview statistics — kept separate from the
  // mock `stats`/`policies`/`bookings` state above, which the rest of this
  // page (charts, recent-bookings table, rate-limit table) still uses
  // pending their own backend integration phases.
  const [overview, setOverview] = useState<AdminOverviewStats | null>(null);
  const [overviewError, setOverviewError] = useState<string | null>(null);

  // FR-40: the real Gateway Rate-Limit Visibility data — a LOCAL Gateway
  // endpoint, fetched once on load like every other admin section here (no
  // polling — see the endpoint's own "since Gateway startup" semantics,
  // not a live/sliding-window metric that would need refreshing).
  const [rateLimitStats, setRateLimitStats] = useState<RateLimitStatsResponse | null>(null);
  const [rateLimitError, setRateLimitError] = useState<string | null>(null);

  const isAdmin = user?.role === 'ADMIN';

  useEffect(() => {
    getAdminStats().then(setStats);
    getRateLimitPolicies().then(setPolicies);
    getBookings().then(setBookings);
  }, []);

  useEffect(() => {
    if (!isAdmin) return;
    setOverviewError(null);
    getAdminOverviewStats()
      .then(setOverview)
      .catch(err => setOverviewError(err instanceof ApiError ? err.message : 'Could not load statistics. Please try again.'));
  }, [isAdmin]);

  useEffect(() => {
    if (!isAdmin) return;
    setRateLimitError(null);
    getRateLimitStats()
      .then(setRateLimitStats)
      // A genuine API failure (network/5xx/401/403) — distinct from a
      // successful 200 response reporting redisAvailable: false, which is
      // not an error and is handled in the render below instead.
      .catch(err => setRateLimitError(err instanceof ApiError ? err.message : 'Could not load rate-limit data. Please try again.'));
  }, [isAdmin]);

  if (!user || !isAdmin) {
    return (
      <EmptyState
        icon={<ShieldAlert size={24} />}
        title="Admin access only"
        description={user
          ? 'Your account does not have administrator access.'
          : 'Log in with an administrator account to continue.'}
        action={!user ? <Link to="/login" className="btn-primary">Log In</Link> : undefined}
      />
    );
  }
  if (!stats) return <Loading />;

  const maxBookings = Math.max(...stats.bookingsTrend.map(d => d.count));

  // FR-40: flatten the configured policy matrix (category -> tier ->
  // limits) into rows, plus the fallback policy, for display — kept
  // separate from `activity`, which is observed counters, not configuration.
  const policyRows = rateLimitStats
    ? [
        ...Object.entries(rateLimitStats.policies).flatMap(([category, byTier]) =>
          Object.entries(byTier).map(([tier, limits]) => ({ id: `${category}:${tier}`, ...limits }))),
        { id: 'FALLBACK', ...rateLimitStats.fallback },
      ]
    : [];
  const activityEntries = rateLimitStats ? Object.entries(rateLimitStats.activity.byPolicy) : [];
  const totalAllowed = activityEntries.reduce((sum, [, counts]) => sum + counts.allowed, 0);
  const totalRejected = activityEntries.reduce((sum, [, counts]) => sum + counts.rejected, 0);

  return (
    <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-8 space-y-8">
      <h1 className="section-title">Admin Dashboard</h1>

      <section className="space-y-4">
        <h2 className="text-text-primary font-semibold">Platform Overview</h2>
        {overviewError ? (
          <div className="card p-5 border-error/30 text-error text-sm">{overviewError}</div>
        ) : !overview ? (
          <Loading />
        ) : (
          <div className="grid grid-cols-2 lg:grid-cols-5 gap-4">
            <StatCard title="Total Users" value={overview.totalUsers.toLocaleString('en-IN')} icon={<Users size={18} />} color="accent" />
            <StatCard title="Total Content" value={overview.totalContent.toLocaleString('en-IN')} icon={<Film size={18} />} />
            <StatCard title="Total Shows" value={overview.totalShows.toLocaleString('en-IN')} icon={<CalendarDays size={18} />} color="warning" />
            <StatCard title="Total Venues" value={overview.totalVenues.toLocaleString('en-IN')} icon={<MapPin size={18} />} />
            <StatCard title="Total Bookings" value={overview.totalBookings.toLocaleString('en-IN')} icon={<Ticket size={18} />} color="success" />
          </div>
        )}
      </section>

      <section className="space-y-4">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <h2 className="text-text-primary font-semibold">Traffic &amp; Rate Limiting</h2>
          <span className="text-text-muted text-xs">Activity counters are since the Gateway last started — not a live or last-5-minutes view.</span>
        </div>

        {rateLimitError ? (
          <div className="card p-5 border-error/30 text-error text-sm">{rateLimitError}</div>
        ) : !rateLimitStats ? (
          <Loading />
        ) : (
          <div className="space-y-4">
            {!rateLimitStats.activity.redisAvailable && (
              <div className="card p-4 border-warning/30 bg-warning/5 flex items-start gap-3">
                <ShieldAlert size={18} className="text-warning shrink-0 mt-0.5" />
                <div className="text-sm">
                  <p className="text-text-primary font-medium">Activity counters unavailable</p>
                  <p className="text-text-muted mt-0.5">
                    Redis is currently unreachable, so recent allowed/rejected counts can't be read. This does
                    not affect rate limiting itself — the Gateway fails open, so ordinary traffic is unaffected.
                    The configured policy limits below are unaffected too (they don't depend on Redis).
                  </p>
                </div>
              </div>
            )}

            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              <StatCard
                title="Redis Status"
                value={rateLimitStats.activity.redisAvailable ? 'Available' : 'Unavailable'}
                icon={<Database size={18} />}
                color={rateLimitStats.activity.redisAvailable ? 'success' : 'warning'}
              />
              <StatCard title="Allowed (since startup)" value={totalAllowed.toLocaleString('en-IN')} icon={<CheckCircle2 size={18} />} color="success" />
              <StatCard title="Rejected (since startup)" value={totalRejected.toLocaleString('en-IN')} icon={<XCircle size={18} />} color={totalRejected > 0 ? 'warning' : 'default'} />
              <StatCard title="Policies Tracked" value={policyRows.length} icon={<Activity size={18} />} />
            </div>

            <div className="grid lg:grid-cols-2 gap-6">
              <div className="card p-5">
                <h3 className="text-text-primary font-semibold mb-1">Observed activity</h3>
                <p className="text-text-muted text-xs mb-4">Requests allowed/rejected per policy, since Gateway startup.</p>
                {activityEntries.length === 0 ? (
                  <p className="text-text-muted text-sm">
                    {rateLimitStats.activity.redisAvailable ? 'No traffic recorded yet.' : 'Unavailable — see warning above.'}
                  </p>
                ) : (
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-text-muted text-xs uppercase tracking-wide text-left">
                        <th className="pb-2 font-medium">Category:Tier</th>
                        <th className="pb-2 font-medium text-right">Allowed</th>
                        <th className="pb-2 font-medium text-right">Rejected</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                      {activityEntries.map(([policyId, counts]) => (
                        <tr key={policyId}>
                          <td className="py-2 text-text-primary font-mono text-xs">{policyId}</td>
                          <td className="py-2 text-right text-success">{counts.allowed.toLocaleString('en-IN')}</td>
                          <td className="py-2 text-right text-error">{counts.rejected.toLocaleString('en-IN')}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>

              <div className="card p-5">
                <h3 className="text-text-primary font-semibold mb-1">Configured policy limits</h3>
                <p className="text-text-muted text-xs mb-4">The active rate-limit matrix (read-only — configuration-driven, restart to change).</p>
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-text-muted text-xs uppercase tracking-wide text-left">
                      <th className="pb-2 font-medium">Category:Tier</th>
                      <th className="pb-2 font-medium text-right">Replenish/s</th>
                      <th className="pb-2 font-medium text-right">Burst</th>
                      <th className="pb-2 font-medium text-right">Cost/req</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {policyRows.map(p => (
                      <tr key={p.id}>
                        <td className="py-2 text-text-primary font-mono text-xs">{p.id}</td>
                        <td className="py-2 text-right text-text-secondary">{p.replenishRate}</td>
                        <td className="py-2 text-right text-text-secondary">{p.burstCapacity}</td>
                        <td className="py-2 text-right text-text-secondary">{p.requestedTokens}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </section>

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
