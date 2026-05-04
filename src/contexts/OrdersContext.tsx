import { createContext, useContext, useState, ReactNode } from 'react';
import { ServiceOrder, ActivityLog } from '@/src/types';
import { MOCK_ORDERS, MOCK_LOGS } from '@/src/lib/mockData';

interface OrdersContextValue {
  orders: ServiceOrder[];
  logs: ActivityLog[];
  addOrder: (order: ServiceOrder) => void;
  addLog: (log: ActivityLog) => void;
}

const OrdersContext = createContext<OrdersContextValue | null>(null);

export function OrdersProvider({ children }: { children: ReactNode }) {
  const [orders, setOrders] = useState<ServiceOrder[]>(MOCK_ORDERS);
  const [logs, setLogs] = useState<ActivityLog[]>(MOCK_LOGS);

  const addOrder = (order: ServiceOrder) =>
    setOrders(prev => [order, ...prev]);

  const addLog = (log: ActivityLog) =>
    setLogs(prev => [...prev, log]);

  return (
    <OrdersContext.Provider value={{ orders, logs, addOrder, addLog }}>
      {children}
    </OrdersContext.Provider>
  );
}

export function useOrders() {
  const ctx = useContext(OrdersContext);
  if (!ctx) throw new Error('useOrders deve ser usado dentro de OrdersProvider');
  return ctx;
}
