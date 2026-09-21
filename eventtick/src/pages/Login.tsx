import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Ticket } from 'lucide-react';
import { login } from '../services/api';
import { useApp } from '../context/AppContext';

export default function Login() {
  const { setUser } = useApp();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim() || !password) {
      setError('Email and password are required.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      setUser(await login({ email: email.trim(), password }));
      navigate('/');
    } catch {
      setError('Login failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-md mx-auto px-4 py-16">
      <div className="card p-8">
        <div className="flex flex-col items-center mb-6">
          <div className="w-12 h-12 rounded-xl bg-accent-gradient flex items-center justify-center mb-3">
            <Ticket size={22} className="text-white" />
          </div>
          <h1 className="text-2xl font-bold text-text-primary">Welcome back</h1>
          <p className="text-text-muted text-sm mt-1">Log in to book your next experience</p>
        </div>
        <form onSubmit={handleSubmit} className="space-y-4">
          <input type="email" className="input-field" placeholder="Email" value={email} onChange={e => setEmail(e.target.value)} autoComplete="email" />
          <input type="password" className="input-field" placeholder="Password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" />
          {error && <p className="text-error text-sm">{error}</p>}
          <button type="submit" disabled={loading} className="btn-primary w-full disabled:opacity-60">
            {loading ? 'Logging in…' : 'Log In'}
          </button>
        </form>
        <p className="text-text-muted text-sm text-center mt-6">
          New to Eventtick? <Link to="/signup" className="text-accent-lighter hover:underline">Sign up</Link>
        </p>
      </div>
    </div>
  );
}
