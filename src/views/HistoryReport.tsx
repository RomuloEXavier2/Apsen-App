import { useState } from 'react';
import { motion } from 'motion/react';
import type { FC } from 'react';
import {
  ClipboardList, Package, MapPin, Thermometer, User,
  ShieldCheck, CheckCircle2, Truck, PlusCircle, Search,
  BoxSelect, Clock
} from 'lucide-react';
import { cn } from '@/src/lib/utils';
import { useAuth } from '@/src/contexts/AuthContext';
import { useOrders } from '@/src/contexts/OrdersContext';
import { MOCK_ROUTES } from '@/src/lib/mockData';
import { ActionType } from '@/src/types';

type RoleFilter = 'todos' | 'admin' | 'operator';

const ACTION_ICON: Record<ActionType, FC<{ className?: string }>> = {
  'Criação': PlusCircle,
  'Separação iniciada': BoxSelect,
  'Item coletado': Package,
  'Verificação': ShieldCheck,
  'Expedição': CheckCircle2,
  'Em trânsito': Truck,
  'Entrega confirmada': CheckCircle2,
  'Arquivado': ClipboardList,
};

const ACTION_COLOR: Record<ActionType, string> = {
  'Criação': 'bg-primary text-white',
  'Separação iniciada': 'bg-tertiary-container text-on-tertiary-container',
  'Item coletado': 'bg-surface-container-highest text-primary',
  'Verificação': 'bg-surface-container text-on-surface',
  'Expedição': 'bg-primary text-white',
  'Em trânsito': 'bg-primary-container text-on-primary-container',
  'Entrega confirmada': 'bg-primary text-white',
  'Arquivado': 'bg-surface-container text-on-surface-variant',
};

