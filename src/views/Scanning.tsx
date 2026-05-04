import { Flashlight, Image as ImageIcon, Keyboard, QrCode } from 'lucide-react';
import { motion } from 'motion/react';

export function Scanning() {
  return (
    <motion.div 
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="relative h-[calc(100vh-64px)] w-full overflow-hidden bg-black"
    >
      {/* Background Image (Warehouse) */}
      <div className="absolute inset-0 z-0">
        <img 
          src="https://picsum.photos/seed/warehouse/1920/1080" 
          alt="Warehouse" 
          className="w-full h-full object-cover opacity-60"
          referrerPolicy="no-referrer"
        />
      </div>

      {/* Scanner Overlay */}
      <div className="absolute inset-0 z-10 flex flex-col items-center justify-center p-6">
        <div className="w-full max-w-xs space-y-4 mb-8">
          <div className="bg-white/10 backdrop-blur-xl p-6 rounded-3xl border border-white/20 text-center">
            <h2 className="font-headline text-white text-xl font-bold">Escanear QR Code</h2>
            <p className="text-white/80 text-sm font-medium mt-2">Aponte para o QR Code da Ordem de Serviço</p>
          </div>
        </div>

        {/* Viewfinder */}
        <div className="relative w-72 h-72">
          {/* Corners */}
          <div className="absolute top-0 left-0 w-12 h-12 border-t-4 border-l-4 border-primary-container rounded-tl-3xl" />
          <div className="absolute top-0 right-0 w-12 h-12 border-t-4 border-r-4 border-primary-container rounded-tr-3xl" />
          <div className="absolute bottom-0 left-0 w-12 h-12 border-b-4 border-l-4 border-primary-container rounded-bl-3xl" />
          <div className="absolute bottom-0 right-0 w-12 h-12 border-b-4 border-r-4 border-primary-container rounded-br-3xl" />
          
          {/* Scanning Line */}
          <motion.div 
            animate={{ top: ['0%', '100%', '0%'] }}
            transition={{ duration: 3, repeat: Infinity, ease: "linear" }}
            className="absolute left-0 w-full h-[2px] bg-primary-container/60 shadow-[0_0_15px_#0047bc]"
          />
        </div>

        {/* Controls */}
        <div className="mt-12 flex flex-col items-center gap-8 w-full max-w-sm">
          <div className="flex items-center gap-6">
            <button className="w-16 h-16 rounded-full bg-white/10 backdrop-blur-md border border-white/20 flex items-center justify-center text-white hover:bg-white/20 transition-all active:scale-90">
              <Flashlight className="w-6 h-6" />
            </button>
            <button className="w-16 h-16 rounded-full bg-white/10 backdrop-blur-md border border-white/20 flex items-center justify-center text-white hover:bg-white/20 transition-all active:scale-90">
              <ImageIcon className="w-6 h-6" />
            </button>
          </div>
          
          <button className="w-full py-5 px-8 bg-surface-container-lowest text-primary font-headline font-bold rounded-2xl flex items-center justify-center gap-3 shadow-2xl active:scale-[0.98] transition-all uppercase tracking-widest text-xs">
            <Keyboard className="w-5 h-5" />
            Inserir Código Manualmente
          </button>
          
          <div className="bg-tertiary-container/90 text-on-tertiary-container px-6 py-2 rounded-full text-[10px] font-bold uppercase tracking-widest flex items-center gap-2">
            <div className="w-2 h-2 rounded-full bg-on-tertiary-container animate-pulse" />
            Scanner Ativo
          </div>
        </div>
      </div>
    </motion.div>
  );
}
