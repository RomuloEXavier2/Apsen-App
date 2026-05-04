import { Truck, QrCode, Archive, Plus } from 'lucide-react';
import { View } from '@/src/types';
import { cn } from '@/src/lib/utils';

interface BottomNavBarProps {
  currentView: View;
  onViewChange: (view: View) => void;
}

export function BottomNavBar({ currentView, onViewChange }: BottomNavBarProps) {
  const items = [
    { id: 'expeditions', label: 'Expeditions', icon: Truck },
    { id: 'scanning', label: 'Scanning', icon: QrCode },
    { id: 'archive', label: 'Archive', icon: Archive },
  ] as const;

  return (
    <nav className="fixed bottom-0 left-0 w-full flex justify-around items-center px-4 pb-6 pt-2 bg-surface/80 backdrop-blur-md z-50 rounded-t-3xl border-t border-surface-container-highest/20 shadow-[0_-4px_32px_rgba(25,28,26,0.04)]">
      {items.map((item) => {
        const Icon = item.icon;
        const isActive = currentView === item.id;
        
        return (
          <button
            key={item.id}
            onClick={() => onViewChange(item.id)}
            className={cn(
              "flex flex-col items-center justify-center transition-all duration-300 relative",
              isActive ? "text-on-primary" : "text-on-surface-variant opacity-60"
            )}
          >
            <div className={cn(
              "w-14 h-14 rounded-full flex items-center justify-center transition-all duration-300",
              isActive ? "bg-primary-container shadow-lg -translate-y-2" : "hover:bg-surface-container-low"
            )}>
              <Icon className={cn("w-6 h-6", isActive && "fill-current")} />
            </div>
            <span className={cn(
              "text-[10px] font-bold uppercase tracking-widest mt-1 transition-all duration-300",
              isActive ? "opacity-100" : "opacity-0"
            )}>
              {item.label}
            </span>
          </button>
        );
      })}
    </nav>
  );
}