export function HistoryReport() {
  const { isAdmin } = useAuth();
  const { orders, logs: allLogs } = useOrders();
  const [selectedOrderId, setSelectedOrderId] = useState('OS-2024-001');
  const [roleFilter, setRoleFilter] = useState<RoleFilter>('todos');
  const [search, setSearch] = useState('');

  const selectedOrder = orders.find(o => o.id === selectedOrderId);
  const routes = MOCK_ROUTES[selectedOrderId] ?? [];

  const logs = allLogs.filter(l => {
    if (l.orderId !== selectedOrderId) return false;
    if (roleFilter !== 'todos' && l.userRole !== roleFilter) return false;
    if (search && !l.details.toLowerCase().includes(search.toLowerCase()) && !l.userName.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });

  const statusColor: Record<string, string> = {
    'concluído': 'bg-primary',
    'em andamento': 'bg-tertiary animate-pulse',
    'pendente': 'bg-surface-container-highest',
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -20 }}
      className="max-w-7xl mx-auto px-6 pt-8 pb-32"
    >
      {/* Header */}
      <div className="mb-10">
        <p className="text-primary font-bold tracking-[0.2em] uppercase text-[10px] mb-2">Auditoria & Rastreabilidade</p>
        <h2 className="text-4xl md:text-5xl font-extrabold text-primary tracking-tight">Relatório de Histórico</h2>
        <p className="text-on-surface-variant mt-2 font-medium">Trajetória completa por ordem — separação, estoque, rota e logs.</p>
      </div>

      {/* Order Selector */}
      <div className="flex gap-3 mb-8 flex-wrap">
        {orders.map(order => (
          <button
            key={order.id}
            onClick={() => setSelectedOrderId(order.id)}
            className={cn(
              'px-5 py-2.5 rounded-full text-[10px] font-bold uppercase tracking-widest transition-all duration-200',
              selectedOrderId === order.id
                ? 'bg-primary text-white shadow-lg shadow-primary/20'
                : 'bg-surface-container text-on-surface-variant hover:bg-surface-container-high'
            )}
          >
            {order.id}
            <span className={cn(
              'ml-2 px-2 py-0.5 rounded-full text-[9px]',
              order.status === 'Expedido' ? 'bg-white/20' : 'bg-white/10'
            )}>
              {order.status}
            </span>
          </button>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Timeline — main column */}
        <div className="lg:col-span-8 space-y-6">

          {/* Filters */}
          <div className="bg-surface-container-lowest rounded-3xl p-4 tonal-shadow flex flex-col md:flex-row gap-3 items-center">
            <div className="relative flex-1">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-on-surface-variant/40" />
              <input
                className="w-full pl-10 pr-4 py-2.5 bg-surface-container-low rounded-xl border-0 text-sm focus:ring-0 placeholder:text-on-surface-variant/40 font-medium"
                placeholder="Filtrar por ação ou operador..."
                value={search}
                onChange={e => setSearch(e.target.value)}
              />
            </div>
            <div className="flex gap-2">
              {(['todos', 'admin', 'operator'] as RoleFilter[]).map(r => (
                <button
                  key={r}
                  onClick={() => setRoleFilter(r)}
                  className={cn(
                    'px-4 py-2 rounded-full text-[10px] font-bold uppercase tracking-widest transition-all',
                    roleFilter === r ? 'bg-primary text-white' : 'bg-surface-container text-on-surface-variant hover:bg-surface-container-high'
                  )}
                >
                  {r === 'todos' ? 'Todos' : r === 'admin' ? 'Admin' : 'Operador'}
                </button>
              ))}
            </div>
          </div>

          {/* Timeline */}
          <div className="bg-surface-container-lowest rounded-3xl tonal-shadow overflow-hidden">
            <div className="p-8 border-b border-surface-container-highest/10">
              <h3 className="text-xl font-bold text-primary flex items-center gap-3">
                <ClipboardList className="w-5 h-5" />
                Linha do Tempo — {selectedOrderId}
              </h3>
              <p className="text-sm text-on-surface-variant mt-1">{logs.length} evento(s) encontrado(s)</p>
            </div>

            <div className="p-8">
              {logs.length === 0 && (
                <p className="text-on-surface-variant text-sm text-center py-8">Nenhum evento encontrado para o filtro selecionado.</p>
              )}
              <div className="relative">
                {/* Vertical line */}
                {logs.length > 1 && (
                  <div className="absolute left-5 top-5 bottom-5 w-[2px] bg-surface-container-highest" />
                )}

                <div className="space-y-6">
                  {logs.map((log, idx) => {
                    const Icon = ACTION_ICON[log.action] ?? ClipboardList;
                    const colorClass = ACTION_COLOR[log.action] ?? 'bg-surface-container text-primary';
                    return (
                      <motion.div
                        key={log.id}
                        initial={{ opacity: 0, x: -10 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: idx * 0.04 }}
                        className="flex gap-5"
                      >
                        {/* Icon dot */}
                        <div className={cn('w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0 z-10', colorClass)}>
                          <Icon className="w-4 h-4" />
                        </div>

                        {/* Content */}
                        <div className="flex-1 bg-surface-container p-5 rounded-2xl border border-surface-container-highest/10 hover:bg-surface-container-low transition-colors">
                          <div className="flex flex-wrap items-center gap-2 mb-2">
                            <span className="flex items-center gap-1.5 text-[10px] text-on-surface-variant font-bold uppercase tracking-widest">
                              <Clock className="w-3 h-3" /> {log.timestamp}
                            </span>
                            <span className={cn(
                              'px-2.5 py-0.5 rounded-full text-[9px] font-black uppercase tracking-widest',
                              log.userRole === 'admin'
                                ? 'bg-primary/10 text-primary'
                                : 'bg-tertiary-container text-on-tertiary-container'
                            )}>
                              {log.userRole === 'admin' ? 'Admin' : 'Operador'}
                            </span>
                            <span className="text-xs font-bold text-on-surface flex items-center gap-1">
                              <User className="w-3 h-3 text-on-surface-variant" /> {log.userName}
                            </span>
                          </div>

                          <div className="flex items-center gap-2 mb-1">
                            <span className={cn('text-xs font-black uppercase tracking-widest px-2.5 py-1 rounded-lg', colorClass)}>
                              {log.action}
                            </span>
                          </div>

                          <p className="text-sm text-on-surface font-medium mt-2">{log.details}</p>

                          {(log.stockBox || log.units || log.temperature) && (
                            <div className="flex flex-wrap gap-3 mt-3 pt-3 border-t border-surface-container-highest/10">
                              {log.stockBox && (
                                <span className="flex items-center gap-1.5 text-[10px] font-bold text-on-surface-variant bg-surface-container-high px-3 py-1.5 rounded-full">
                                  <BoxSelect className="w-3 h-3" /> Caixa: {log.stockBox}
                                </span>
                              )}
                              {log.units && (
                                <span className="flex items-center gap-1.5 text-[10px] font-bold text-on-surface-variant bg-surface-container-high px-3 py-1.5 rounded-full">
                                  <Package className="w-3 h-3" /> {log.units.toLocaleString('pt-BR')} un.
                                  {log.itemName && ` — ${log.itemName}`}
                                </span>
                              )}
                              {log.temperature && (
                                <span className="flex items-center gap-1.5 text-[10px] font-bold text-primary bg-primary/10 px-3 py-1.5 rounded-full">
                                  <Thermometer className="w-3 h-3" /> {log.temperature}
                                </span>
                              )}
                            </div>
                          )}
                        </div>
                      </motion.div>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Sidebar */}
        <div className="lg:col-span-4 space-y-6">

          {/* Order Summary */}
          {selectedOrder && (
            <div className="bg-primary-container p-8 rounded-3xl text-on-primary-container tonal-shadow">
              <h3 className="font-bold uppercase tracking-widest text-[10px] mb-4 opacity-70">Resumo da Ordem</h3>
              <p className="text-2xl font-extrabold tracking-tight mb-1">{selectedOrder.id}</p>
              <p className="text-sm font-medium mb-4">{selectedOrder.client}</p>

              <div className="space-y-3 text-sm">
                <div className="flex justify-between">
                  <span className="opacity-70 text-[10px] font-bold uppercase tracking-wider">Status</span>
                  <span className="font-bold">{selectedOrder.status}</span>
                </div>
                <div className="flex justify-between">
                  <span className="opacity-70 text-[10px] font-bold uppercase tracking-wider">Prioridade</span>
                  <span className="font-bold">{selectedOrder.priority}</span>
                </div>
                <div className="flex justify-between">
                  <span className="opacity-70 text-[10px] font-bold uppercase tracking-wider">Data</span>
                  <span className="font-bold">{selectedOrder.date}</span>
                </div>
                <div className="flex justify-between">
                  <span className="opacity-70 text-[10px] font-bold uppercase tracking-wider">Itens</span>
                  <span className="font-bold">{selectedOrder.items?.length ?? 0} medicamentos</span>
                </div>
                {selectedOrder.temperature && (
                  <div className="flex justify-between">
                    <span className="opacity-70 text-[10px] font-bold uppercase tracking-wider">Temperatura</span>
                    <span className="font-bold">{selectedOrder.temperature}</span>
                  </div>
                )}
                {/* Admin-only financial data */}
                {isAdmin && selectedOrder.totalValue && (
                  <div className="pt-3 border-t border-white/20">
                    <span className="opacity-70 text-[10px] font-bold uppercase tracking-wider block mb-1">Valor Total da Ordem</span>
                    <span className="text-2xl font-black tracking-tight">
                      {selectedOrder.totalValue.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
                    </span>
                    <p className="text-[10px] opacity-50 mt-1 uppercase tracking-wider">Visível apenas para Admins</p>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Route */}
          <div className="bg-surface-container-lowest p-6 rounded-3xl tonal-shadow">
            <h3 className="text-on-surface-variant font-bold uppercase tracking-widest text-[10px] mb-5 flex items-center gap-2">
              <MapPin className="w-4 h-4" /> Rota de Envio
            </h3>
            <div className="space-y-4">
              {routes.map((stop, idx) => (
                <div key={idx} className="flex items-start gap-4">
                  <div className="flex flex-col items-center">
                    <div className={cn('w-3 h-3 rounded-full flex-shrink-0 mt-0.5', statusColor[stop.status])} />
                    {idx < routes.length - 1 && (
                      <div className="w-[1px] h-8 bg-surface-container-highest mt-1" />
                    )}
                  </div>
                  <div>
                    <p className="text-xs font-bold text-on-surface">{stop.name}</p>
                    <div className="flex items-center gap-2 mt-0.5">
                      <span className="text-[10px] text-on-surface-variant uppercase tracking-wider">
                        {stop.type.charAt(0).toUpperCase() + stop.type.slice(1)}
                      </span>
                      {stop.time !== '—' && (
                        <span className="text-[10px] font-bold text-primary">{stop.time}</span>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Items summary */}
          {selectedOrder?.items && (
            <div className="bg-surface-container-lowest p-6 rounded-3xl tonal-shadow">
              <h3 className="text-on-surface-variant font-bold uppercase tracking-widest text-[10px] mb-4 flex items-center gap-2">
                <Package className="w-4 h-4" /> Medicamentos Separados
              </h3>
              <div className="space-y-3">
                {selectedOrder.items.map((item, idx) => (
                  <div key={idx} className="flex items-center justify-between">
                    <div>
                      <p className="text-xs font-bold text-on-surface">{item.name}</p>
                      <p className="text-[10px] text-on-surface-variant">{item.subName}</p>
                    </div>
                    <div className="text-right">
                      <p className="text-xs font-bold text-primary">{item.qty} un.</p>
                      {item.stockBox && (
                        <p className="text-[10px] font-mono text-on-surface-variant">{item.stockBox}</p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Admin KPIs */}
          {isAdmin && (
            <div className="bg-surface-container-lowest p-6 rounded-3xl tonal-shadow border-l-4 border-primary">
              <h3 className="text-primary font-bold uppercase tracking-widest text-[10px] mb-4 flex items-center gap-2">
                <ShieldCheck className="w-4 h-4" /> KPIs — Somente Admin
              </h3>
              <div className="space-y-3">
                <div className="flex justify-between text-xs">
                  <span className="text-on-surface-variant font-bold uppercase tracking-wider">Tempo de Separação</span>
                  <span className="font-bold text-on-surface">1h 13min</span>
                </div>
                <div className="flex justify-between text-xs">
                  <span className="text-on-surface-variant font-bold uppercase tracking-wider">Eficiência</span>
                  <span className="font-bold text-primary">98.2%</span>
                </div>
                <div className="flex justify-between text-xs">
                  <span className="text-on-surface-variant font-bold uppercase tracking-wider">Divergências</span>
                  <span className="font-bold text-on-surface">0</span>
                </div>
                <div className="flex justify-between text-xs">
                  <span className="text-on-surface-variant font-bold uppercase tracking-wider">Operadores Envolvidos</span>
                  <span className="font-bold text-on-surface">2</span>
                </div>
                <div className="pt-2 border-t border-surface-container-highest/10 flex justify-between text-xs">
                  <span className="text-on-surface-variant font-bold uppercase tracking-wider">Custo/Unidade</span>
                  <span className="font-bold text-primary">R$ 10,42</span>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </motion.div>
  );
}
