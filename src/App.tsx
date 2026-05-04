/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { useState } from 'react';
import { AnimatePresence } from 'motion/react';
import { TopAppBar } from './components/TopAppBar';
import { BottomNavBar } from './components/BottomNavBar';
import { Expeditions } from './views/Expeditions';
import { Details } from './views/Details';
import { Scanning } from './views/Scanning';
import { NewOrder } from './views/NewOrder';
import { Archive } from './views/Archive';
import { View, ServiceOrder } from './types';

export default function App() {
  const [currentView, setCurrentView] = useState<View>('expeditions');
  const [selectedOrder, setSelectedOrder] = useState<ServiceOrder | null>(null);

  const handleOrderClick = (order: ServiceOrder) => {
    setSelectedOrder(order);
    setCurrentView('details');
  };

  const handleNewOrder = () => {
    setCurrentView('new-order');
  };

  const renderView = () => {
    switch (currentView) {
      case 'expeditions':
        return <Expeditions onOrderClick={handleOrderClick} onNewOrder={handleNewOrder} />;
      case 'details':
        return selectedOrder ? (
          <Details order={selectedOrder} onBack={() => setCurrentView('expeditions')} />
        ) : (
          <Expeditions onOrderClick={handleOrderClick} onNewOrder={handleNewOrder} />
        );
      case 'scanning':
        return <Scanning />;
      case 'new-order':
        return <NewOrder />;
      case 'archive':
        return <Archive />;
      default:
        return <Expeditions onOrderClick={handleOrderClick} onNewOrder={handleNewOrder} />;
    }
  };

  return (
    <div className="min-h-screen flex flex-col">
      <TopAppBar 
        title={currentView === 'new-order' ? "Service Initiation" : "MYND HEALTHY"}
        subtitle={currentView === 'new-order' ? "Logistics Portal" : "Apsen Pharmaceuticals"}
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

      {/* Background Decoration */}
      <div className="fixed inset-0 pointer-events-none -z-10 opacity-[0.02] bg-[radial-gradient(#00328b_1px,transparent_1px)] [background-size:40px_40px]" />
    </div>
  );
}
