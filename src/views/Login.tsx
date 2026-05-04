import { useState, type FormEvent } from 'react';
import { motion } from 'motion/react';
import { Lock, User } from 'lucide-react';
import { useAuth } from '@/src/contexts/AuthContext';

export function Login() {
  const { login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setTimeout(() => {
      const ok = login(username, password);
      if (!ok) setError('Usuário ou senha incorretos.');
      setLoading(false);
    }, 600);
  };

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="min-h-screen flex items-center justify-center bg-surface px-6"
    >
      <div className="w-full max-w-sm">
        {/* Branding */}
        <div className="text-center mb-12">
          <motion.div
            initial={{ scale: 0.8, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ delay: 0.1 }}
            className="inline-flex items-center justify-center w-20 h-20 rounded-3xl bg-primary mb-6 shadow-2xl shadow-primary/30"
          >
            <span className="text-white font-headline font-black text-3xl">A</span>
          </motion.div>
          <h1 className="text-3xl font-extrabold text-primary tracking-tight">Apsen App</h1>
          <p className="text-on-surface-variant text-sm mt-2 font-medium">Sistema de Gestão Logística</p>
          <p className="text-on-surface-variant/50 text-[10px] mt-1 font-bold uppercase tracking-widest">Apsen Farmacêuticos S.A.</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="bg-surface-container-lowest rounded-3xl p-8 tonal-shadow space-y-6">
            <div className="flex flex-col gap-2">
              <label className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
                Usuário
              </label>
              <div className="relative">
                <User className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-on-surface-variant/40" />
                <input
                  className="w-full pl-11 pr-4 py-3 bg-surface-container-low rounded-xl border-0 border-b-2 border-transparent focus:border-primary focus:ring-0 text-on-surface font-medium transition-all placeholder:text-on-surface-variant/40"
                  placeholder="usuario.nome"
                  value={username}
                  onChange={e => setUsername(e.target.value)}
                  autoComplete="username"
                />
              </div>
            </div>

            <div className="flex flex-col gap-2">
              <label className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
                Senha
              </label>
              <div className="relative">
                <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-on-surface-variant/40" />
                <input
                  type="password"
                  className="w-full pl-11 pr-4 py-3 bg-surface-container-low rounded-xl border-0 border-b-2 border-transparent focus:border-primary focus:ring-0 text-on-surface font-medium transition-all placeholder:text-on-surface-variant/40"
                  placeholder="••••••••"
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  autoComplete="current-password"
                />
              </div>
            </div>

            {error && (
              <motion.p
                initial={{ opacity: 0, y: -4 }}
                animate={{ opacity: 1, y: 0 }}
                className="text-error text-xs font-bold text-center"
              >
                {error}
              </motion.p>
            )}
          </div>

          <button
            type="submit"
            disabled={loading || !username || !password}
            className="w-full bg-primary text-on-primary py-4 rounded-2xl font-bold uppercase tracking-widest text-xs shadow-xl shadow-primary/20 transition-all hover:scale-[1.02] active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? 'Verificando...' : 'Entrar'}
          </button>
        </form>

        {/* Credential hints */}
        <div className="mt-8 bg-surface-container p-5 rounded-2xl">
          <p className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-3">
            Credenciais de acesso
          </p>
          <div className="space-y-2 text-xs">
            <div className="flex justify-between items-center">
              <span className="text-on-surface-variant flex items-center gap-2">
                <span className="px-2 py-0.5 rounded-full bg-primary/10 text-primary text-[10px] font-bold uppercase tracking-wider">Admin</span>
              </span>
              <span className="font-mono text-primary font-medium">admin / apsen@admin</span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-on-surface-variant flex items-center gap-2">
                <span className="px-2 py-0.5 rounded-full bg-tertiary-container text-on-tertiary-container text-[10px] font-bold uppercase tracking-wider">Operador</span>
              </span>
              <span className="font-mono text-on-surface font-medium">joao.silva / apsen@op</span>
            </div>
          </div>
        </div>
      </div>
    </motion.div>
  );
}
