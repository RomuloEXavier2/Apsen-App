import { Menu, User } from 'lucide-react';
import { cn } from '@/src/lib/utils';

interface TopAppBarProps {
  title?: string;
  subtitle?: string;
  onMenuClick?: () => void;
}

export function TopAppBar({ title = "MYND HEALTHY", subtitle = "Apsen Pharmaceuticals", onMenuClick }: TopAppBarProps) {
  return (
    <header className="w-full top-0 sticky bg-surface/80 backdrop-blur-md z-50 border-b border-surface-container-highest/10">
      <div className="flex justify-between items-center px-6 h-16 w-full max-w-7xl mx-auto">
        <div className="flex items-center gap-4">
          <button 
            onClick={onMenuClick}
            className="text-primary hover:bg-surface-container-low transition-colors p-2 rounded-full active:scale-95 duration-150"
          >
            <Menu className="w-6 h-6" />
          </button>
          <h1 className="text-xl font-extrabold text-primary tracking-tighter uppercase font-headline">
            {title}
          </h1>
        </div>
        
        <div className="flex items-center gap-4">
          <span className="hidden md:block font-headline font-bold tracking-wide text-primary text-sm">
            {subtitle}
          </span>
          <div className="w-10 h-10 rounded-full bg-surface-container-highest overflow-hidden border-2 border-primary/10">
            <img 
              className="w-full h-full object-cover" 
              src="https://picsum.photos/seed/pharmacist/100/100" 
              alt="User profile"
              referrerPolicy="no-referrer"
            />
          </div>
        </div>
      </div>
    </header>
  );
}
