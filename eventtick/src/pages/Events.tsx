import { useEffect, useState } from 'react';
import { Calendar, Search } from 'lucide-react';
import { getEvents } from '../services/api';
import type { Content } from '../types';
import EventCard from '../components/events/EventCard';
import EmptyState from '../components/common/EmptyState';
import { EventCardSkeleton } from '../components/common/Loading';

const genres = ['All', 'Conference', 'Festival', 'Exhibition', 'Cultural Festival', 'Workshop', 'Literature Festival', 'Food Festival', 'Wellness'];

export default function Events() {
  const [events, setEvents] = useState<Content[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeGenre, setActiveGenre] = useState('All');
  const [search, setSearch] = useState('');

  useEffect(() => {
    getEvents('EVENT').then(data => { setEvents(data); setLoading(false); });
  }, []);

  const filtered = events.filter(e => {
    if (activeGenre !== 'All' && !e.genre?.toLowerCase().includes(activeGenre.toLowerCase())) return false;
    if (search && !e.title.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });

  return (
    <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center gap-3 mb-6">
        <div className="p-2 bg-purple-500/10 rounded-xl border border-purple-500/20">
          <Calendar size={22} className="text-purple-400" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-text-primary">Events</h1>
          <p className="text-text-muted text-sm">Conferences, festivals, exhibitions & more</p>
        </div>
      </div>

      <div className="flex flex-col sm:flex-row gap-3 mb-5">
        <div className="relative">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
          <input
            type="text"
            placeholder="Search events..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="input-field pl-9 py-2 text-sm w-full sm:w-64"
          />
        </div>
      </div>

      <div className="flex gap-2 overflow-x-auto scrollbar-hide pb-2 mb-6">
        {genres.map(g => (
          <button
            key={g}
            onClick={() => setActiveGenre(g)}
            className={`px-4 py-2 rounded-full border text-sm font-medium whitespace-nowrap transition-colors ${
              activeGenre === g
                ? 'bg-purple-500/20 border-purple-500/50 text-purple-400'
                : 'bg-bg-secondary border-border text-text-secondary hover:border-purple-500/30'
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
        <EmptyState title="No events found" description="Try changing your search or genre filter." icon={<Calendar size={24} />} />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {filtered.map(e => <EventCard key={e.id} event={e} variant="landscape" />)}
        </div>
      )}
    </div>
  );
}

