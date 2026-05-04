import { ChevronRight, Plus, Pill, Trash2, Route, ShieldCheck, Snowflake } from 'lucide-react';
import { motion } from 'motion/react';

export function NewOrder() {
  return (
    <motion.div 
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.95 }}
      className="max-w-5xl mx-auto px-6 pt-8 pb-32"
    >
      {/* Header */}
      <header className="mb-12">
        <div className="flex items-center gap-2 mb-4">
          <span className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant/60">Logistics Portal</span>
          <ChevronRight className="w-3 h-3 text-on-surface-variant/40" />
          <span className="text-[10px] font-bold uppercase tracking-widest text-primary">New Order</span>
        </div>
        <h1 className="text-4xl md:text-5xl font-extrabold tracking-tighter text-on-surface mb-2">Service Initiation</h1>
        <p className="text-on-surface-variant font-medium">Create a high-priority clinical distribution request.</p>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* Main Form */}
        <section className="lg:col-span-8 space-y-8">
          <div className="bg-surface-container-lowest p-8 rounded-3xl tonal-shadow">
            <h2 className="text-xl font-bold tracking-tight text-on-surface mb-8">Client & Logistics Details</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-x-10 gap-y-8">
              <div className="flex flex-col gap-2">
                <label className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">Client Name</label>
                <input 
                  className="bg-surface-container-low border-0 border-b-2 border-transparent focus:border-primary focus:ring-0 text-on-surface font-medium py-3 transition-all placeholder:text-on-surface-variant/40" 
                  placeholder="e.g. BioGen Laboratories" 
                  type="text" 
                />
              </div>
              <div className="flex flex-col gap-2">
                <label className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">Destination CD</label>
                <input 
                  className="bg-surface-container-low border-0 border-b-2 border-transparent focus:border-primary focus:ring-0 text-on-surface font-medium py-3 transition-all placeholder:text-on-surface-variant/40" 
                  placeholder="CD-8842-AMS" 
                  type="text" 
                />
              </div>
              <div className="flex flex-col gap-2">
                <label className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">Order Priority</label>
                <select className="bg-surface-container-low border-0 border-b-2 border-transparent focus:border-primary focus:ring-0 text-on-surface font-medium py-3 transition-all">
                  <option>Routine (48-72h)</option>
                  <option>Priority (24h)</option>
                  <option>Critical (Stat)</option>
                  <option>Cold-Chain Express</option>
                </select>
              </div>
              <div className="flex flex-col gap-2">
                <label className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">Expected Delivery Date</label>
                <input 
                  className="bg-surface-container-low border-0 border-b-2 border-transparent focus:border-primary focus:ring-0 text-on-surface font-medium py-3 transition-all" 
                  type="date" 
                />
              </div>
            </div>
          </div>

          {/* Manifest */}
          <div className="bg-surface-container-lowest p-8 rounded-3xl tonal-shadow">
            <div className="flex justify-between items-end mb-8">
              <div>
                <h2 className="text-xl font-bold tracking-tight text-on-surface">Shipment Manifest</h2>
                <p className="text-sm text-on-surface-variant">Specify pharmaceutical units and storage requirements.</p>
              </div>
              <button className="flex items-center gap-2 bg-primary-container text-on-primary px-4 py-2 rounded-xl font-bold text-[10px] uppercase tracking-widest transition-all hover:opacity-90">
                <Plus className="w-4 h-4" /> Add Item
              </button>
            </div>
            
            <div className="space-y-4">
              <div className="bg-surface-container p-4 rounded-2xl flex items-center gap-6">
                <div className="w-12 h-12 bg-white rounded-xl flex items-center justify-center text-primary">
                  <Plus className="w-6 h-6" />
                </div>
                <div className="flex-1">
                  <p className="text-sm font-bold text-on-surface">Insulin Glargine Vials</p>
                  <p className="text-[10px] text-on-surface-variant uppercase tracking-wider">Cold Chain Required (2-8°C)</p>
                </div>
                <div className="text-right">
                  <p className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-1">Quantity</p>
                  <p className="text-sm font-bold">450 Units</p>
                </div>
                <button className="text-error opacity-40 hover:opacity-100 transition-opacity">
                  <Trash2 className="w-5 h-5" />
                </button>
              </div>
            </div>
          </div>
        </section>

        {/* Summary Sidebar */}
        <aside className="lg:col-span-4 space-y-6">
          <div className="bg-surface-container-high p-8 rounded-3xl border-t-4 border-primary sticky top-24 tonal-shadow">
            <h3 className="text-[10px] font-black uppercase tracking-[0.2em] text-primary mb-6">Service Summary</h3>
            <div className="space-y-6 mb-10">
              <div className="flex justify-between items-start">
                <div>
                  <p className="text-[10px] text-on-surface-variant font-bold uppercase tracking-widest mb-1">Estimated Route</p>
                  <p className="text-sm font-medium">Direct Clinical Courier</p>
                </div>
                <Route className="w-5 h-5 text-primary/40" />
              </div>
              <div className="flex justify-between items-start">
                <div>
                  <p className="text-[10px] text-on-surface-variant font-bold uppercase tracking-widest mb-1">Handling Protocol</p>
                  <p className="text-sm font-medium">Level 4 Bio-Hazard Compliance</p>
                </div>
                <ShieldCheck className="w-5 h-5 text-primary/40" />
              </div>
              <div className="pt-4 border-t border-on-surface-variant/10">
                <p className="text-[10px] text-on-surface-variant font-bold uppercase tracking-widest mb-1">Service Fee Est.</p>
                <p className="text-3xl font-black tracking-tighter text-on-surface">$2,480.00</p>
              </div>
            </div>
            <button className="w-full bg-primary text-on-primary py-4 rounded-2xl font-bold uppercase tracking-widest text-xs shadow-xl shadow-primary/20 transition-all hover:scale-[1.02] active:scale-[0.98]">
              Finalize Order
            </button>
            <button className="w-full mt-4 text-on-surface-variant font-bold text-[10px] uppercase tracking-widest py-2 hover:text-primary transition-colors">
              Save as Draft
            </button>
          </div>

          {/* Thermal Stability */}
          <div className="bg-surface-container-lowest p-6 rounded-3xl tonal-shadow">
            <div className="flex items-center gap-3 mb-4">
              <Snowflake className="w-5 h-5 text-primary" />
              <h4 className="text-sm font-bold tracking-tight">Thermal Stability Check</h4>
            </div>
            <div className="h-2 w-full bg-surface-container-highest rounded-full overflow-hidden flex">
              <div className="h-full bg-primary w-2/3" />
            </div>
            <p className="text-[10px] uppercase font-bold text-on-surface-variant mt-2 tracking-tighter">Optimal Range: 2.0°C — 8.0°C</p>
          </div>
        </aside>
      </div>
    </motion.div>
  );
}
