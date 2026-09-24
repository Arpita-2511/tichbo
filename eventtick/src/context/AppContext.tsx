import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import type { User, SubscriptionPlan } from '../types';
import { cities } from '../data/mockData';
import { getCurrentUser } from '../services/api';

interface AppContextType {
  // City selection
  selectedCity: string;
  setSelectedCity: (city: string) => void;

  // Auth
  user: User | null;
  setUser: (user: User | null) => void;
  isLoggedIn: boolean;

  // Search
  searchQuery: string;
  setSearchQuery: (q: string) => void;
  isSearchOpen: boolean;
  setIsSearchOpen: (open: boolean) => void;

  // Location modal
  isLocationOpen: boolean;
  setIsLocationOpen: (open: boolean) => void;

  // Mobile nav
  isMobileNavOpen: boolean;
  setIsMobileNavOpen: (open: boolean) => void;

  // Cities list
  cities: string[];
}

const AppContext = createContext<AppContextType | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [selectedCity, setSelectedCity] = useState('Mumbai');
  const [user, setUser] = useState<User | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isLocationOpen, setIsLocationOpen] = useState(false);
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false);

  useEffect(() => {
    // Restores the session after a page load: getCurrentUser() checks for a
    // stored JWT and, if there is one, validates it via GET /api/users/me.
    // No token, or a rejected one, resolves to null (logged out).
    getCurrentUser().then(u => setUser(u));
  }, []);

  return (
    <AppContext.Provider
      value={{
        selectedCity,
        setSelectedCity,
        user,
        setUser,
        isLoggedIn: !!user,
        searchQuery,
        setSearchQuery,
        isSearchOpen,
        setIsSearchOpen,
        isLocationOpen,
        setIsLocationOpen,
        isMobileNavOpen,
        setIsMobileNavOpen,
        cities,
      }}
    >
      {children}
    </AppContext.Provider>
  );
}

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used inside AppProvider');
  return ctx;
}

// Convenience subscription plan accessor
export function getPlanBadgeColor(plan: SubscriptionPlan) {
  switch (plan) {
    case 'VIP': return 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30';
    case 'PREMIUM': return 'bg-accent/20 text-accent-lighter border-accent/30';
    default: return 'bg-border text-text-secondary border-border-light';
  }
}

