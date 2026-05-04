import { Menu, LogOut, ShieldCheck } from 'lucide-react';
import { UserRole } from '@/src/types';

interface TopAppBarProps {
  title?: string;
  subtitle?: string;
  userName?: string;
  userRole?: UserRole;
  onMenuClick?: () => void;
  onLogout?: () => void;
}

export function TopAppBar({
  title = 'Apsen App',
  subtitle = 'Apsen Farmacêuticos',
  userName,
  userRole,
  onMenuClick,
  onLogout,
}: TopAppBarProps) {
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

        <div className="flex items-center gap-3">
          <span className="hidden md:block font-headline font-bold tracking-wide text-primary text-sm">
            {subtitle}
          </span>

          {userName && (
            <div className="hidden md:flex items-center gap-2 bg-surface-container px-3 py-1.5 rounded-full">
              {userRole === 'admin' && (
                <ShieldCheck className="w-3.5 h-3.5 text-primary" />
              )}
              <span className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
                {userName}
              </span>
              <span className={
                userRole === 'admin'
                  ? 'text-[9px] font-black uppercase tracking-widest px-2 py-0.5 rounded-full bg-primary/10 text-primary'
                  : 'text-[9px] font-black uppercase tracking-widest px-2 py-0.5 rounded-full bg-tertiary-container text-on-tertiary-container'
              }>
                {userRole === 'admin' ? 'Admin' : 'Operador'}
              </span>
            </div>
          )}

          {onLogout && (
            <button
              onClick={onLogout}
              title="Sair"
              className="p-2 text-on-surface-variant hover:text-primary hover:bg-surface-container-low rounded-full transition-all active:scale-95"
            >
              <LogOut className="w-5 h-5" />
            </button>
          )}
        </div>
      </div>
    </header>
  );
}
