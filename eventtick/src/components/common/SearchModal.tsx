import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, X, Clock, TrendingUp } from 'lucide-react';
import { useApp } from '../../context/AppContext';
import { searchEvents } from '../../services/api';
import { popularSearches, recentSearches } from '../../data/mockData';
import type { SearchResult, ContentType } from '../../types';

const typeLabel: Record<ContentType, string> = {
  MOVIE: 'Movie',
  SPORTS_MATCH: 'Sports',
  CONCERT: 'Concert',
  THEATRE: 'Theatre',
  EVENT: 'Event',
};


export default function SearchModal() {
  const { isSearchOpen, setIsSearchOpen, searchQuery, setSearchQuery } = useApp();
  const navigate = useNavigate();
  const [results, setResults] = useState<SearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isSearchOpen) {
      setTimeout(() => inputRef.current?.focus(), 100);
    }
  }, [isSearchOpen]);

  useEffect(() => {
    if (!searchQuery.trim()) { setResults([]); return; }
    setLoading(true);
    const timer = setTimeout(async () => {
      const r = await searchEvents(searchQuery);
      setResults(r);
      setLoading(false);
    }, 250);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  const handleSelect = (result: SearchResult) => {
    setIsSearchOpen(false);
    setSearchQuery('');
    navigate(`/event/${result.id}`);
  };

  const handleCategorySearch = (q: string) => {
    setSearchQuery(q);
  };

  if (!isSearchOpen) return null;

  return (
    <div className="fixed inset-0 z-[100] bg-black/70 backdrop-blur-sm animate-fade-in" onClick={() => setIsSearchOpen(false)}>
      <div
        className="max-w-2xl mx-auto mt-16 mx-4 sm:mx-auto"
        onClick={e => e.stopPropagation()}
      >
        <div className="bg-bg-secondary border border-border rounded-2xl overflow-hidden shadow-2xl animate-slide-up">
          {/* Input */}
          <div className="flex items-center gap-3 px-4 py-4 border-b border-border">
            <Search size={18} className="text-text-muted flex-shrink-0" />
            <input
              ref={inputRef}
              type="text"
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              placeholder="Search movies, artists, teams, events..."
              className="flex-1 bg-transparent text-text-primary placeholder-text-muted outline-none text-base"
            />
            {searchQuery && (
              <button onClick={() => setSearchQuery('')} className="text-text-muted hover:text-text-primary">
                <X size={16} />
              </button>
            )}
            <button onClick={() => setIsSearchOpen(false)} className="text-text-muted hover:text-text-primary text-sm hidden sm:block">
              Esc
            </button>
          </div>

          <div className="max-h-[60vh] overflow-y-auto">
            {/* Default: no query */}
            {!searchQuery && (
              <div className="p-4 space-y-5">
                <div>
                  <div className="flex items-center gap-2 text-text-muted text-xs font-semibold uppercase tracking-wider mb-3">
                    <Clock size={12} /> Recent Searches
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {recentSearches.map(s => (
                      <button key={s} onClick={() => handleCategorySearch(s)} className="text-sm bg-bg-tertiary border border-border rounded-full px-3 py-1.5 text-text-secondary hover:border-accent/50 hover:text-text-primary transition-colors">
                        {s}
                      </button>
                    ))}
                  </div>
                </div>
                <div>
                  <div className="flex items-center gap-2 text-text-muted text-xs font-semibold uppercase tracking-wider mb-3">
                    <TrendingUp size={12} /> Popular Searches
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {popularSearches.map(s => (
                      <button key={s} onClick={() => handleCategorySearch(s)} className="text-sm bg-bg-tertiary border border-border rounded-full px-3 py-1.5 text-text-secondary hover:border-accent/50 hover:text-text-primary transition-colors">
                        {s}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            )}

            {/* Loading */}
            {searchQuery && loading && (
              <div className="p-8 text-center text-text-muted text-sm">Searching...</div>
            )}

            {/* Results */}
            {searchQuery && !loading && results.length === 0 && (
              <div className="p-8 text-center text-text-muted text-sm">
                No results for "<span className="text-text-secondary">{searchQuery}</span>"
              </div>
            )}

            {searchQuery && !loading && results.length > 0 && (
              <div className="py-2">
                {results.map(result => (
                  <button
                    key={result.id}
                    onClick={() => handleSelect(result)}
                    className="w-full flex items-center gap-3 px-4 py-3 hover:bg-bg-tertiary transition-colors text-left"
                  >
                    <img src={result.image} alt={result.title} className="w-10 h-10 rounded-lg object-cover flex-shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="text-text-primary text-sm font-medium truncate">{result.title}</div>
                      <div className="text-text-muted text-xs truncate">{result.subtitle}</div>
                    </div>
                    <span className="text-xs text-accent-lighter bg-accent/10 px-2 py-0.5 rounded-full flex-shrink-0">
                      {typeLabel[result.type]}
                    </span>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

