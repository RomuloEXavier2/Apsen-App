import { ArrowLeft, Package, Thermometer, MapPin, FileText, Download, RefreshCw, CheckCircle2, Snowflake } from 'lucide-react';
import { motion } from 'motion/react';
import { ServiceOrder } from '@/src/types';
import { cn } from '@/src/lib/utils';

interface DetailsProps {
  order: ServiceOrder;
  onBack: () => void;
}

export function Details({ order, onBack }: DetailsProps) {
  const items = [
    { name: 'Velija 60mg', subName: 'Duloxetine Hydrochloride', qty: '1,200', batch: '#B24-APS-091', coldChain: true },
    { name: 'Donaren Retard', subName: 'Trazodone Hydrochloride', qty: '850', batch: '#B24-APS-442', coldChain: false },
    { name: 'Pondera 20mg', subName: 'Paroxetine', qty: '2,400', batch: '#B24-APS-218', coldChain: false },
  ];

  return (
    <motion.div 
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -20 }}
      className="max-w-6xl mx-auto px-6 mt-8 pb-32"
    >
      {/* Header */}
      <section className="flex flex-col md:flex-row md:items-end justify-between gap-6 mb-12">
        <div>
          <button 
            onClick={onBack}
            className="flex items-center gap-2 text-on-surface-variant hover:text-primary transition-colors mb-4 group"
          >
            <ArrowLeft className="w-4 h-4 group-hover:-translate-x-1 transition-transform" />
            <span className="text-xs font-bold uppercase tracking-widest">Voltar</span>
          </button>
          <span className="text-on-surface-variant font-bold text-[10px] tracking-[0.2em] uppercase">Service Order Details</span>
          <h1 className="text-5xl font-extrabold text-primary tracking-tight mt-2">{order.id}</h1>
          <p className="text-on-surface-variant mt-2 font-medium text-lg">Apsen Pharmaceutical Logistics Hub • Node 04</p>
        </div>
        <div className="flex items-center gap-4">
          <div className="bg-tertiary-container/30 px-6 py-3 rounded-full flex items-center gap-3 border border-on-tertiary-container/10">
            <div className="w-3 h-3 bg-on-tertiary-container rounded-full animate-pulse" />
            <span className="text-on-tertiary-container font-bold uppercase tracking-widest text-xs">In Transit</span>
          </div>
        </div>
      </section>

      {/* Bento Grid */}
      <div className="grid grid-cols-1 md:grid-cols-12 gap-6">
        {/* Items Table */}
        <div className="md:col-span-8 bg-surface-container-lowest rounded-3xl tonal-shadow overflow-hidden">
          <div className="p-8 border-b border-surface-container-highest/10">
            <h2 className="text-2xl font-bold text-primary flex items-center gap-3">
              <Package className="w-6 h-6" />
              Manifest Items
            </h2>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead className="bg-surface-container-low/50">
                <tr>
                  <th className="px-8 py-4 text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">Medication Name</th>
                  <th className="px-8 py-4 text-[10px] font-bold uppercase tracking-widest text-on-surface-variant text-right">Qty</th>
                  <th className="px-8 py-4 text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">Batch Number</th>
                  <th className="px-8 py-4 text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">Cold Chain</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-surface-container-highest/10">
                {items.map((item, i) => (
                  <tr key={i} className="hover:bg-surface-container-low transition-colors group">
                    <td className="px-8 py-6">
                      <div className="font-bold text-primary">{item.name}</div>
                      <div className="text-[10px] text-on-surface-variant uppercase tracking-wider">{item.subName}</div>
                    </td>
                    <td className="px-8 py-6 text-right font-mono font-bold text-lg text-primary">{item.qty}</td>
                    <td className="px-8 py-6 font-mono text-xs text-on-surface-variant">{item.batch}</td>
                    <td className="px-8 py-6">
                      {item.coldChain ? (
                        <Snowflake className="w-5 h-5 text-primary fill-current opacity-20" />
                      ) : (
                        <CheckCircle2 className="w-5 h-5 text-on-surface-variant opacity-20" />
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Sidebar Info */}
        <div className="md:col-span-4 space-y-6">
          {/* Logistics Tracking Card */}
          <div className="bg-primary-container p-8 rounded-3xl text-on-primary overflow-hidden relative">
            <div className="relative z-10">
              <h3 className="text-on-primary-container font-bold uppercase tracking-widest text-[10px] mb-4">Critical Metrics</h3>
              <div className="space-y-6">
                <div>
                  <div className="text-on-primary-container/60 text-[10px] uppercase font-bold mb-1">Current Temperature</div>
                  <div className="text-4xl font-extrabold font-headline">2.4°C</div>
                </div>
                <div>
                  <div className="text-on-primary-container/60 text-[10px] uppercase font-bold mb-1">Estimated Arrival</div>
                  <div className="text-xl font-bold">14:35 — SP Hub</div>
                </div>
              </div>
            </div>
            <Thermometer className="absolute -bottom-10 -right-10 w-40 h-40 opacity-10" />
          </div>

          {/* Shipping Destination */}
          <div className="bg-surface-container p-6 rounded-3xl border border-surface-container-highest/10">
            <h3 className="text-on-surface-variant font-bold uppercase tracking-widest text-[10px] mb-4">Shipping Route</h3>
            <div className="flex items-start gap-4">
              <div className="flex flex-col items-center gap-1">
                <div className="w-3 h-3 rounded-full border-2 border-primary" />
                <div className="w-[1px] h-8 bg-surface-container-highest" />
                <MapPin className="w-4 h-4 text-tertiary" />
              </div>
              <div className="flex flex-col justify-between h-20">
                <div>
                  <div className="text-xs font-bold text-primary">Valinhos Distribution Center</div>
                  <div className="text-[10px] text-on-surface-variant uppercase">Origin</div>
                </div>
                <div>
                  <div className="text-xs font-bold text-primary">São Paulo Regional Hub</div>
                  <div className="text-[10px] text-on-surface-variant uppercase">Destination</div>
                </div>
              </div>
            </div>
          </div>

          {/* Documentation */}
          <div className="bg-surface-container-low p-6 rounded-3xl border border-surface-container-highest/10">
            <h3 className="text-on-surface-variant font-bold uppercase tracking-widest text-[10px] mb-4">Documentation</h3>
            <ul className="space-y-3">
              <li className="flex items-center justify-between text-xs">
                <span className="flex items-center gap-2 text-on-surface-variant">
                  <FileText className="w-4 h-4" /> Invoice_8842.pdf
                </span>
                <Download className="w-4 h-4 text-primary cursor-pointer hover:scale-110 transition-transform" />
              </li>
              <li className="flex items-center justify-between text-xs">
                <span className="flex items-center gap-2 text-on-surface-variant">
                  <CheckCircle2 className="w-4 h-4" /> Quality_Cert_V2.pdf
                </span>
                <Download className="w-4 h-4 text-primary cursor-pointer hover:scale-110 transition-transform" />
              </li>
            </ul>
          </div>
        </div>
      </div>

      {/* Floating Action Bar */}
      <div className="fixed bottom-24 left-1/2 -translate-x-1/2 z-[60]">
        <button className="flex flex-col items-center group">
          <div className="w-16 h-16 bg-primary rounded-full flex items-center justify-center shadow-2xl group-hover:scale-105 group-active:scale-95 transition-all duration-300 bg-gradient-to-b from-primary to-primary-container">
            <RefreshCw className="w-8 h-8 text-white" />
          </div>
          <span className="font-bold text-[10px] uppercase tracking-widest text-primary mt-2 drop-shadow-sm">Atualizar Status</span>
        </button>
      </div>
    </motion.div>
  );
}
