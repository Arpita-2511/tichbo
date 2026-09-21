

interface SkeletonProps {
  className?: string;
}

export function Skeleton({ className = '' }: SkeletonProps) {
  return (
    <div className={`bg-bg-tertiary animate-skeleton rounded ${className}`} />
  );
}

export function EventCardSkeleton() {
  return (
    <div className="card overflow-hidden">
      <Skeleton className="h-52 w-full rounded-none" />
      <div className="p-4 space-y-2">
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="h-3 w-1/2" />
        <Skeleton className="h-3 w-2/3" />
        <Skeleton className="h-8 w-full mt-3 rounded-lg" />
      </div>
    </div>
  );
}

export function HeroSkeleton() {
  return (
    <div className="h-[420px] bg-bg-secondary animate-skeleton" />
  );
}

export function EventGridSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
      {Array.from({ length: count }).map((_, i) => (
        <EventCardSkeleton key={i} />
      ))}
    </div>
  );
}

export default function Loading() {
  return (
    <div className="flex items-center justify-center py-16">
      <div className="flex flex-col items-center gap-3">
        <div className="w-8 h-8 border-2 border-accent/30 border-t-accent rounded-full animate-spin" />
        <span className="text-text-muted text-sm">Loading...</span>
      </div>
    </div>
  );
}

