
import type { RateLimitPolicy } from '../../types';

interface RateLimitTableProps {
  policies: RateLimitPolicy[];
}

const planColors: Record<string, string> = {
  FREE: 'bg-border text-text-secondary border-border-light',
  PREMIUM: 'bg-accent/20 text-accent-lighter border-accent/30',
  VIP: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30',
};

export default function RateLimitTable({ policies }: RateLimitTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border">
            {['Plan', 'Route', 'Limit', 'Window', 'Status'].map(h => (
              <th key={h} className="text-left text-text-muted text-xs font-medium py-3 px-4 whitespace-nowrap">{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {policies.map((p, i) => (
            <tr key={i} className="border-b border-border/50 hover:bg-bg-tertiary transition-colors">
              <td className="py-3 px-4">
                <span className={`badge border text-[10px] ${planColors[p.plan]}`}>{p.plan}</span>
              </td>
              <td className="py-3 px-4 font-mono text-xs text-text-secondary">{p.route}</td>
              <td className="py-3 px-4 text-text-primary font-semibold">{p.limit} req</td>
              <td className="py-3 px-4 text-text-muted text-xs">{p.window}</td>
              <td className="py-3 px-4">
                <div className="flex items-center gap-1.5">
                  <div className="w-1.5 h-1.5 rounded-full bg-success animate-pulse" />
                  <span className="text-success text-xs">Active</span>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

