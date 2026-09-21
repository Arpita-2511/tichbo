import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  MapPin, Calendar, Clock, Star, Share2, Heart, ChevronRight,
  Globe, Shield, Info
} from 'lucide-react';
import { getEventById, getShowsByEvent, getVenueById } from '../services/api';
import type { Content, Show, Venue } from '../types';
import Loading from '../components/common/Loading';

function formatDate(str: string) {
  return new Date(str).toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
}
function shortDate(str: string) {
  const d = new Date(str);
  return { day: d.getDate(), month: d.toLocaleString('en-IN', { month: 'short' }).toUpperCase() };
}

export default function EventDetails() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [event, setEvent] = useState<Content | null>(null);
  const [shows, setShows] = useState<Show[]>([]);
  const [venue, setVenue] = useState<Venue | null>(null);
  const [loading, setLoading] = useState(true);
  const [selectedDate, setSelectedDate] = useState<string>('');
  const [selectedShow, setSelectedShow] = useState<Show | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!id) return;
    Promise.all([getEventById(id), getShowsByEvent(id)]).then(async ([ev, sh]) => {
      setEvent(ev);
      setShows(sh);
      if (sh.length > 0) {
        setSelectedDate(sh[0].date);
        // Load venue for first show
        const v = await getVenueById(sh[0].venueId);
        setVenue(v);
      }
      setLoading(false);
    });
  }, [id]);

  // Unique dates from shows
  const dates = Array.from(new Set(shows.map(s => s.date))).sort();
  const showsOnDate = shows.filter(s => s.date === selectedDate);

  const handleBookNow = () => {
    if (selectedShow) {
      navigate(`/booking/${selectedShow.id}/seats`, {
        state: { event, show: selectedShow, venue }
      });
    } else if (shows.length > 0) {
      navigate(`/booking/${shows[0].id}/seats`, {
        state: { event, show: shows[0], venue }
      });
    }
  };

  if (loading) return <div className="min-h-screen flex items-center justify-center"><Loading /></div>;
  if (!event) return (
    <div className="min-h-screen flex items-center justify-center text-text-muted">
      Event not found. <a href="/" className="text-accent ml-2">Go Home</a>
    </div>
  );

  const typeColors: Record<string, string> = {
    MOVIE: 'bg-blue-500/20 text-blue-400',
    SPORTS_MATCH: 'bg-green-500/20 text-green-400',
    CONCERT: 'bg-pink-500/20 text-pink-400',
    THEATRE: 'bg-yellow-500/20 text-yellow-400',
    EVENT: 'bg-purple-500/20 text-purple-400',
  };
  const typeLabel: Record<string, string> = {
    MOVIE: 'Movie', SPORTS_MATCH: 'Sports', CONCERT: 'Concert', THEATRE: 'Theatre', EVENT: 'Event'
  };

  return (
    <div className="min-h-screen">
      {/* ── HERO ──────────────────────────────────────────────────────────── */}
      <div className="relative h-72 sm:h-96 overflow-hidden">
        <img
          src={event.bannerImage || event.image}
          alt={event.title}
          className="w-full h-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-bg-primary via-bg-primary/60 to-transparent" />
        <div className="absolute inset-0 bg-gradient-to-r from-bg-primary/80 to-transparent" />
      </div>

      <div className="max-w-[1400px] mx-auto px-4 sm:px-6 -mt-32 relative z-10">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* ── LEFT / MAIN ───────────────────────────────────────────────── */}
          <div className="lg:col-span-2 space-y-6">
            {/* Identity */}
            <div>
              <div className="flex flex-wrap items-center gap-2 mb-3">
                <span className={`badge ${typeColors[event.type]}`}>{typeLabel[event.type]}</span>
                {event.genre && <span className="text-text-muted text-xs">{event.genre}</span>}
                {event.certificate && (
                  <span className="border border-border-light text-text-muted text-xs px-1.5 py-0.5 rounded">{event.certificate}</span>
                )}
              </div>
              <h1 className="text-3xl sm:text-4xl font-black text-text-primary mb-2">{event.title}</h1>
              {event.type === 'SPORTS_MATCH' && event.teams && (
                <p className="text-text-secondary text-lg mb-1">{event.teams.join(' vs ')}</p>
              )}
              {event.artist && <p className="text-text-secondary text-lg mb-1">{event.artist}</p>}

              {/* Meta row */}
              <div className="flex flex-wrap gap-4 mt-3">
                {event.rating && (
                  <div className="flex items-center gap-1.5">
                    <Star size={15} className="text-warning" fill="currentColor" />
                    <span className="text-text-primary font-semibold">{event.rating.toFixed(1)}</span>
                    <span className="text-text-muted text-sm">({(event.ratingCount || 0).toLocaleString('en-IN')} ratings)</span>
                  </div>
                )}
                {event.duration && (
                  <div className="flex items-center gap-1.5 text-text-secondary text-sm">
                    <Clock size={14} />
                    {Math.floor(event.duration / 60)}h {event.duration % 60}m
                  </div>
                )}
                {event.language && (
                  <div className="flex items-center gap-1.5 text-text-secondary text-sm">
                    <Globe size={14} />
                    {event.language}
                  </div>
                )}
              </div>
            </div>

            {/* ── DATE SELECTION ────────────────────────────────────────── */}
            {dates.length > 0 && (
              <div className="bg-bg-secondary border border-border rounded-xl p-5">
                <h3 className="text-text-primary font-semibold mb-4 flex items-center gap-2">
                  <Calendar size={16} className="text-accent-lighter" /> Select Date
                </h3>
                <div className="flex gap-2 overflow-x-auto scrollbar-hide pb-1">
                  {dates.map(date => {
                    const { day, month } = shortDate(date);
                    return (
                      <button
                        key={date}
                        onClick={() => setSelectedDate(date)}
                        className={`flex-none flex flex-col items-center w-16 py-3 rounded-xl border transition-all ${
                          selectedDate === date
                            ? 'bg-accent border-accent text-white'
                            : 'bg-bg-tertiary border-border text-text-secondary hover:border-accent/50'
                        }`}
                      >
                        <span className="text-xs font-medium">{month}</span>
                        <span className="text-xl font-bold">{day}</span>
                      </button>
                    );
                  })}
                </div>
              </div>
            )}

            {/* ── SHOW SELECTION ────────────────────────────────────────── */}
            {showsOnDate.length > 0 && (
              <div className="bg-bg-secondary border border-border rounded-xl p-5">
                <h3 className="text-text-primary font-semibold mb-4 flex items-center gap-2">
                  <Clock size={16} className="text-accent-lighter" /> Available Shows
                </h3>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                  {showsOnDate.map(show => (
                    <button
                      key={show.id}
                      onClick={() => setSelectedShow(show)}
                      className={`flex flex-col p-3 rounded-xl border transition-all ${
                        selectedShow?.id === show.id
                          ? 'bg-accent/20 border-accent text-accent-lighter'
                          : 'bg-bg-tertiary border-border text-text-secondary hover:border-accent/40'
                      }`}
                    >
                      <span className="font-bold text-sm">{show.timeLabel}</span>
                      {show.language && <span className="text-xs mt-0.5">{show.language}</span>}
                      {show.format && <span className="text-xs opacity-70">{show.format}</span>}
                      {show.seatsAvailable !== undefined && (
                        <span className={`text-[10px] mt-1 font-medium ${show.seatsAvailable < 50 ? 'text-warning' : 'text-success'}`}>
                          {show.seatsAvailable < 50 ? '⚠ Filling fast' : '✓ Available'}
                        </span>
                      )}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* ── VENUE ─────────────────────────────────────────────────── */}
            {venue && (
              <div className="bg-bg-secondary border border-border rounded-xl p-5">
                <h3 className="text-text-primary font-semibold mb-3 flex items-center gap-2">
                  <MapPin size={16} className="text-accent-lighter" /> Venue
                </h3>
                <p className="text-text-primary font-medium">{venue.name}</p>
                <p className="text-text-muted text-sm">{venue.address}, {venue.city}, {venue.state} - {venue.pincode}</p>
                {venue.amenities && (
                  <div className="flex flex-wrap gap-1.5 mt-3">
                    {venue.amenities.map(a => (
                      <span key={a} className="text-[11px] bg-bg-tertiary border border-border text-text-secondary px-2 py-0.5 rounded-full">{a}</span>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* ── ABOUT ─────────────────────────────────────────────────── */}
            <div className="bg-bg-secondary border border-border rounded-xl p-5">
              <h3 className="text-text-primary font-semibold mb-3 flex items-center gap-2">
                <Info size={16} className="text-accent-lighter" /> About
              </h3>
              <p className="text-text-secondary text-sm leading-relaxed">{event.description}</p>
              {event.cast && event.cast.length > 0 && (
                <div className="mt-4">
                  <p className="text-text-muted text-xs font-semibold mb-2 uppercase tracking-wide">Cast</p>
                  <p className="text-text-secondary text-sm">{event.cast.join(', ')}</p>
                </div>
              )}
              {event.director && (
                <div className="mt-3">
                  <p className="text-text-muted text-xs font-semibold mb-1 uppercase tracking-wide">Director</p>
                  <p className="text-text-secondary text-sm">{event.director}</p>
                </div>
              )}
            </div>

            {/* ── T&C ───────────────────────────────────────────────────── */}
            <div className="bg-bg-secondary border border-border rounded-xl p-5">
              <h3 className="text-text-primary font-semibold mb-3 flex items-center gap-2">
                <Shield size={16} className="text-accent-lighter" /> Terms & Conditions
              </h3>
              <ul className="text-text-muted text-xs space-y-1.5 list-disc list-inside leading-relaxed">
                <li>Tickets once booked cannot be exchanged or refunded.</li>
                <li>Outside food and beverages are not permitted.</li>
                <li>Carry a valid ID proof matching the ticket name.</li>
                <li>Entry is subject to frisking and security checks.</li>
                <li>Children below 12 years must have adult supervision.</li>
                <li>Management reserves the right to change the event without prior notice.</li>
              </ul>
            </div>
          </div>

          {/* ── RIGHT / BOOKING PANEL ─────────────────────────────────────── */}
          <div className="lg:sticky lg:top-20 h-fit">
            <div className="bg-bg-secondary border border-border rounded-xl overflow-hidden">
              <img
                src={event.image}
                alt={event.title}
                className="w-full h-48 object-cover"
              />
              <div className="p-5 space-y-4">
                <div>
                  <p className="text-text-muted text-xs mb-1">Starting from</p>
                  <p className="text-2xl font-black text-accent-lighter">
                    ₹{(event.price || 0).toLocaleString('en-IN')}
                  </p>
                </div>

                {selectedShow && (
                  <div className="bg-bg-tertiary border border-border rounded-lg p-3 text-sm space-y-1">
                    <div className="flex justify-between">
                      <span className="text-text-muted">Date</span>
                      <span className="text-text-primary">{formatDate(selectedDate)}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-text-muted">Time</span>
                      <span className="text-text-primary">{selectedShow.timeLabel}</span>
                    </div>
                    {venue && (
                      <div className="flex justify-between">
                        <span className="text-text-muted">Venue</span>
                        <span className="text-text-primary text-right max-w-[180px] truncate">{venue.name}</span>
                      </div>
                    )}
                  </div>
                )}

                <button
                  onClick={handleBookNow}
                  disabled={shows.length === 0}
                  className="w-full btn-primary flex items-center justify-center gap-2"
                >
                  {shows.length === 0 ? 'No Shows Available' : 'Book Tickets'}
                  {shows.length > 0 && <ChevronRight size={16} />}
                </button>

                <div className="flex gap-2">
                  <button
                    onClick={() => setSaved(!saved)}
                    className={`flex-1 flex items-center justify-center gap-2 py-2 rounded-lg border text-sm transition-colors ${
                      saved ? 'bg-error/10 border-error/30 text-error' : 'bg-bg-tertiary border-border text-text-secondary hover:border-border-light'
                    }`}
                  >
                    <Heart size={14} fill={saved ? 'currentColor' : 'none'} />
                    {saved ? 'Saved' : 'Save'}
                  </button>
                  <button className="flex-1 flex items-center justify-center gap-2 py-2 rounded-lg border border-border bg-bg-tertiary text-text-secondary hover:border-border-light text-sm transition-colors">
                    <Share2 size={14} /> Share
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

