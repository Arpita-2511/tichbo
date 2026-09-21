import { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
  Search, MapPin, ArrowRight, Star,
  Shield, Zap, HeadphonesIcon, Award, Sparkles
} from 'lucide-react';
import { useApp } from '../context/AppContext';
import { getTrendingEvents, getEvents } from '../services/api';
import type { Content } from '../types';
import EventCard from '../components/events/EventCard';
import { CategoryNav } from '../components/events/EventCategoryCard';
import { EventCardSkeleton } from '../components/common/Loading';

const heroImages = [
  'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?w=1400&h=700&fit=crop',
  'https://images.unsplash.com/photo-1540747913346-19212a4d8c47?w=1400&h=700&fit=crop',
  'https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=1400&h=700&fit=crop',
];

function SectionHeader({ title, subtitle, viewAllPath }: { title: string; subtitle?: string; viewAllPath?: string }) {
  return (
    <div className="flex items-end justify-between mb-5">
      <div>
        <h2 className="section-title">{title}</h2>
        {subtitle && <p className="section-subtitle">{subtitle}</p>}
      </div>
      {viewAllPath && (
        <Link to={viewAllPath} className="flex items-center gap-1 text-accent-lighter hover:text-accent text-sm font-medium transition-colors">
          View All <ArrowRight size={14} />
        </Link>
      )}
    </div>
  );
}

function HorizontalScroll({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex gap-4 overflow-x-auto scrollbar-hide pb-2 -mx-4 px-4">
      {children}
    </div>
  );
}

