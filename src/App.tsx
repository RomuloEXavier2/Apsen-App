import { useState } from 'react';
import { AnimatePresence } from 'motion/react';
import { TopAppBar } from './components/TopAppBar';
import { BottomNavBar } from './components/BottomNavBar';
import { Expeditions } from './views/Expeditions';
import { Details } from './views/Details';
import { Scanning } from './views/Scanning';
import { NewOrder } from './views/NewOrder';
import { Archive } from './views/Archive';
import { HistoryReport } from './views/HistoryReport';
import { Login } from './views/Login';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { OrdersProvider } from './contexts/OrdersContext';
import { View, ServiceOrder } from './types';

function AppContent() {
  const { user, logout } = useAuth();
  const [currentView, setCurrentView] = useState<View>('expeditions');
  const [selectedOrder, setSelectedOrder] = useState<ServiceOrder | null>(null);

  if (!user) return <Login />;

  const handleOrderClick = (order: ServiceOrder) => {
    setSelectedOrder(order);
    setCurrentView('details');
  };

  const handleNewOrder = () => setCurrentView('new-order');

  const handleOrderCreated = (order: ServiceOrder) => {
    setSelectedOrder(order);
    setCurrentView('details');
  };

  const titleMap: Partial<Record<View, string>> = {
    'new-order': 'Abertura de Ordem',
    'details': 'Detalhes',
    'history': 'Histórico',
    'archive': 'Arquivo',
    'scanning': 'Scanner QR',
  };

  const renderView = () => {
    switch (currentView) {
      case 'expeditions':
        return <Expeditions onOrderClick={handleOrderClick} onNewOrder={handleNewOrder} />;
      case 'details':
        return selectedOrder
          ? <Details order={selectedOrder} onBack={() => setCurrentView('expeditions')} />
          : <Expeditions onOrderClick={handleOrderClick} onNewOrder={handleNewOrder} />;
      case 'scanning':
        return <Scanning />;
      case 'new-order':
        return <NewOrder onSuccess={handleOrderCreated} />;
      case 'archive':
        return <Archive />;
      case 'history':
        return <HistoryReport />;
      default:
        return <Expeditions onOrderClick={handleOrderClick} onNewOrder={handleNewOrder} />;
    }
  };

  return (
    <div className="min-h-screen flex flex-col">
      <TopAppBar
        title={titleMap[currentView] ?? 'Apsen App'}
        subtitle="Apsen Farmacêuticos"
        userName={user.name}
        userRole={user.role}
        onLogout={logout}
      />

      <main className="flex-1">
        <AnimatePresence mode="wait">
          {renderView()}
        </AnimatePresence>
      </main>

      <BottomNavBar
        currentView={currentView}
        onViewChange={(view) => {
          setCurrentView(view);
          setSelectedOrder(null);
        }}
      />

      <div className="fixed inset-0 pointer-events-none -z-10 opacity-[0.02] bg-[radial-gradient(#00328b_1px,transparent_1px)] [background-size:40px_40px]" />
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <OrdersProvider>
        <AppContent />
      </OrdersProvider>
    </AuthProvider>
  );
}
