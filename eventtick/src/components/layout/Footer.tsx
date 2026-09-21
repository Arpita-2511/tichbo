
import { Link } from 'react-router-dom';
import { Ticket, Globe, Camera, Play, Users, Mail } from 'lucide-react';

const footerLinks = {
  Company: [
    { label: 'About Us', path: '#' },
    { label: 'Careers', path: '#' },
    { label: 'Press', path: '#' },
    { label: 'Blog', path: '#' },
  ],
  Explore: [
    { label: 'Movies', path: '/movies' },
    { label: 'Sports', path: '/sports' },
    { label: 'Concerts', path: '/concerts' },
    { label: 'Theatre', path: '/theatre' },
    { label: 'Events', path: '/events' },
  ],
  Support: [
    { label: 'Help Centre', path: '#' },
    { label: 'Cancellation Policy', path: '#' },
    { label: 'Contact Us', path: '#' },
    { label: 'Report an Issue', path: '#' },
  ],
  Account: [
    { label: 'Login', path: '/login' },
    { label: 'Sign Up', path: '/signup' },
    { label: 'My Bookings', path: '/bookings' },
    { label: 'Subscription Plans', path: '/plans' },
  ],
};

export default function Footer() {
  return (
    <footer className="bg-bg-secondary border-t border-border mt-auto">
      <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-12">
        {/* Top */}
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-8 mb-10">
          {/* Brand */}
          <div className="col-span-2 md:col-span-3 lg:col-span-1">
            <Link to="/" className="flex items-center gap-2 mb-4">
              <div className="w-8 h-8 bg-accent-gradient rounded-lg flex items-center justify-center">
                <Ticket size={18} className="text-white" />
              </div>
              <span className="text-lg font-bold">
                <span className="text-text-primary">event</span>
                <span className="text-accent-lighter">tick</span>
              </span>
            </Link>
            <p className="text-text-muted text-sm leading-relaxed mb-4">
              Your ticket to every experience. Discover and book tickets for movies, sports, concerts, theatre, and live events.
            </p>
            <div className="flex items-center gap-3">
              {[Globe, Camera, Play, Users].map((Icon, i) => (
                <a key={i} href="#" className="w-8 h-8 rounded-lg bg-bg-tertiary hover:bg-accent/20 flex items-center justify-center text-text-muted hover:text-accent-lighter transition-colors">
                  <Icon size={15} />
                </a>
              ))}
            </div>
          </div>

          {/* Links */}
          {Object.entries(footerLinks).map(([section, links]) => (
            <div key={section}>
              <h4 className="text-text-primary font-semibold text-sm mb-3">{section}</h4>
              <ul className="space-y-2">
                {links.map(link => (
                  <li key={link.label}>
                    <Link
                      to={link.path}
                      className="text-text-muted hover:text-text-secondary text-sm transition-colors"
                    >
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        {/* Newsletter */}
        <div className="border-t border-border pt-8 mb-8">
          <div className="flex flex-col sm:flex-row gap-4 items-start sm:items-center justify-between">
            <div>
              <h4 className="text-text-primary font-semibold mb-1">Stay in the loop</h4>
              <p className="text-text-muted text-sm">Get exclusive deals and event alerts in your city.</p>
            </div>
            <div className="flex gap-2 w-full sm:w-auto">
              <div className="relative flex-1 sm:flex-none">
                <Mail size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
                <input
                  type="email"
                  placeholder="Enter your email"
                  className="input-field pl-9 py-2.5 text-sm w-full sm:w-64"
                />
              </div>
              <button className="btn-primary text-sm py-2.5 whitespace-nowrap">Subscribe</button>
            </div>
          </div>
        </div>

        {/* Bottom */}
        <div className="border-t border-border pt-6 flex flex-col sm:flex-row gap-3 items-center justify-between text-text-muted text-xs">
          <p>© {new Date().getFullYear()} Eventtick Technologies Pvt. Ltd. All rights reserved.</p>
          <div className="flex items-center gap-4">
            <a href="#" className="hover:text-text-secondary transition-colors">Privacy Policy</a>
            <a href="#" className="hover:text-text-secondary transition-colors">Terms of Service</a>
            <a href="#" className="hover:text-text-secondary transition-colors">Cookie Policy</a>
          </div>
        </div>
      </div>
    </footer>
  );
}

