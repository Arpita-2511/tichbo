
import { Search } from 'lucide-react';

interface EmptyStateProps {
  title?: string;
  description?: string;
  icon?: React.ReactNode;
  action?: React.ReactNode;
}

export default function EmptyState({
  title = 'No events found',
  description = 'Try changing your filters or searching in a different city.',
  icon,
  action,
}: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-center px-4">
      <div className="w-16 h-16 rounded-2xl bg-bg-tertiary border border-border flex items-center justify-center mb-4 text-text-muted">
        {icon || <Search size={24} />}
      </div>
      <h3 className="text-text-primary font-semibold text-lg mb-2">{title}</h3>
      <p className="text-text-muted text-sm max-w-sm leading-relaxed">{description}</p>
      {action && <div className="mt-6">{action}</div>}
    </div>
  );
}

