import { useEffect, useState } from 'react';
import { Music } from 'lucide-react';
import { getEvents } from '../services/api';
import type { Content } from '../types';
import EventCard from '../components/events/EventCard';
import EmptyState from '../components/common/EmptyState';
import { EventCardSkeleton } from '../components/common/Loading';

export default function Concerts() {
  const [events, setEvents] = useState<Content[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getEvents('CONCERT').then(data => { setEvents(data); setLoading(false); });
  }, []);

  return (
    <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center gap-3 mb-8">
        <div className="p-2 bg-pink-500/10 rounded-xl border border-pink-500/20">
          <Music size={22} className="text-pink-400" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-text-primary">Concerts</h1>
          <p className="text-text-muted text-sm">Live music, performances & artist tours</p>
        </div>
      </div>

      {/* Featured hero card */}
      {!loading && events[0] && (
        <div
          className="relative rounded-2xl overflow-hidden h-64 sm:h-80 mb-8 cursor-pointer group"
          onClick={() => window.location.href = `/event/${events[0].id}`}
        >
          <img src={events[0].bannerImage || events[0].image} alt={events[0].title} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-700" />
          <div className="absolute inset-0 bg-gradient-to-r from-bg-primary/90 via-bg-primary/50 to-transparent" />
          <div className="absolute inset-0 flex flex-col justify-end p-6 sm:p-8">
            <span className="text-pink-400 text-xs font-semibold uppercase tracking-wider mb-2">Featured Concert</span>
            <h2 className="text-text-primary text-2xl sm:text-3xl font-black mb-1">{events[0].title}</h2>
            <p className="text-text-secondary text-sm mb-4">{events[0].venue}, {events[0].city} • From ₹{events[0].price?.toLocaleString('en-IN')}</p>
            <button className="btn-primary w-fit">Book Tickets</button>
          </div>
        </div>
      )}

      {loading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {[...Array(6)].map((_, i) => <EventCardSkeleton key={i} />)}
        </div>
      ) : events.length === 0 ? (
        <EmptyState title="No concerts found" icon={<Music size={24} />} />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {events.map(e => <EventCard key={e.id} event={e} variant="landscape" />)}
        </div>
      )}
    </div>
  );
}

