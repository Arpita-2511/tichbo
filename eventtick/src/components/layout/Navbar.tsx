
import { Link, useLocation } from 'react-router-dom';
import {
  Ticket, Search, MapPin, User, Menu, X, ChevronDown
} from 'lucide-react';
import { useApp } from '../../context/AppContext';

const navLinks = [
  { label: 'Home', path: '/' },
  { label: 'Movies', path: '/movies' },
  { label: 'Sports', path: '/sports' },
  { label: 'Concerts', path: '/concerts' },
  { label: 'Theatre', path: '/theatre' },
  { label: 'Events', path: '/events' },
];

export default function Navbar() {
  const location = useLocation();
  const {
    selectedCity, setIsLocationOpen, setIsSearchOpen,
    isLoggedIn, user, isMobileNavOpen, setIsMobileNavOpen
  } = useApp();

  const isActive = (path: string) =>
    path === '/' ? location.pathname === '/' : location.pathname.startsWith(path);

  return (
    <header className="sticky top-0 z-50 glass border-b border-border">
      <div className="max-w-[1400px] mx-auto px-4 sm:px-6 h-16 flex items-center gap-4">
        {/* Logo */}
        <Link to="/" className="flex items-center gap-2 flex-shrink-0 group">
          <div className="w-8 h-8 bg-accent-gradient rounded-lg flex items-center justify-center">
            <Ticket size={18} className="text-white" />
          </div>
          <span className="text-lg font-bold tracking-tight">
            <span className="text-text-primary">event</span>
            <span className="text-accent-lighter">tick</span>
          </span>
        </Link>

        {/* Desktop Nav */}
        <nav className="hidden lg:flex items-center gap-1 ml-4">
          {navLinks.map(link => (
            <Link
              key={link.path}
              to={link.path}
              className={`px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                isActive(link.path)
                  ? 'text-accent-lighter bg-accent/10'
                  : 'text-text-secondary hover:text-text-primary hover:bg-bg-tertiary'
              }`}
            >
              {link.label}
            </Link>
          ))}
        </nav>

        {/* Spacer */}
        <div className="flex-1" />

        {/* Right section */}
        <div className="flex items-center gap-2">
          {/* Search */}
          <button
            onClick={() => setIsSearchOpen(true)}
            className="flex items-center gap-2 bg-bg-tertiary border border-border hover:border-accent/50 rounded-lg px-3 py-2 text-sm text-text-muted hover:text-text-secondary transition-colors hidden sm:flex"
          >
            <Search size={15} />
            <span className="hidden md:block">Search events...</span>
          </button>

          {/* Location */}
          <button
            onClick={() => setIsLocationOpen(true)}
            className="hidden sm:flex items-center gap-1.5 text-sm text-text-secondary hover:text-text-primary transition-colors px-2 py-2 rounded-lg hover:bg-bg-tertiary"
          >
            <MapPin size={15} className="text-accent-lighter" />
            <span className="hidden lg:block max-w-[100px] truncate">{selectedCity}</span>
            <ChevronDown size={13} />
          </button>

          {/* Auth */}
          {isLoggedIn ? (
            <Link
              to="/profile"
              className="flex items-center gap-2 bg-bg-tertiary border border-border hover:border-accent/50 rounded-lg px-3 py-2 transition-colors"
            >
              <div className="w-6 h-6 bg-accent-gradient rounded-full flex items-center justify-center text-xs font-bold text-white">
                {user?.name?.charAt(0) || 'U'}
              </div>
              <span className="text-sm text-text-secondary hidden lg:block">{user?.name?.split(' ')[0]}</span>
            </Link>
          ) : (
            <div className="hidden sm:flex items-center gap-2">
              <Link to="/login" className="btn-ghost text-sm py-2 px-3">
                Login
              </Link>
              <Link to="/signup" className="btn-primary text-sm py-2 px-4">
                Sign Up
              </Link>
            </div>
          )}

          {/* Mobile search */}
          <button
            onClick={() => setIsSearchOpen(true)}
            className="sm:hidden p-2 rounded-lg hover:bg-bg-tertiary text-text-secondary"
          >
            <Search size={20} />
          </button>

          {/* Hamburger */}
          <button
            onClick={() => setIsMobileNavOpen(!isMobileNavOpen)}
            className="lg:hidden p-2 rounded-lg hover:bg-bg-tertiary text-text-secondary"
          >
            {isMobileNavOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
      </div>

      {/* Mobile Nav Drawer */}
      {isMobileNavOpen && (
        <div className="lg:hidden border-t border-border bg-bg-secondary animate-slide-up">
          <div className="max-w-[1400px] mx-auto px-4 py-3 space-y-1">
            {navLinks.map(link => (
              <Link
                key={link.path}
                to={link.path}
                onClick={() => setIsMobileNavOpen(false)}
                className={`flex items-center w-full px-3 py-3 rounded-lg text-sm font-medium transition-colors ${
                  isActive(link.path)
                    ? 'text-accent-lighter bg-accent/10'
                    : 'text-text-secondary hover:text-text-primary hover:bg-bg-tertiary'
                }`}
              >
                {link.label}
              </Link>
            ))}
            <div className="pt-2 border-t border-border mt-2 flex gap-2">
              <button
                onClick={() => { setIsLocationOpen(true); setIsMobileNavOpen(false); }}
                className="flex items-center gap-2 text-sm text-text-secondary hover:text-text-primary transition-colors py-2 px-3 rounded-lg hover:bg-bg-tertiary"
              >
                <MapPin size={15} className="text-accent-lighter" />
                {selectedCity}
              </button>
            </div>
            {!isLoggedIn && (
              <div className="flex gap-2 pt-1">
                <Link to="/login" onClick={() => setIsMobileNavOpen(false)} className="flex-1 btn-secondary text-sm text-center py-2">Login</Link>
                <Link to="/signup" onClick={() => setIsMobileNavOpen(false)} className="flex-1 btn-primary text-sm text-center py-2">Sign Up</Link>
              </div>
            )}
            {isLoggedIn && (
              <Link to="/profile" onClick={() => setIsMobileNavOpen(false)} className="flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-bg-tertiary text-text-secondary">
                <User size={16} />
                <span className="text-sm">{user?.name}</span>
              </Link>
            )}
          </div>
        </div>
      )}
    </header>
  );
}

