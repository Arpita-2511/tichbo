
import { useNavigate } from 'react-router-dom';
import {
  Film, Trophy, Music, Theater, Calendar, ArrowRight
} from 'lucide-react';
import type { ContentType } from '../../types';

interface CategoryCardProps {
  type: ContentType;
  className?: string;
}

const categories: Record<ContentType, {
  icon: React.ReactNode;
  label: string;
  desc: string;
  path: string;
  color: string;
  bg: string;
}> = {
  MOVIE: {
    icon: <Film size={28} />,
    label: 'Movies',
    desc: 'Latest releases & blockbusters',
    path: '/movies',
    color: 'text-blue-400',
    bg: 'bg-blue-500/10 border-blue-500/20 hover:border-blue-500/50 hover:bg-blue-500/15',
  },
  SPORTS_MATCH: {
    icon: <Trophy size={28} />,
    label: 'Sports',
    desc: 'Cricket, football, tennis & more',
    path: '/sports',
    color: 'text-green-400',
    bg: 'bg-green-500/10 border-green-500/20 hover:border-green-500/50 hover:bg-green-500/15',
  },
  CONCERT: {
    icon: <Music size={28} />,
    label: 'Concerts',
    desc: 'Live music & artist performances',
    path: '/concerts',
    color: 'text-pink-400',
    bg: 'bg-pink-500/10 border-pink-500/20 hover:border-pink-500/50 hover:bg-pink-500/15',
  },
  THEATRE: {
    icon: <Theater size={28} />,
    label: 'Theatre',
    desc: 'Plays, musicals & stand-up',
    path: '/theatre',
    color: 'text-yellow-400',
    bg: 'bg-yellow-500/10 border-yellow-500/20 hover:border-yellow-500/50 hover:bg-yellow-500/15',
  },
  EVENT: {
    icon: <Calendar size={28} />,
    label: 'Events',
    desc: 'Festivals, conferences & more',
    path: '/events',
    color: 'text-purple-400',
    bg: 'bg-purple-500/10 border-purple-500/20 hover:border-purple-500/50 hover:bg-purple-500/15',
  },
};

export default function EventCategoryCard({ type, className = '' }: CategoryCardProps) {
  const navigate = useNavigate();
  const cat = categories[type];

  return (
    <button
      onClick={() => navigate(cat.path)}
      className={`group flex flex-col items-center gap-3 p-5 rounded-2xl border transition-all duration-300 cursor-pointer hover-lift text-center ${cat.bg} ${className}`}
    >
      <div className={`${cat.color} group-hover:scale-110 transition-transform duration-300`}>
        {cat.icon}
      </div>
      <div>
        <h3 className={`font-semibold text-sm ${cat.color}`}>{cat.label}</h3>
        <p className="text-text-muted text-xs mt-0.5 leading-snug">{cat.desc}</p>
      </div>
      <div className={`flex items-center gap-1 text-xs ${cat.color} opacity-0 group-hover:opacity-100 transition-opacity`}>
        <span>Explore</span>
        <ArrowRight size={11} />
      </div>
    </button>
  );
}

// Full category nav row
export function CategoryNav() {
  const types: ContentType[] = ['MOVIE', 'SPORTS_MATCH', 'CONCERT', 'THEATRE', 'EVENT'];
  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
      {types.map(t => (
        <EventCategoryCard key={t} type={t} />
      ))}
    </div>
  );
}

