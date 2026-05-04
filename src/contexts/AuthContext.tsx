import { createContext, useContext, useState, ReactNode } from 'react';
import { User } from '@/src/types';

export const MOCK_USERS: User[] = [
  { id: 'u1', name: 'Dr. Ricardo Melo', username: 'admin', password: 'apsen@admin', role: 'admin' },
  { id: 'u2', name: 'João Silva', username: 'joao.silva', password: 'apsen@op', role: 'operator' },
  { id: 'u3', name: 'Carlos Ferreira', username: 'carlos.ferreira', password: 'apsen@op', role: 'operator' },
];

interface AuthContextValue {
  user: User | null;
  login: (username: string, password: string) => boolean;
  logout: () => void;
  isAdmin: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);

  const login = (username: string, password: string): boolean => {
    const found = MOCK_USERS.find(u => u.username === username && u.password === password);
    if (found) { setUser(found); return true; }
    return false;
  };

  const logout = () => setUser(null);

  return (
    <AuthContext.Provider value={{ user, login, logout, isAdmin: user?.role === 'admin' }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth deve ser usado dentro de AuthProvider');
  return ctx;
}
