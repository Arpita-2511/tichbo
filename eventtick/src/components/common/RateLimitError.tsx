
import { AlertTriangle, RefreshCw, Clock } from 'lucide-react';

interface RateLimitErrorProps {
  onRetry?: () => void;
}

export default function RateLimitError({ onRetry }: RateLimitErrorProps) {
  return (
    <div className="flex flex-col items-center justify-center py-20 px-4 text-center">
      <div className="w-20 h-20 rounded-2xl bg-warning/10 border border-warning/30 flex items-center justify-center mb-6">
        <AlertTriangle size={32} className="text-warning" />
      </div>
      <div className="text-warning text-xs font-mono font-bold mb-2 tracking-widest">HTTP 429</div>
      <h2 className="text-text-primary text-2xl font-bold mb-3">Too Many Requests</h2>
      <p className="text-text-muted text-sm max-w-sm leading-relaxed mb-6">
        You've temporarily reached the request limit for your current plan. Please wait a moment and try again.
      </p>
      <div className="flex items-center gap-2 text-text-muted text-xs mb-6 bg-bg-tertiary border border-border rounded-lg px-4 py-2.5">
        <Clock size={14} />
        <span>Rate limit resets in a few seconds...</span>
      </div>
      <div className="flex flex-col sm:flex-row gap-3">
        {onRetry && (
          <button onClick={onRetry} className="btn-primary flex items-center gap-2">
            <RefreshCw size={15} />
            Try Again
          </button>
        )}
        <a href="/plans" className="btn-secondary text-sm">
          Upgrade Plan for Higher Limits
        </a>
      </div>
    </div>
  );
}

