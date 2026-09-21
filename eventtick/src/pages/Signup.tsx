import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Ticket } from 'lucide-react';
import { signup } from '../services/api';
import { useApp } from '../context/AppContext';

export default function Signup() {
  const { setUser } = useApp();
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', email: '', phone: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const update = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm(f => ({ ...f, [key]: e.target.value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) return setError('Name is required.');
    if (!/^\S+@\S+\.\S+$/.test(form.email)) return setError('Enter a valid email address.');
    if (form.password.length < 8) return setError('Password must be at least 8 characters.');
    setLoading(true);
    setError('');
    try {
      setUser(await signup({
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
        phone: form.phone.trim() || undefined,
      }));
      navigate('/');
    } catch {
      setError('Sign up failed. Please try again.');
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
          <h1 className="text-2xl font-bold text-text-primary">Create your account</h1>
          <p className="text-text-muted text-sm mt-1">Movies, sports, concerts and more</p>
        </div>
        <form onSubmit={handleSubmit} className="space-y-4">
          <input className="input-field" placeholder="Full name" value={form.name} onChange={update('name')} autoComplete="name" />
          <input type="email" className="input-field" placeholder="Email" value={form.email} onChange={update('email')} autoComplete="email" />
          <input type="tel" className="input-field" placeholder="Phone (optional)" value={form.phone} onChange={update('phone')} autoComplete="tel" />
          <input type="password" className="input-field" placeholder="Password (min 8 characters)" value={form.password} onChange={update('password')} autoComplete="new-password" />
          {error && <p className="text-error text-sm">{error}</p>}
          <button type="submit" disabled={loading} className="btn-primary w-full disabled:opacity-60">
            {loading ? 'Creating account…' : 'Sign Up'}
          </button>
        </form>
        <p className="text-text-muted text-sm text-center mt-6">
          Already have an account? <Link to="/login" className="text-accent-lighter hover:underline">Log in</Link>
        </p>
      </div>
    </div>
  );
}
