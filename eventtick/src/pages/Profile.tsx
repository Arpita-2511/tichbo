
import { Link, useNavigate } from 'react-router-dom';
import { User as UserIcon } from 'lucide-react';
import { logout } from '../services/api';
import { useApp, getPlanBadgeColor } from '../context/AppContext';
import EmptyState from '../components/common/EmptyState';

export default function Profile() {
  const { user, setUser } = useApp();
  const navigate = useNavigate();

  if (!user) {
    return (
      <EmptyState
        icon={<UserIcon size={24} />}
        title="You're not logged in"
        description="Log in to view your profile."
        action={<Link to="/login" className="btn-primary">Log In</Link>}
      />
    );
  }

  const handleLogout = async () => {
    await logout();
    setUser(null);
    navigate('/');
  };

  const rows: [string, string][] = [
    ['Email', user.email],
    ['Phone', user.phone ?? '—'],
    ['City', user.city ?? '—'],
    ['Member since', new Date(user.createdAt).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' })],
  ];

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 py-10">
      <div className="card p-6">
        <div className="flex items-center gap-4 mb-6">
          <div className="w-14 h-14 rounded-full bg-accent-gradient flex items-center justify-center text-white text-xl font-bold">
            {user.name.charAt(0).toUpperCase()}
          </div>
          <div className="flex-1 min-w-0">
            <h1 className="text-xl font-bold text-text-primary truncate">{user.name}</h1>
            <span className={`badge border text-[10px] ${getPlanBadgeColor(user.plan)}`}>{user.plan}</span>
          </div>
        </div>
        <dl className="divide-y divide-border">
          {rows.map(([k, v]) => (
            <div key={k} className="flex justify-between py-3 text-sm">
              <dt className="text-text-muted">{k}</dt>
              <dd className="text-text-primary">{v}</dd>
            </div>
          ))}
        </dl>
        <div className="flex flex-wrap gap-3 mt-6">
          <Link to="/bookings" className="btn-secondary text-sm">My Bookings</Link>
          <Link to="/plans" className="btn-secondary text-sm">Manage Plan</Link>
          <button onClick={handleLogout} className="btn-ghost text-sm text-error">Log Out</button>
        </div>
      </div>
    </div>
  );
}
