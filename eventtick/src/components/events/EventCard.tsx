
import { useNavigate } from 'react-router-dom';
import {
  MapPin, Calendar, Star, Clock, ChevronRight, Zap
} from 'lucide-react';
import type { Content } from '../../types';

interface EventCardProps {
  event: Content;
  variant?: 'portrait' | 'landscape' | 'compact';
  className?: string;
}

function formatDate(dateStr?: string) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

function formatPrice(price?: number) {
  if (price === undefined) return '';
  if (price === 0) return 'Free';
  return `From ₹${price.toLocaleString('en-IN')}`;
}

export default function EventCard({ event, variant = 'portrait', className = '' }: EventCardProps) {
  const navigate = useNavigate();

  const handleClick = () => navigate(`/event/${event.id}`);

  const typeColors: Record<string, string> = {
    MOVIE: 'bg-blue-500/20 text-blue-400',
    SPORTS_MATCH: 'bg-green-500/20 text-green-400',
    CONCERT: 'bg-pink-500/20 text-pink-400',
    THEATRE: 'bg-yellow-500/20 text-yellow-400',
    EVENT: 'bg-purple-500/20 text-purple-400',
  };

  const typeLabel: Record<string, string> = {
    MOVIE: 'Movie',
    SPORTS_MATCH: 'Sports',
    CONCERT: 'Concert',
    THEATRE: 'Theatre',
    EVENT: 'Event',
  };

  if (variant === 'landscape') {
    return (
      <div
        onClick={handleClick}
        className={`card flex gap-0 cursor-pointer group hover:border-accent/40 transition-all duration-300 hover-lift ${className}`}
      >
        <div className="relative w-36 sm:w-48 flex-shrink-0 overflow-hidden">
          <img
            src={event.image}
            alt={event.title}
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
          />
          {event.trending && (
            <div className="absolute top-2 left-2 flex items-center gap-1 bg-accent text-white text-[10px] font-bold px-1.5 py-0.5 rounded-md">
              <Zap size={9} fill="currentColor" /> HOT
            </div>
          )}
        </div>
        <div className="flex flex-col justify-between p-4 flex-1 min-w-0">
          <div>
            <span className={`badge text-[10px] mb-2 ${typeColors[event.type]}`}>{typeLabel[event.type]}</span>
            <h3 className="text-text-primary font-semibold text-sm leading-snug mb-1 line-clamp-2 group-hover:text-accent-lighter transition-colors">
              {event.title}
            </h3>
            {event.genre && <p className="text-text-muted text-xs mb-2">{event.genre}</p>}
            <div className="space-y-1">
              {(event.venue || event.city) && (
                <div className="flex items-center gap-1.5 text-text-muted text-xs">
                  <MapPin size={11} className="flex-shrink-0" />
                  <span className="truncate">{[event.venue, event.city].filter(Boolean).join(', ')}</span>
                </div>
              )}
              {event.releaseDate && (
                <div className="flex items-center gap-1.5 text-text-muted text-xs">
                  <Calendar size={11} />
                  <span>{formatDate(event.releaseDate)}</span>
                </div>
              )}
            </div>
          </div>
          <div className="flex items-center justify-between mt-3">
            <span className="text-accent-lighter font-semibold text-sm">{formatPrice(event.price)}</span>
            {event.rating && (
              <div className="flex items-center gap-1 text-warning text-xs">
                <Star size={11} fill="currentColor" />
                <span>{event.rating.toFixed(1)}</span>
              </div>
            )}
          </div>
        </div>
      </div>
    );
  }

  if (variant === 'compact') {
    return (
      <div
        onClick={handleClick}
        className={`flex items-center gap-3 p-3 rounded-xl hover:bg-bg-tertiary cursor-pointer group transition-colors ${className}`}
      >
        <img src={event.image} alt={event.title} className="w-12 h-12 rounded-lg object-cover flex-shrink-0" />
        <div className="flex-1 min-w-0">
          <h4 className="text-text-primary text-sm font-medium truncate group-hover:text-accent-lighter transition-colors">{event.title}</h4>
          <p className="text-text-muted text-xs truncate">{event.city} • {formatDate(event.releaseDate)}</p>
        </div>
        <ChevronRight size={15} className="text-text-muted flex-shrink-0" />
      </div>
    );
  }

  // Portrait (default)
  return (
    <div
      onClick={handleClick}
      className={`card cursor-pointer group hover:border-accent/40 transition-all duration-300 hover-lift ${className}`}
    >
      {/* Image */}
      <div className="relative overflow-hidden aspect-[2/3]">
        <img
          src={event.image}
          alt={event.title}
          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
        />
        {/* Overlay */}
        <div className="absolute inset-0 bg-card-gradient" />
        {/* Badges */}
        <div className="absolute top-2 left-2 flex gap-1">
          {event.trending && (
            <span className="flex items-center gap-1 bg-accent text-white text-[10px] font-bold px-1.5 py-0.5 rounded-md">
              <Zap size={9} fill="currentColor" /> HOT
            </span>
          )}
          {!event.isReleased && event.type === 'MOVIE' && (
            <span className="bg-yellow-500/90 text-black text-[10px] font-bold px-1.5 py-0.5 rounded-md">
              COMING SOON
            </span>
          )}
        </div>
        {/* Rating */}
        {event.rating && (
          <div className="absolute bottom-2 right-2 flex items-center gap-1 bg-black/60 px-2 py-0.5 rounded-full">
            <Star size={11} className="text-warning" fill="currentColor" />
            <span className="text-white text-xs font-medium">{event.rating.toFixed(1)}</span>
          </div>
        )}
        {/* Type badge */}
        <div className="absolute bottom-2 left-2">
          <span className={`badge text-[10px] ${typeColors[event.type]}`}>{typeLabel[event.type]}</span>
        </div>
      </div>

      {/* Info */}
      <div className="p-3">
        <h3 className="text-text-primary font-semibold text-sm leading-snug mb-1 line-clamp-1 group-hover:text-accent-lighter transition-colors">
          {event.title}
        </h3>
        {event.genre && <p className="text-text-muted text-xs mb-1.5 truncate">{event.genre}</p>}
        <div className="space-y-0.5 mb-2">
          {event.language && (
            <p className="text-text-muted text-xs">{event.language}</p>
          )}
          {event.city && (
            <div className="flex items-center gap-1 text-text-muted text-xs">
              <MapPin size={10} />
              <span>{event.city}</span>
              {event.releaseDate && <span>• {formatDate(event.releaseDate)}</span>}
            </div>
          )}
          {event.duration && (
            <div className="flex items-center gap-1 text-text-muted text-xs">
              <Clock size={10} />
              <span>{Math.floor(event.duration / 60)}h {event.duration % 60}m</span>
            </div>
          )}
        </div>
        <div className="flex items-center justify-between">
          <span className="text-accent-lighter font-semibold text-xs">{formatPrice(event.price)}</span>
          <button className="text-xs bg-accent/10 text-accent-lighter border border-accent/30 px-2.5 py-1 rounded-lg hover:bg-accent hover:text-white transition-colors font-medium">
            Book
          </button>
        </div>
      </div>
    </div>
  );
}

