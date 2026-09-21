import { useEffect, useState } from 'react';
import { Film, Search } from 'lucide-react';
import { getEvents } from '../services/api';
import type { Content, EventFilters } from '../types';
import EventCard from '../components/events/EventCard';
import EventFiltersComponent from '../components/events/EventFilters';
import EmptyState from '../components/common/EmptyState';
import { EventGridSkeleton } from '../components/common/Loading';

export default function Movies() {
  const [tab, setTab] = useState<'now' | 'soon'>('now');
  const [all, setAll] = useState<Content[]>([]);
  const [filters, setFilters] = useState<EventFilters>({});
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getEvents('MOVIE').then(data => { setAll(data); setLoading(false); });
  }, []);

  const nowShowing = all.filter(m => m.isReleased);
  const comingSoon = all.filter(m => !m.isReleased);
  const source = tab === 'now' ? nowShowing : comingSoon;

  const filtered = source.filter(m => {
    if (search && !m.title.toLowerCase().includes(search.toLowerCase())) return false;
    if (filters.language && m.language !== filters.language) return false;
    if (filters.genre && !m.genre?.toLowerCase().includes(filters.genre.toLowerCase())) return false;
    if (filters.minRating && (m.rating || 0) < filters.minRating) return false;
    return true;
  });

  return (
    <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-8">
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <div className="p-2 bg-blue-500/10 rounded-xl border border-blue-500/20">
          <Film size={22} className="text-blue-400" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-text-primary">Movies</h1>
          <p className="text-text-muted text-sm">Book tickets for the latest blockbusters</p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-bg-secondary border border-border rounded-xl p-1 w-fit mb-6">
        {[
          { key: 'now', label: `Now Showing (${nowShowing.length})` },
          { key: 'soon', label: `Coming Soon (${comingSoon.length})` },
        ].map(t => (
          <button
            key={t.key}
            onClick={() => setTab(t.key as 'now' | 'soon')}
            className={`px-5 py-2 rounded-lg text-sm font-medium transition-colors ${
              tab === t.key ? 'bg-accent text-white' : 'text-text-secondary hover:text-text-primary'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Search + Filters */}
      <div className="flex flex-col sm:flex-row gap-3 mb-6">
        <div className="relative flex-1 max-w-sm">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
          <input
            type="text"
            placeholder="Search movies..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="input-field pl-9 py-2 text-sm"
          />
        </div>
        <EventFiltersComponent
          filters={filters}
          onChange={setFilters}
          showLanguage
          showGenre
        />
      </div>

      {/* Grid */}
      {loading ? (
        <EventGridSkeleton count={10} />
      ) : filtered.length === 0 ? (
        <EmptyState title="No movies found" description="Try adjusting your filters or search term." icon={<Film size={24} />} />
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
          {filtered.map(m => <EventCard key={m.id} event={m} />)}
        </div>
      )}
    </div>
  );
}

