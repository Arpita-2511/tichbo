import { useState } from 'react';
import { ChevronDown, X, SlidersHorizontal } from 'lucide-react';
import type { EventFilters } from '../../types';

interface FilterOption { label: string; value: string; }

interface EventFiltersProps {
  filters: EventFilters;
  onChange: (filters: EventFilters) => void;
  options?: {
    languages?: FilterOption[];
    genres?: FilterOption[];
    formats?: FilterOption[];
    sports?: FilterOption[];
    cities?: FilterOption[];
    priceRanges?: FilterOption[];
  };
  showSport?: boolean;
  showFormat?: boolean;
  showLanguage?: boolean;
  showGenre?: boolean;
  showPrice?: boolean;
  showCity?: boolean;
}

function FilterSelect({
  label, options, value, onChange
}: { label: string; options: FilterOption[]; value?: string; onChange: (v: string) => void }) {
  return (
    <div className="relative">
      <select
        value={value || ''}
        onChange={e => onChange(e.target.value || '')}
        className="appearance-none bg-bg-tertiary border border-border rounded-lg pl-3 pr-8 py-2 text-sm text-text-secondary hover:border-accent/50 focus:border-accent focus:outline-none transition-colors cursor-pointer w-full"
      >
        <option value="">{label}</option>
        {options.map(o => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>
      <ChevronDown size={14} className="absolute right-2 top-1/2 -translate-y-1/2 text-text-muted pointer-events-none" />
    </div>
  );
}

export default function EventFilters({
  filters, onChange, options = {}, showSport, showFormat, showLanguage = true, showGenre = true
}: EventFiltersProps) {
  const [mobileOpen, setMobileOpen] = useState(false);

  const activeCount = Object.values(filters).filter(v => v !== undefined && v !== '').length;

  const clearAll = () => onChange({});

  const langs = options.languages || [
    { label: 'Hindi', value: 'Hindi' },
    { label: 'English', value: 'English' },
    { label: 'Telugu', value: 'Telugu' },
    { label: 'Tamil', value: 'Tamil' },
    { label: 'Kannada', value: 'Kannada' },
    { label: 'Malayalam', value: 'Malayalam' },
    { label: 'Punjabi', value: 'Punjabi' },
  ];

  const genres = options.genres || [
    { label: 'Action', value: 'Action' },
    { label: 'Drama', value: 'Drama' },
    { label: 'Comedy', value: 'Comedy' },
    { label: 'Thriller', value: 'Thriller' },
    { label: 'Romance', value: 'Romance' },
    { label: 'Horror', value: 'Horror' },
    { label: 'Sci-Fi', value: 'Sci-Fi' },
  ];

  const sports = options.sports || [
    { label: 'Cricket', value: 'Cricket' },
    { label: 'Football', value: 'Football' },
    { label: 'Basketball', value: 'Basketball' },
    { label: 'Tennis', value: 'Tennis' },
    { label: 'Badminton', value: 'Badminton' },
    { label: 'Kabaddi', value: 'Kabaddi' },
    { label: 'Formula 1', value: 'Formula 1' },
  ];

  const formats = options.formats || [
    { label: '2D', value: '2D' },
    { label: '3D', value: '3D' },
    { label: 'IMAX', value: 'IMAX' },
    { label: '4DX', value: '4DX' },
    { label: 'Dolby', value: 'Dolby' },
  ];

  const ratings = [
    { label: '9+ Outstanding', value: '9' },
    { label: '8+ Great', value: '8' },
    { label: '7+ Good', value: '7' },
    { label: '6+ Fair', value: '6' },
  ];

  const filterContent = (
    <div className="flex flex-wrap gap-2">
      {showLanguage && (
        <FilterSelect
          label="Language"
          options={langs}
          value={filters.language}
          onChange={v => onChange({ ...filters, language: v || undefined })}
        />
      )}
      {showGenre && (
        <FilterSelect
          label="Genre"
          options={genres}
          value={filters.genre}
          onChange={v => onChange({ ...filters, genre: v || undefined })}
        />
      )}
      {showSport && (
        <FilterSelect
          label="Sport"
          options={sports}
          value={filters.sport}
          onChange={v => onChange({ ...filters, sport: v || undefined })}
        />
      )}
      {showFormat && (
        <FilterSelect
          label="Format"
          options={formats}
          value={filters.format}
          onChange={v => onChange({ ...filters, format: v || undefined })}
        />
      )}
      <FilterSelect
        label="Rating"
        options={ratings}
        value={filters.minRating?.toString()}
        onChange={v => onChange({ ...filters, minRating: v ? parseFloat(v) : undefined })}
      />
      {activeCount > 0 && (
        <button onClick={clearAll} className="flex items-center gap-1.5 px-3 py-2 text-sm text-error hover:bg-error/10 rounded-lg transition-colors border border-error/30">
          <X size={13} /> Clear ({activeCount})
        </button>
      )}
    </div>
  );

  return (
    <div>
      {/* Desktop */}
      <div className="hidden md:block">{filterContent}</div>

      {/* Mobile */}
      <div className="md:hidden">
        <button
          onClick={() => setMobileOpen(!mobileOpen)}
          className="flex items-center gap-2 btn-secondary text-sm"
        >
          <SlidersHorizontal size={15} />
          Filters
          {activeCount > 0 && (
            <span className="bg-accent text-white text-xs rounded-full w-4 h-4 flex items-center justify-center">{activeCount}</span>
          )}
        </button>
        {mobileOpen && (
          <div className="mt-3 p-3 bg-bg-secondary border border-border rounded-xl space-y-2">
            {filterContent}
          </div>
        )}
      </div>
    </div>
  );
}

