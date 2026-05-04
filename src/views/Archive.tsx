import { Search, Archive as ArchiveIcon, CheckCircle2 } from 'lucide-react';
import { motion } from 'motion/react';
import { useAuth } from '@/src/contexts/AuthContext';

export function Archive() {
  const { isAdmin } = useAuth();

  const archivedOrders = [
    { id: 'OS-29481-B', client: 'Drogasil Distribuidora Ltda.', date: '24 Out, 2023' },
    { id: 'OS-30112-C', client: 'Hospital das Clínicas — FMUSP', date: '22 Out, 2023' },
    { id: 'OS-31455-A', client: 'Ultrafarma Distribuidora', date: '21 Out, 2023' },
    { id: 'OS-33900-X', client: 'Pague Menos S.A. — CD Fortaleza', date: '19 Out, 2023' },
  ];

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -20 }}
      className="max-w-5xl mx-auto px-6 pt-8 pb-32"
    >
      <div className="mb-12">
        <div className="flex flex-col md:flex-row md:items-end justify-between gap-6">
          <div>
            <p className="text-on-surface-variant text-[10px] uppercase tracking-[0.2em] font-bold mb-2">Registros Históricos</p>
            <h2 className="text-4xl font-extrabold tracking-tight text-primary">Arquivo</h2>
          </div>
          <div className="relative w-full md:w-80 group">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-on-surface-variant group-focus-within:text-primary transition-colors" />
            <input
              className="w-full h-14 pl-12 pr-4 bg-surface-container-highest/50 border-none rounded-2xl focus:ring-0 focus:border-b-2 focus:border-primary transition-all placeholder:text-on-surface-variant/60 font-medium"
              placeholder="Buscar ordens, clientes, IDs..."
              type="text"
            />
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-12 gap-6">
        {/* Featured Archive Item */}
        <div className="md:col-span-8 bg-surface-container-lowest rounded-3xl p-8 tonal-shadow transition-all hover:translate-y-[-4px]">
          <div className="flex justify-between items-start mb-8">
            <div>
              <span className="text-[10px] font-bold text-on-surface-variant tracking-widest uppercase block mb-1">Ordem de Serviço</span>
              <h3 className="text-2xl font-extrabold text-on-surface">{archivedOrders[0].id}</h3>
            </div>
            <span className="px-4 py-1.5 rounded-full bg-primary/10 text-primary text-[10px] font-black tracking-widest uppercase flex items-center gap-1.5">
              <CheckCircle2 className="w-3 h-3" /> Concluído
            </span>
          </div>
          <div className="grid grid-cols-2 gap-8">
            <div>
              <p className="text-[10px] text-on-surface-variant font-bold uppercase tracking-widest mb-1">Cliente</p>
              <p className="font-bold text-on-surface">{archivedOrders[0].client}</p>
            </div>
            <div>
              <p className="text-[10px] text-on-surface-variant font-bold uppercase tracking-widest mb-1">Data de Encerramento</p>
              <p className="font-bold text-on-surface">{archivedOrders[0].date}</p>
            </div>
          </div>
        </div>

        {/* Stats Card — admin-only full number */}
        <div className="md:col-span-4 bg-primary text-on-primary rounded-3xl p-8 flex flex-col justify-between tonal-shadow">
          <ArchiveIcon className="w-10 h-10 opacity-50" />
          <div>
            <h4 className="text-[10px] font-bold uppercase tracking-[0.2em] mb-1 opacity-70">
              {isAdmin ? 'Totais do Mês' : 'Remessas Arquivadas'}
            </h4>
            <p className="text-5xl font-black tracking-tighter">
              {isAdmin ? '1.248' : '—'}
            </p>
            <p className="text-xs opacity-80 mt-2">
              {isAdmin ? 'Remessas arquivadas' : 'Disponível para Admins'}
            </p>
          </div>
        </div>

        {/* List Items */}
        {archivedOrders.slice(1).map((order) => (
          <div key={order.id} className="md:col-span-6 bg-surface-container-lowest rounded-3xl p-8 tonal-shadow transition-all hover:translate-y-[-4px]">
            <div className="flex justify-between items-start mb-8">
              <div>
                <span className="text-[10px] font-bold text-on-surface-variant tracking-widest uppercase block mb-1">Ordem de Serviço</span>
                <h3 className="text-2xl font-extrabold text-on-surface">{order.id}</h3>
              </div>
              <span className="px-4 py-1.5 rounded-full bg-primary/10 text-primary text-[10px] font-black tracking-widest uppercase flex items-center gap-1.5">
                <CheckCircle2 className="w-3 h-3" /> Concluído
              </span>
            </div>
            <div className="space-y-4">
              <div className="flex justify-between items-center">
                <span className="text-[10px] text-on-surface-variant font-bold uppercase tracking-widest">Cliente</span>
                <span className="font-bold text-on-surface text-sm">{order.client}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-[10px] text-on-surface-variant font-bold uppercase tracking-widest">Data</span>
                <span className="font-bold text-on-surface text-sm">{order.date}</span>
              </div>
            </div>
          </div>
        ))}
      </div>
    </motion.div>
  );
}
