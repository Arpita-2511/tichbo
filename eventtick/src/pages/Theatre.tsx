import { useEffect, useState } from 'react';
import { Theater } from 'lucide-react';
import { getEvents } from '../services/api';
import type { Content } from '../types';
import EventCard from '../components/events/EventCard';
import EmptyState from '../components/common/EmptyState';
import { EventCardSkeleton } from '../components/common/Loading';

const genres = ['All', 'Musical', 'Drama', 'Stand-Up Comedy', 'Classical Drama', 'Comedy'];

export default function Theatre() {
  const [events, setEvents] = useState<Content[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeGenre, setActiveGenre] = useState('All');

  useEffect(() => {
    getEvents('THEATRE').then(data => { setEvents(data); setLoading(false); });
  }, []);

  const filtered = activeGenre === 'All'
    ? events
    : events.filter(e => e.genre?.toLowerCase().includes(activeGenre.toLowerCase()));

  return (
    <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center gap-3 mb-6">
        <div className="p-2 bg-yellow-500/10 rounded-xl border border-yellow-500/20">
          <Theater size={22} className="text-yellow-400" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-text-primary">Theatre</h1>
          <p className="text-text-muted text-sm">Plays, musicals, stand-up comedy & performing arts</p>
        </div>
      </div>

      <div className="flex gap-2 overflow-x-auto scrollbar-hide pb-2 mb-6">
        {genres.map(g => (
          <button
            key={g}
            onClick={() => setActiveGenre(g)}
            className={`px-4 py-2 rounded-full border text-sm font-medium whitespace-nowrap transition-colors ${
              activeGenre === g
                ? 'bg-yellow-500/20 border-yellow-500/50 text-yellow-400'
                : 'bg-bg-secondary border-border text-text-secondary hover:border-yellow-500/30'
            }`}
          >
            {g}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {[...Array(6)].map((_, i) => <EventCardSkeleton key={i} />)}
        </div>
      ) : filtered.length === 0 ? (
        <EmptyState title="No theatre events found" icon={<Theater size={24} />} />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {filtered.map(e => <EventCard key={e.id} event={e} variant="landscape" />)}
        </div>
      )}
    </div>
  );
}

