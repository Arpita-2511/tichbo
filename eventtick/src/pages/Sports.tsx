import { useEffect, useState } from 'react';
import { Trophy, Search } from 'lucide-react';
import { getEvents } from '../services/api';
import type { Content, EventFilters } from '../types';
import EventCard from '../components/events/EventCard';
import EventFiltersComponent from '../components/events/EventFilters';
import EmptyState from '../components/common/EmptyState';

const sportIcons: Record<string, string> = {
  Cricket: '🏏', Football: '⚽', Basketball: '🏀', Tennis: '🎾',
  Badminton: '🏸', Kabaddi: '🤸', 'Formula 1': '🏎️',
};

export default function Sports() {
  const [events, setEvents] = useState<Content[]>([]);
  const [filters, setFilters] = useState<EventFilters>({});
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [activeSport, setActiveSport] = useState('All');

  useEffect(() => {
    getEvents('SPORTS_MATCH').then(data => { setEvents(data); setLoading(false); });
  }, []);

  const sports = ['All', ...Array.from(new Set(events.map(e => e.sport).filter(Boolean) as string[]))];

  const filtered = events.filter(e => {
    if (activeSport !== 'All' && e.sport !== activeSport) return false;
    if (search && !e.title.toLowerCase().includes(search.toLowerCase())) return false;
    if (filters.minRating && (e.rating || 0) < filters.minRating) return false;
    return true;
  });

  return (
    <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center gap-3 mb-6">
        <div className="p-2 bg-green-500/10 rounded-xl border border-green-500/20">
          <Trophy size={22} className="text-green-400" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-text-primary">Sports</h1>
          <p className="text-text-muted text-sm">Cricket, football, tennis & more live sporting action</p>
        </div>
      </div>

      {/* Sport pills */}
      <div className="flex gap-2 overflow-x-auto scrollbar-hide pb-2 mb-5">
        {sports.map(sport => (
          <button
            key={sport}
            onClick={() => setActiveSport(sport)}
            className={`flex items-center gap-1.5 px-4 py-2 rounded-full border text-sm font-medium whitespace-nowrap transition-colors ${
              activeSport === sport
                ? 'bg-accent border-accent text-white'
                : 'bg-bg-secondary border-border text-text-secondary hover:border-accent/50 hover:text-text-primary'
            }`}
          >
            {sport !== 'All' && <span>{sportIcons[sport] || '🏅'}</span>}
            {sport}
          </button>
        ))}
      </div>

      {/* Search */}
      <div className="flex flex-col sm:flex-row gap-3 mb-6">
        <div className="relative flex-1 max-w-sm">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
          <input
            type="text"
            placeholder="Search teams, tournaments..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="input-field pl-9 py-2 text-sm"
          />
        </div>
        <EventFiltersComponent filters={filters} onChange={setFilters} showLanguage={false} showGenre={false} />
      </div>

      {loading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {[...Array(6)].map((_, i) => (
            <div key={i} className="h-32 bg-bg-tertiary animate-skeleton rounded-xl" />
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <EmptyState title="No sports events found" description="Try a different sport or date range." icon={<Trophy size={24} />} />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {filtered.map(e => <EventCard key={e.id} event={e} variant="landscape" />)}
        </div>
      )}
    </div>
  );
}