export default function Home() {
  const { selectedCity, setIsSearchOpen, setIsLocationOpen } = useApp();
  const navigate = useNavigate();
  const [searchInput, setSearchInput] = useState('');
  const [heroIndex, setHeroIndex] = useState(0);
  const [trending, setTrending] = useState<Content[]>([]);
  const [movies, setMovies] = useState<Content[]>([]);
  const [sports, setSports] = useState<Content[]>([]);
  const [concerts, setConcerts] = useState<Content[]>([]);
  const [theatre, setTheatre] = useState<Content[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const timer = setInterval(() => setHeroIndex(i => (i + 1) % heroImages.length), 5000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    Promise.all([
      getTrendingEvents(),
      getEvents('MOVIE'),
      getEvents('SPORTS_MATCH'),
      getEvents('CONCERT'),
      getEvents('THEATRE'),
    ]).then(([t, m, s, c, th]) => {
      setTrending(t);
      setMovies(m.slice(0, 6));
      setSports(s.slice(0, 6));
      setConcerts(c.slice(0, 5));
      setTheatre(th.slice(0, 5));
      setLoading(false);
    });
  }, []);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setIsSearchOpen(true);
  };

  return (
    <div className="min-h-screen">
      {/* ── HERO ─────────────────────────────────────────────────────────── */}
      <section className="relative h-[480px] sm:h-[520px] overflow-hidden">
        {/* Background cycling images */}
        {heroImages.map((img, i) => (
          <div
            key={img}
            className="absolute inset-0 transition-opacity duration-1000"
            style={{ opacity: i === heroIndex ? 1 : 0 }}
          >
            <img src={img} alt="hero" className="w-full h-full object-cover" />
          </div>
        ))}
        {/* Overlay */}
        <div className="absolute inset-0 bg-hero-gradient" />
        <div className="absolute inset-0 bg-gradient-to-r from-bg-primary/80 via-bg-primary/40 to-transparent" />

        {/* Content */}
        <div className="relative z-10 max-w-[1400px] mx-auto px-4 sm:px-6 h-full flex flex-col justify-center">
          <div className="max-w-2xl">
            <div className="flex items-center gap-2 mb-4">
              <Sparkles size={14} className="text-accent-lighter" />
              <span className="text-accent-lighter text-sm font-medium">Your ticket to every experience</span>
            </div>
            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-black text-white leading-tight mb-4">
              Discover Your<br />
              <span className="text-gradient">Next Experience</span>
            </h1>
            <p className="text-text-secondary text-base sm:text-lg max-w-xl mb-8">
              Book tickets for movies, sports, concerts, theatre and unforgettable live events.
            </p>

            {/* Search + Location */}
            <form onSubmit={handleSearch} className="flex flex-col sm:flex-row gap-3 max-w-xl">
              <div className="flex-1 relative">
                <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-text-muted" />
                <input
                  type="text"
                  value={searchInput}
                  onChange={e => setSearchInput(e.target.value)}
                  onFocus={() => setIsSearchOpen(true)}
                  placeholder="Search movies, teams, artists..."
                  className="w-full bg-bg-secondary/90 border border-border/60 hover:border-accent/50 focus:border-accent rounded-xl pl-10 pr-4 py-3 text-text-primary placeholder-text-muted focus:outline-none transition-colors text-sm"
                  readOnly
                />
              </div>
              <button
                type="button"
                onClick={() => setIsLocationOpen(true)}
                className="flex items-center gap-2 bg-bg-secondary/90 border border-border/60 hover:border-accent/50 rounded-xl px-4 py-3 text-text-secondary text-sm transition-colors whitespace-nowrap"
              >
                <MapPin size={15} className="text-accent-lighter" />
                {selectedCity}
              </button>
            </form>

            <button
              onClick={() => navigate('/events')}
              className="mt-4 btn-primary inline-flex items-center gap-2"
            >
              Explore Events <ArrowRight size={15} />
            </button>
          </div>
        </div>

        {/* Dots */}
        <div className="absolute bottom-5 left-1/2 -translate-x-1/2 flex gap-2">
          {heroImages.map((_, i) => (
            <button
              key={i}
              onClick={() => setHeroIndex(i)}
              className={`w-1.5 h-1.5 rounded-full transition-all ${i === heroIndex ? 'bg-accent w-4' : 'bg-white/40'}`}
            />
          ))}
        </div>
      </section>

      <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-10 space-y-14">
        {/* ── CATEGORIES ─────────────────────────────────────────────────── */}
        <section>
          <SectionHeader title="Explore by Category" subtitle="What are you looking for today?" />
          <CategoryNav />
        </section>

        {/* ── TRENDING ───────────────────────────────────────────────────── */}
        <section>
          <SectionHeader title="Trending Near You" subtitle={`Hot events in ${selectedCity}`} viewAllPath="/events" />
          {loading ? (
            <HorizontalScroll>
              {[...Array(5)].map((_, i) => (
                <div key={i} className="flex-none w-64"><EventCardSkeleton /></div>
              ))}
            </HorizontalScroll>
          ) : (
            <HorizontalScroll>
              {trending.map(event => (
                <div key={event.id} className="flex-none w-56 sm:w-64">
                  <EventCard event={event} />
                </div>
              ))}
            </HorizontalScroll>
          )}
        </section>

        {/* ── MOVIES ─────────────────────────────────────────────────────── */}
        <section>
          <SectionHeader title="Popular Movies" subtitle="Now showing & coming soon" viewAllPath="/movies" />
          {loading ? (
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
              {[...Array(6)].map((_, i) => <EventCardSkeleton key={i} />)}
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
              {movies.map(m => <EventCard key={m.id} event={m} />)}
            </div>
          )}
        </section>

        {/* ── SPORTS ─────────────────────────────────────────────────────── */}
        <section>
          <SectionHeader title="Sports Events" subtitle="Cricket, football, tennis & more" viewAllPath="/sports" />
          {loading ? (
            <div className="space-y-3">
              {[...Array(3)].map((_, i) => (
                <div key={i} className="h-28 bg-bg-tertiary animate-skeleton rounded-xl" />
              ))}
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {sports.slice(0, 6).map(s => <EventCard key={s.id} event={s} variant="landscape" />)}
            </div>
          )}
        </section>

        {/* ── CONCERTS ───────────────────────────────────────────────────── */}
        <section>
          <SectionHeader title="Upcoming Concerts" subtitle="Live music experiences" viewAllPath="/concerts" />
          <HorizontalScroll>
            {loading
              ? [...Array(4)].map((_, i) => <div key={i} className="flex-none w-72 h-48 bg-bg-tertiary animate-skeleton rounded-xl" />)
              : concerts.map(c => (
                  <div key={c.id} className="flex-none w-72">
                    <EventCard event={c} variant="landscape" />
                  </div>
                ))}
          </HorizontalScroll>
        </section>

        {/* ── THEATRE ────────────────────────────────────────────────────── */}
        <section>
          <SectionHeader title="Theatre & Live Performances" subtitle="Plays, musicals, stand-up comedy" viewAllPath="/theatre" />
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {loading
              ? [...Array(3)].map((_, i) => <EventCardSkeleton key={i} />)
              : theatre.slice(0, 3).map(t => <EventCard key={t.id} event={t} variant="landscape" />)}
          </div>
        </section>

        {/* ── WHY EVENTTICK ──────────────────────────────────────────────── */}
        <section className="bg-bg-secondary border border-border rounded-2xl p-8">
          <div className="text-center mb-8">
            <h2 className="section-title">Why Eventtick?</h2>
            <p className="section-subtitle mt-1">Built for the modern entertainment enthusiast</p>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {[
              { icon: <Zap size={22} />, color: 'text-accent-lighter bg-accent/10', title: 'Lightning Fast', desc: 'Book tickets in seconds with our optimised checkout flow.' },
              { icon: <Shield size={22} />, color: 'text-success bg-success/10', title: 'Secure Payments', desc: 'Bank-grade encryption and trusted payment gateways.' },
              { icon: <Award size={22} />, color: 'text-yellow-400 bg-yellow-500/10', title: 'Best Seats', desc: 'Interactive seat maps so you always get the view you want.' },
              { icon: <HeadphonesIcon size={22} />, color: 'text-pink-400 bg-pink-500/10', title: '24/7 Support', desc: 'Got a question? Our support team is always here.' },
            ].map(item => (
              <div key={item.title} className="flex flex-col items-center text-center gap-3">
                <div className={`w-12 h-12 rounded-2xl flex items-center justify-center ${item.color}`}>
                  {item.icon}
                </div>
                <div>
                  <h3 className="text-text-primary font-semibold mb-1">{item.title}</h3>
                  <p className="text-text-muted text-sm leading-relaxed">{item.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* ── PROMO BANNER ───────────────────────────────────────────────── */}
        <section>
          <div className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-accent/30 via-accent/10 to-bg-secondary border border-accent/30 p-8 sm:p-12">
            <div className="absolute right-0 top-0 bottom-0 w-1/3 bg-gradient-to-l from-accent/20 to-transparent" />
            <div className="relative z-10 max-w-xl">
              <div className="flex items-center gap-2 mb-3">
                <Star size={14} className="text-yellow-400" fill="currentColor" />
                <span className="text-yellow-400 text-sm font-medium">Eventtick Premium</span>
              </div>
              <h2 className="text-2xl sm:text-3xl font-black text-text-primary mb-3">
                Unlock priority access to every experience
              </h2>
              <p className="text-text-secondary text-sm mb-6 leading-relaxed">
                Get early access, exclusive offers, and zero convenience fees with Premium and VIP plans.
              </p>
              <Link to="/plans" className="btn-primary inline-flex items-center gap-2">
                View Plans <ArrowRight size={15} />
              </Link>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}

