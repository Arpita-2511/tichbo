import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getPlans, subscribeToPlan } from '../services/api';
import { useApp } from '../context/AppContext';
import type { Plan } from '../types';
import Loading from '../components/common/Loading';

export default function Plans() {
  const { user, setUser, isLoggedIn } = useApp();
  const navigate = useNavigate();
  const [plans, setPlans] = useState<Plan[]>([]);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getPlans()
      .then(setPlans)
      .catch(() => setError('Failed to load plans. Please try again.'));
  }, []);

  const choose = async (plan: Plan) => {
    if (!user) return navigate('/login');
    setBusy(plan.id);
    setError(null);
    try {
      const updatedUser = await subscribeToPlan(plan.id);
      setUser(updatedUser);
    } catch {
      setError('Failed to change plan. Please try again.');
    }
    setBusy(null);
  };

  if (error && plans.length === 0) {
    return (
      <div className="max-w-5xl mx-auto px-4 sm:px-6 py-10 text-center">
        <p className="text-error">{error}</p>
      </div>
    );
  }

  if (plans.length === 0) return <Loading />;

  const currentPlanName = user?.plan;

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-10">
      <div className="text-center mb-10">
        <h1 className="section-title">Subscription Plans</h1>
        <p className="section-subtitle">Higher plans get higher API request limits and priority access.</p>
      </div>
      {error && <p className="text-error text-center text-sm mb-4">{error}</p>}
      <div className="grid md:grid-cols-3 gap-5">
        {plans.map(plan => {
          const planKey = plan.name.toUpperCase();
          const current = isLoggedIn && currentPlanName === planKey;
          const highlighted = planKey === 'PRO';
          return (
            <div key={plan.id} className={`card p-6 flex flex-col ${highlighted ? 'border-accent' : ''}`}>
              <h2 className="text-text-primary font-bold text-lg">{plan.name}</h2>
              <div className="my-3">
                <span className="text-3xl font-extrabold text-text-primary">₹{plan.price}</span>
                <span className="text-text-muted text-sm"> / month</span>
              </div>
              {plan.description && (
                <p className="text-sm text-text-secondary mb-4">{plan.description}</p>
              )}
              <div className="flex-1" />
              <button
                onClick={() => choose(plan)}
                disabled={current || busy === plan.id}
                className={`${highlighted ? 'btn-primary' : 'btn-secondary'} w-full disabled:opacity-60`}
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
