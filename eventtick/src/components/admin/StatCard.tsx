
import { TrendingUp, TrendingDown } from 'lucide-react';

interface StatCardProps {
  title: string;
  value: string | number;
  subtitle?: string;
  icon: React.ReactNode;
  trend?: number; // percentage
  color?: 'default' | 'accent' | 'success' | 'warning';
}

export default function StatCard({ title, value, subtitle, icon, trend, color = 'default' }: StatCardProps) {
  const colorMap = {
    default: 'text-text-primary bg-bg-tertiary border-border',
    accent: 'text-accent-lighter bg-accent/10 border-accent/30',
    success: 'text-success bg-success/10 border-success/30',
    warning: 'text-warning bg-warning/10 border-warning/30',
  };

  return (
    <div className={`card p-5 ${color !== 'default' ? colorMap[color] : ''}`}>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-text-muted text-xs font-medium mb-1 uppercase tracking-wide">{title}</p>
          <p className="text-2xl font-bold text-text-primary">{value}</p>
          {subtitle && <p className="text-text-muted text-xs mt-1">{subtitle}</p>}
        </div>
        <div className={`p-2.5 rounded-xl ${colorMap[color]}`}>
          {icon}
        </div>
      </div>
      {trend !== undefined && (
        <div className="flex items-center gap-1 mt-3">
          {trend >= 0 ? (
            <TrendingUp size={13} className="text-success" />
          ) : (
            <TrendingDown size={13} className="text-error" />
          )}
          <span className={`text-xs font-medium ${trend >= 0 ? 'text-success' : 'text-error'}`}>
            {trend >= 0 ? '+' : ''}{trend}% vs last week
          </span>
        </div>
      )}
    </div>
  );
}

