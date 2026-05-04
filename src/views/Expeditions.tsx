import { Search, Filter, Calendar, Settings2, Eye, MoreVertical, Plus } from 'lucide-react';
import { motion } from 'motion/react';
import { ServiceOrder, View } from '@/src/types';
import { cn } from '@/src/lib/utils';

const MOCK_ORDERS: ServiceOrder[] = [
  { id: 'OS-2024-001', client: 'Droga Raia S.A.', destination: 'Hub Logístico - Cajamar SP', date: '12 Out, 2023', status: 'Em Separação', priority: 'Alta' },
  { id: 'OS-2024-002', client: 'Hospital Albert Einstein', destination: 'Unidade Morumbi - São Paulo SP', date: '13 Out, 2023', status: 'Aguardando', priority: 'Normal' },
  { id: 'OS-2024-003', client: 'Pague Menos Distribuição', destination: 'CD Fortaleza - CE', date: '13 Out, 2023', status: 'Expedido', priority: 'Normal' },
  { id: 'OS-2024-004', client: 'Drogaria São Paulo', destination: 'CD Osasco - SP', date: '14 Out, 2023', status: 'Em Separação', priority: 'Urgente' },
];

interface ExpeditionsProps {
  onOrderClick: (order: ServiceOrder) => void;
  onNewOrder: () => void;
}

export function Expeditions({ onOrderClick, onNewOrder }: ExpeditionsProps) {
  return (
    <motion.div 
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -20 }}
      className="max-w-7xl mx-auto px-6 pt-8 pb-32"
    >
      {/* Hero Header */}
      <div className="mb-10 flex flex-col md:flex-row md:items-end justify-between gap-6">
        <div>
          <p className="text-primary font-bold tracking-[0.2em] uppercase text-[10px] mb-2">Monitoramento de Fluxo</p>
          <h2 className="text-4xl md:text-5xl font-extrabold text-primary tracking-tight">Expeditions</h2>
        </div>
        <button 
          onClick={onNewOrder}
          className="bg-primary-container text-on-primary px-6 py-3 rounded-xl font-bold flex items-center gap-2 hover:opacity-90 active:scale-95 duration-150 shadow-sm transition-all text-xs uppercase tracking-widest"
        >
          <Plus className="w-4 h-4" />
          Nova Ordem de Serviço
        </button>
      </div>

      {/* Search & Filters Bento */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
        <div className="md:col-span-3 bg-surface-container-lowest p-2 rounded-full flex items-center px-6 tonal-shadow">
          <Search className="w-5 h-5 text-on-surface-variant" />
          <input 
            className="w-full border-none bg-transparent focus:ring-0 text-sm font-medium placeholder:text-on-surface-variant/40 px-4" 
            placeholder="Buscar por ID, Cliente ou Medicamento..." 
            type="text"
          />
          <div className="h-6 w-[1px] bg-surface-container-highest mx-2" />
          <button className="text-primary font-bold text-[10px] uppercase tracking-widest px-2 whitespace-nowrap">Scan QR</button>
        </div>
        <div className="bg-surface-container p-2 rounded-full flex justify-between items-center px-4">
          <button className="p-2 bg-surface-container-lowest text-primary rounded-full shadow-sm">
            <Filter className="w-5 h-5" />
          </button>
          <div className="flex-1 flex justify-center gap-4">
            <Calendar className="w-5 h-5 text-on-surface-variant opacity-40" />
            <Settings2 className="w-5 h-5 text-on-surface-variant opacity-40" />
          </div>
        </div>
      </div>

      {/* Listing Section */}
      <div className="space-y-4">
        {/* Table Header */}
        <div className="hidden md:grid grid-cols-6 px-8 py-4 text-[10px] font-bold uppercase tracking-widest text-on-surface-variant opacity-60">
          <div className="col-span-1">ID Expedição</div>
          <div className="col-span-2">Cliente / Destino</div>
          <div className="col-span-1">Data Solicitação</div>
          <div className="col-span-1">Status Atual</div>
          <div className="col-span-1 text-right">Ações</div>
        </div>

        {MOCK_ORDERS.map((order) => (
          <motion.div 
            key={order.id}
            whileHover={{ scale: 1.01 }}
            onClick={() => onOrderClick(order)}
            className="bg-surface-container-lowest p-6 md:px-8 md:py-5 rounded-3xl md:rounded-full border border-surface-container-highest/10 hover:bg-surface-container-low transition-all duration-300 group cursor-pointer flex flex-col md:grid md:grid-cols-6 items-center gap-4 tonal-shadow"
          >
            <div className="col-span-1 flex items-center gap-3 w-full md:w-auto">
              <div className={cn(
                "w-2 h-10 rounded-full hidden md:block",
                order.status === 'Expedido' ? "bg-primary" : "bg-tertiary"
              )} />
              <span className="font-headline font-bold text-primary">{order.id}</span>
            </div>
            <div className="col-span-2 w-full md:w-auto">
              <p className="font-bold text-on-surface">{order.client}</p>
              <p className="text-[10px] text-on-surface-variant uppercase tracking-wider">{order.destination}</p>
            </div>
            <div className="col-span-1 w-full md:w-auto">
              <p className="text-sm font-medium">{order.date}</p>
              <p className="text-[10px] uppercase tracking-tighter text-on-surface-variant">Prioridade: {order.priority}</p>
            </div>
            <div className="col-span-1 w-full md:w-auto">
              <span className={cn(
                "px-4 py-1.5 rounded-full text-[10px] font-bold uppercase tracking-widest flex items-center gap-2 w-fit",
                order.status === 'Expedido' ? "bg-primary text-on-primary" : "bg-tertiary-container text-on-tertiary-container"
              )}>
                <div className={cn(
                  "w-1.5 h-1.5 rounded-full",
                  order.status === 'Expedido' ? "bg-on-primary" : "bg-on-tertiary-container animate-pulse"
                )} />
                {order.status}
              </span>
            </div>
            <div className="col-span-1 flex justify-end gap-2 w-full md:w-auto">
              <button className="p-2 hover:bg-surface-container-high rounded-full text-primary transition-colors">
                <Eye className="w-5 h-5" />
              </button>
              <button className="p-2 hover:bg-surface-container-high rounded-full text-primary transition-colors">
                <MoreVertical className="w-5 h-5" />
              </button>
            </div>
          </motion.div>
        ))}
      </div>
    </motion.div>
  );
}
