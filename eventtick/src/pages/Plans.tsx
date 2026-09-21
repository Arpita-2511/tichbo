import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Check } from 'lucide-react';
import { getPlans, subscribeToPlan } from '../services/api';
import { useApp } from '../context/AppContext';
import type { Plan } from '../types';
import Loading from '../components/common/Loading';

export default function Plans() {
  const { user, setUser, isLoggedIn } = useApp();
  const navigate = useNavigate();
  const [plans, setPlans] = useState<Plan[]>([]);
  const [busy, setBusy] = useState<string | null>(null);

  useEffect(() => { getPlans().then(setPlans); }, []);

  const choose = async (plan: Plan) => {
    if (!user) return navigate('/login');
    setBusy(plan.id);
    if (await subscribeToPlan(plan.id)) setUser({ ...user, plan: plan.id });
    setBusy(null);
  };

  if (plans.length === 0) return <Loading />;

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-10">
      <div className="text-center mb-10">
        <h1 className="section-title">Subscription Plans</h1>
        <p className="section-subtitle">Higher plans get higher API request limits and priority access.</p>
      </div>
      <div className="grid md:grid-cols-3 gap-5">
        {plans.map(plan => {
          const current = isLoggedIn && user?.plan === plan.id;
          return (
            <div key={plan.id} className={`card p-6 flex flex-col ${plan.highlighted ? 'border-accent' : ''}`}>
              <h2 className="text-text-primary font-bold text-lg">{plan.name}</h2>
              <div className="my-3">
                <span className="text-3xl font-extrabold text-text-primary">₹{plan.price}</span>
                <span className="text-text-muted text-sm"> / {plan.period}</span>
              </div>
              <ul className="space-y-2 mb-4 flex-1">
                {plan.features.map(f => (
                  <li key={f} className="flex gap-2 text-sm text-text-secondary">
                    <Check size={16} className="text-success flex-shrink-0 mt-0.5" /> {f}
                  </li>
                ))}
              </ul>
              <div className="text-xs text-text-muted mb-4 space-y-1">
                {plan.limits.map(l => (
                  <div key={l.route} className="flex justify-between font-mono">
                    <span>{l.route}</span><span>{l.limit} {l.window}</span>
                  </div>
                ))}
              </div>
              <button
                onClick={() => choose(plan)}
                disabled={current || busy === plan.id}
                className={`${plan.highlighted ? 'btn-primary' : 'btn-secondary'} w-full disabled:opacity-60`}
              >
                {current ? 'Current Plan' : busy === plan.id ? 'Updating…' : 'Choose Plan'}
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}
