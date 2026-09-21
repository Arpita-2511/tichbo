
import { X, MapPin, CheckCircle } from 'lucide-react';
import { useApp } from '../../context/AppContext';

export default function LocationSelector() {
  const { isLocationOpen, setIsLocationOpen, selectedCity, setSelectedCity, cities } = useApp();

  if (!isLocationOpen) return null;

  const handleSelect = (city: string) => {
    setSelectedCity(city);
    setIsLocationOpen(false);
  };

  return (
    <div className="fixed inset-0 z-[100] bg-black/70 backdrop-blur-sm animate-fade-in flex items-center justify-center p-4" onClick={() => setIsLocationOpen(false)}>
      <div className="bg-bg-secondary border border-border rounded-2xl w-full max-w-md shadow-2xl animate-slide-up" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between px-5 py-4 border-b border-border">
          <div>
            <h2 className="text-text-primary font-semibold">Select Your City</h2>
            <p className="text-text-muted text-xs mt-0.5">Events will be filtered by your selected city</p>
          </div>
          <button onClick={() => setIsLocationOpen(false)} className="p-1.5 rounded-lg hover:bg-bg-tertiary text-text-muted hover:text-text-primary transition-colors">
            <X size={18} />
          </button>
        </div>

        <div className="p-4">
          {/* Current */}
          <div className="flex items-center gap-2 text-accent-lighter text-sm mb-4 px-1">
            <MapPin size={15} />
            <span>Currently showing: <strong>{selectedCity}</strong></span>
          </div>

          {/* City Grid */}
          <div className="grid grid-cols-3 gap-2">
            {cities.map(city => (
              <button
                key={city}
                onClick={() => handleSelect(city)}
                className={`relative flex flex-col items-center gap-1 p-3 rounded-xl border text-sm font-medium transition-all ${
                  selectedCity === city
                    ? 'border-accent bg-accent/10 text-accent-lighter'
                    : 'border-border bg-bg-tertiary text-text-secondary hover:border-accent/50 hover:text-text-primary'
                }`}
              >
                {selectedCity === city && (
                  <CheckCircle size={12} className="absolute top-1.5 right-1.5 text-accent" />
                )}
                <MapPin size={16} />
                {city}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

