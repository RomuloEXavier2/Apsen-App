import { useState, useId, type ReactNode } from 'react';
import { ChevronRight, Plus, Pill, Trash2, Route, ShieldCheck, Snowflake, AlertCircle, CheckCircle2, MapPin, Package } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { useAuth } from '@/src/contexts/AuthContext';
import { useOrders } from '@/src/contexts/OrdersContext';
import { ServiceOrder, ActivityLog, ManifestItem } from '@/src/types';
import { cn } from '@/src/lib/utils';

// ── Drug catalogue ────────────────────────────────────────────────────────────
const APSEN_DRUGS = [
  { name: 'Donaren®',  subName: 'Trazodona HCl',         coldChain: false, armz: 'ARMZ-B', unitPrice: 15.50 },
  { name: 'Flancox®',  subName: 'Etodolaco',              coldChain: false, armz: 'ARMZ-B', unitPrice: 22.00 },
  { name: 'Alois®',    subName: 'Memantina HCl',          coldChain: true,  armz: 'ARMZ-C', unitPrice: 45.00 },
  { name: 'Miosan',    subName: 'Ciclobenzaprina HCl',    coldChain: false, armz: 'ARMZ-B', unitPrice: 8.00  },
  { name: 'Atentah',   subName: 'Atomoxetina HCl',        coldChain: false, armz: 'ARMZ-B', unitPrice: 38.00 },
] as const;

const PRIORITY_OPTIONS: { label: string; value: ServiceOrder['priority'] }[] = [
  { label: 'Rotina (48–72h)',       value: 'Normal'  },
  { label: 'Prioritário (24h)',     value: 'Alta'    },
  { label: 'Crítico (Stat)',        value: 'Urgente' },
  { label: 'Cadeia Fria Expressa',  value: 'Alta'    },
  { label: 'Baixa prioridade',      value: 'Baixa'   },
];

// ── Helpers ───────────────────────────────────────────────────────────────────
function genBatch(): string {
  const yr = new Date().getFullYear().toString().slice(2);
  return `#B${yr}-APS-${String(Math.floor(Math.random() * 500) + 500)}`;
}

function genStockBox(coldChain: boolean): string {
  const zone = coldChain ? 'C' : String.fromCharCode(65 + Math.floor(Math.random() * 2));
  const line = Math.floor(Math.random() * 4) + 1;
  const box  = String(Math.floor(Math.random() * 20) + 1).padStart(2, '0');
  return `ARMZ-${zone}-L${line}-C${box}`;
}

function formatDate(iso: string): string {
  if (!iso) return new Date().toLocaleDateString('pt-BR', { day: 'numeric', month: 'short', year: 'numeric' });
  const [y, m, d] = iso.split('-');
  const months = ['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez'];
  return `${parseInt(d)} ${months[parseInt(m) - 1]}, ${y}`;
}

function genOrderId(count: number): string {
  return `OS-${new Date().getFullYear()}-${String(count + 1).padStart(3, '0')}`;
}

// ── Types ─────────────────────────────────────────────────────────────────────
interface FormItem {
  uid: string;
  drugIdx: number;
  qty: string;
  batch: string;
  stockBox: string;
}

interface FormErrors {
  client?: string;
  destination?: string;
  deliveryDate?: string;
  items?: string;
  [key: string]: string | undefined;
}

interface NewOrderProps {
  onSuccess: (order: ServiceOrder) => void;
}

// ── Field component ───────────────────────────────────────────────────────────
function Field({ label, error, children, required }: {
  label: string; error?: string; children: ReactNode; required?: boolean;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
        {label}{required && <span className="text-error ml-0.5">*</span>}
      </label>
      {children}
      {error && (
        <span className="flex items-center gap-1 text-[10px] text-error font-bold">
          <AlertCircle className="w-3 h-3" />{error}
        </span>
      )}
    </div>
  );
}

// ── Main component ────────────────────────────────────────────────────────────
export function NewOrder({ onSuccess }: NewOrderProps) {
  const { user, isAdmin } = useAuth();
  const { orders, addOrder, addLog } = useOrders();
  const uid = useId();

  // Form state
  const [client, setClient]           = useState('');
  const [destination, setDestination] = useState('');
  const [origin]                      = useState('Centro de Distribuição Valinhos — SP');
  const [priority, setPriority]       = useState<ServiceOrder['priority']>('Normal');
  const [deliveryDate, setDeliveryDate] = useState('');
  const [notes, setNotes]             = useState('');
  const [items, setItems]             = useState<FormItem[]>([]);
  const [errors, setErrors]           = useState<FormErrors>({});
  const [submitting, setSubmitting]   = useState(false);

  // ── Derived ──────────────────────────────────────────────────────────────────
  const hasColdChain  = items.some(i => APSEN_DRUGS[i.drugIdx].coldChain);
  const totalUnits    = items.reduce((s, i) => s + (parseInt(i.qty) || 0), 0);
  const totalValue    = items.reduce((s, i) => {
    const d = APSEN_DRUGS[i.drugIdx];
    return s + (parseInt(i.qty) || 0) * d.unitPrice;
  }, 0);
  const isFormReady   = client.trim() && destination.trim() && deliveryDate && items.length > 0
    && items.every(i => parseInt(i.qty) > 0);

  // ── Item helpers ─────────────────────────────────────────────────────────────
  const addItem = () => {
    const drug = APSEN_DRUGS[0];
    setItems(prev => [...prev, {
      uid: `${uid}-${Date.now()}`,
      drugIdx: 0,
      qty: '',
      batch: genBatch(),
      stockBox: genStockBox(drug.coldChain),
    }]);
    setErrors(e => ({ ...e, items: undefined }));
  };

  const removeItem = (uid: string) =>
    setItems(prev => prev.filter(i => i.uid !== uid));

  const updateItem = (uid: string, patch: Partial<FormItem>) =>
    setItems(prev => prev.map(i => i.uid === uid ? { ...i, ...patch } : i));

  const changeDrug = (uid: string, drugIdx: number) => {
    const drug = APSEN_DRUGS[drugIdx];
    updateItem(uid, {
      drugIdx,
      batch: genBatch(),
      stockBox: genStockBox(drug.coldChain),
    });
  };

  // ── Validation ────────────────────────────────────────────────────────────────
  const validate = (): FormErrors => {
    const e: FormErrors = {};
    if (!client.trim())       e.client       = 'Campo obrigatório';
    if (!destination.trim())  e.destination  = 'Campo obrigatório';
    if (!deliveryDate)        e.deliveryDate = 'Campo obrigatório';
    if (items.length === 0)   e.items        = 'Adicione pelo menos um medicamento';
    else if (items.some(i => !parseInt(i.qty) || parseInt(i.qty) <= 0))
      e.items = 'Todos os itens precisam de uma quantidade válida (> 0)';
    return e;
  };

  // ── Submit ────────────────────────────────────────────────────────────────────
  const handleSubmit = (status: ServiceOrder['status']) => {
    const errs = validate();
    if (Object.keys(errs).length > 0) { setErrors(errs); return; }

    setSubmitting(true);

    const manifestItems: ManifestItem[] = items.map(item => {
      const drug = APSEN_DRUGS[item.drugIdx];
      return {
        name:       drug.name,
        subName:    drug.subName,
        qty:        parseInt(item.qty).toLocaleString('pt-BR'),
        batch:      item.batch,
        coldChain:  drug.coldChain,
        stockBox:   item.stockBox,
      };
    });

    const newOrder: ServiceOrder = {
      id:          genOrderId(orders.length),
      client:      client.trim(),
      destination: destination.trim(),
      date:        formatDate(deliveryDate),
      status,
      priority,
      origin,
      temperature: hasColdChain ? '2.4°C' : undefined,
      eta:         '14:35',
      totalValue,
      items:       manifestItems,
    };

    const log: ActivityLog = {
      id:        `log-${Date.now()}`,
      timestamp: new Date().toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).replace(',', ''),
      orderId:   newOrder.id,
      userId:    user!.id,
      userName:  user!.name,
      userRole:  user!.role,
      action:    'Criação',
      details:   `Ordem ${status === 'Aguardando' ? 'salva como rascunho' : 'criada'} para ${newOrder.client} — ${newOrder.destination}. Prioridade: ${newOrder.priority}. ${notes ? 'Obs: ' + notes : ''}`.trim(),
    };

    addOrder(newOrder);
    addLog(log);

    setTimeout(() => {
      setSubmitting(false);
      onSuccess(newOrder);
    }, 400);
  };

  // ── Render ────────────────────────────────────────────────────────────────────
  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.98 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.98 }}
      className="max-w-5xl mx-auto px-6 pt-8 pb-32"
    >
      {/* Header */}
      <header className="mb-10">
        <div className="flex items-center gap-2 mb-4">
          <span className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant/60">Portal de Logística</span>
          <ChevronRight className="w-3 h-3 text-on-surface-variant/40" />
          <span className="text-[10px] font-bold uppercase tracking-widest text-primary">Nova Ordem</span>
        </div>
        <h1 className="text-4xl md:text-5xl font-extrabold tracking-tighter text-on-surface mb-2">Abertura de Ordem</h1>
        <p className="text-on-surface-variant font-medium">Preencha todos os campos marcados com <span className="text-error">*</span> para finalizar a ordem.</p>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* ── Main form ─────────────────────────────────────────────────── */}
        <section className="lg:col-span-8 space-y-6">

          {/* Client & Logistics */}
          <div className="bg-surface-container-lowest p-8 rounded-3xl tonal-shadow">
            <h2 className="text-base font-bold tracking-tight text-on-surface mb-6 flex items-center gap-2">
              <MapPin className="w-4 h-4 text-primary" /> Dados do Cliente e Logística
            </h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-6">

              <Field label="Nome do Cliente" error={errors.client} required>
                <input
                  value={client}
                  onChange={e => { setClient(e.target.value); setErrors(v => ({ ...v, client: undefined })); }}
                  className={cn(
                    'bg-surface-container-low border-0 border-b-2 focus:ring-0 text-on-surface font-medium py-3 transition-all placeholder:text-on-surface-variant/40',
                    errors.client ? 'border-error' : 'border-transparent focus:border-primary'
                  )}
                  placeholder="ex: Droga Raia S.A."
                />
              </Field>

              <Field label="CD de Destino" error={errors.destination} required>
                <input
                  value={destination}
                  onChange={e => { setDestination(e.target.value); setErrors(v => ({ ...v, destination: undefined })); }}
                  className={cn(
                    'bg-surface-container-low border-0 border-b-2 focus:ring-0 text-on-surface font-medium py-3 transition-all placeholder:text-on-surface-variant/40',
                    errors.destination ? 'border-error' : 'border-transparent focus:border-primary'
                  )}
                  placeholder="Hub Logístico — Cajamar, SP"
                />
              </Field>

              <Field label="Prioridade da Ordem" required>
                <select
                  value={priority}
                  onChange={e => setPriority(e.target.value as ServiceOrder['priority'])}
                  className="bg-surface-container-low border-0 border-b-2 border-transparent focus:border-primary focus:ring-0 text-on-surface font-medium py-3 transition-all"
                >
                  {PRIORITY_OPTIONS.map(opt => (
                    <option key={opt.label} value={opt.value}>{opt.label}</option>
                  ))}
                </select>
              </Field>

              <Field label="Data de Entrega Prevista" error={errors.deliveryDate} required>
                <input
                  type="date"
                  value={deliveryDate}
                  onChange={e => { setDeliveryDate(e.target.value); setErrors(v => ({ ...v, deliveryDate: undefined })); }}
                  className={cn(
                    'bg-surface-container-low border-0 border-b-2 focus:ring-0 text-on-surface font-medium py-3 transition-all',
                    errors.deliveryDate ? 'border-error' : 'border-transparent focus:border-primary'
                  )}
                />
              </Field>

              <div className="md:col-span-2">
                <Field label="Origem (pré-preenchida)">
                  <input
                    readOnly
                    value={origin}
                    className="bg-surface-container-low border-0 border-b-2 border-transparent text-on-surface-variant font-medium py-3 cursor-not-allowed opacity-60"
                  />
                </Field>
              </div>

              <div className="md:col-span-2">
                <Field label="Observações (opcional)">
                  <textarea
                    value={notes}
                    onChange={e => setNotes(e.target.value)}
                    rows={2}
                    className="bg-surface-container-low border-0 border-b-2 border-transparent focus:border-primary focus:ring-0 text-on-surface font-medium py-3 transition-all placeholder:text-on-surface-variant/40 resize-none"
                    placeholder="Requisitos especiais, instruções de entrega..."
                  />
                </Field>
              </div>
            </div>
          </div>

          {/* Manifest */}
          <div className="bg-surface-container-lowest p-8 rounded-3xl tonal-shadow">
            <div className="flex justify-between items-end mb-6">
              <div>
                <h2 className="text-base font-bold tracking-tight text-on-surface flex items-center gap-2">
                  <Package className="w-4 h-4 text-primary" /> Manifesto de Envio
                  {items.length > 0 && (
                    <span className="ml-1 px-2 py-0.5 rounded-full bg-primary text-white text-[10px] font-black">
                      {items.length}
                    </span>
                  )}
                </h2>
                <p className="text-xs text-on-surface-variant mt-1">Lote e caixa gerados automaticamente; edite se necessário.</p>
              </div>
              <button
                type="button"
                onClick={addItem}
                className="flex items-center gap-2 bg-primary-container text-on-primary px-4 py-2 rounded-xl font-bold text-[10px] uppercase tracking-widest transition-all hover:opacity-90 active:scale-95"
              >
                <Plus className="w-4 h-4" /> Adicionar Item
              </button>
            </div>

            {errors.items && (
              <div className="flex items-center gap-2 text-error text-xs font-bold mb-4 bg-error/5 p-3 rounded-xl">
                <AlertCircle className="w-4 h-4 flex-shrink-0" /> {errors.items}
              </div>
            )}

            {items.length === 0 && (
              <div className="border-2 border-dashed border-surface-container-highest rounded-2xl p-10 text-center">
                <Pill className="w-10 h-10 text-on-surface-variant/20 mx-auto mb-3" />
                <p className="text-sm text-on-surface-variant/60 font-medium">Nenhum medicamento adicionado.</p>
                <p className="text-xs text-on-surface-variant/40 mt-1">Clique em "Adicionar Item" para começar.</p>
              </div>
            )}

            <AnimatePresence initial={false}>
              <div className="space-y-4">
                {items.map((item, idx) => {
                  const drug = APSEN_DRUGS[item.drugIdx];
                  return (
                    <motion.div
                      key={item.uid}
                      initial={{ opacity: 0, y: -8 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, x: 20, height: 0, marginBottom: 0 }}
                      transition={{ duration: 0.18 }}
                      className="bg-surface-container rounded-2xl overflow-hidden border border-surface-container-highest/10"
                    >
                      {/* Item header */}
                      <div className="flex items-center gap-4 p-4 border-b border-surface-container-highest/10">
                        <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center text-primary text-xs font-black flex-shrink-0">
                          {idx + 1}
                        </div>
                        <select
                          value={item.drugIdx}
                          onChange={e => changeDrug(item.uid, parseInt(e.target.value))}
                          className="flex-1 bg-transparent border-none text-on-surface font-bold text-sm focus:ring-0 cursor-pointer"
                        >
                          {APSEN_DRUGS.map((d, i) => (
                            <option key={i} value={i}>{d.name} — {d.subName}</option>
                          ))}
                        </select>
                        {drug.coldChain && (
                          <span className="flex items-center gap-1 text-[10px] font-bold text-primary bg-primary/10 px-2.5 py-1 rounded-full flex-shrink-0">
                            <Snowflake className="w-3 h-3" /> Cadeia Fria
                          </span>
                        )}
                        <button
                          type="button"
                          onClick={() => removeItem(item.uid)}
                          className="p-1.5 text-error/40 hover:text-error hover:bg-error/5 rounded-lg transition-all flex-shrink-0"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>

                      {/* Item fields */}
                      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 p-4">
                        <div className="flex flex-col gap-1">
                          <label className="text-[9px] font-bold uppercase tracking-widest text-on-surface-variant">
                            Quantidade<span className="text-error">*</span>
                          </label>
                          <input
                            type="number"
                            min="1"
                            value={item.qty}
                            onChange={e => { updateItem(item.uid, { qty: e.target.value }); setErrors(v => ({ ...v, items: undefined })); }}
                            placeholder="0"
                            className={cn(
                              'bg-surface-container-low border-0 border-b-2 focus:ring-0 text-on-surface font-bold py-2 text-sm transition-all placeholder:text-on-surface-variant/40',
                              item.qty && parseInt(item.qty) <= 0 ? 'border-error' : 'border-transparent focus:border-primary'
                            )}
                          />
                        </div>

                        <div className="flex flex-col gap-1">
                          <label className="text-[9px] font-bold uppercase tracking-widest text-on-surface-variant">Nº Lote</label>
                          <input
                            value={item.batch}
                            onChange={e => updateItem(item.uid, { batch: e.target.value })}
                            className="bg-surface-container-low border-0 border-b-2 border-transparent focus:border-primary focus:ring-0 text-on-surface font-mono py-2 text-xs transition-all"
                          />
                        </div>

                        <div className="flex flex-col gap-1 md:col-span-2">
                          <label className="text-[9px] font-bold uppercase tracking-widest text-on-surface-variant">Caixa de Estoque</label>
                          <input
                            value={item.stockBox}
                            onChange={e => updateItem(item.uid, { stockBox: e.target.value })}
                            className="bg-surface-container-low border-0 border-b-2 border-transparent focus:border-primary focus:ring-0 text-on-surface font-mono py-2 text-xs transition-all"
                          />
                        </div>
                      </div>
                    </motion.div>
                  );
                })}
              </div>
            </AnimatePresence>
          </div>
        </section>

        {/* ── Sidebar ───────────────────────────────────────────────────── */}
        <aside className="lg:col-span-4 space-y-6">
          <div className="bg-surface-container-high p-8 rounded-3xl border-t-4 border-primary sticky top-24 tonal-shadow">
            <h3 className="text-[10px] font-black uppercase tracking-[0.2em] text-primary mb-6">Resumo do Serviço</h3>

            {/* Live summary */}
            <div className="space-y-4 mb-8">

              <div>
                <p className="text-[10px] text-on-surface-variant font-bold uppercase tracking-widest mb-1">Cliente</p>
                <p className="text-sm font-bold text-on-surface truncate">
                  {client.trim() || <span className="text-on-surface-variant/40 font-normal italic">—</span>}
                </p>
              </div>

              <div className="flex gap-4">
                <div className="flex-1">
                  <p className="text-[10px] text-on-surface-variant font-bold uppercase tracking-widest mb-1">Itens</p>
                  <p className="text-sm font-bold text-on-surface">{items.length}</p>
                </div>
                <div className="flex-1">
                  <p className="text-[10px] text-on-surface-variant font-bold uppercase tracking-widest mb-1">Unidades</p>
                  <p className="text-sm font-bold text-on-surface">{totalUnits > 0 ? totalUnits.toLocaleString('pt-BR') : '—'}</p>
                </div>
              </div>

              <div className="flex justify-between items-start">
                <div>
                  <p className="text-[10px] text-on-surface-variant font-bold uppercase tracking-widest mb-1">Rota</p>
                  <p className="text-xs font-medium text-on-surface">Valinhos → {destination.trim() || '—'}</p>
                </div>
                <Route className="w-5 h-5 text-primary/40 flex-shrink-0" />
              </div>

              <div className="flex justify-between items-start">
                <div>
                  <p className="text-[10px] text-on-surface-variant font-bold uppercase tracking-widest mb-1">Protocolo</p>
                  <p className="text-xs font-medium">Bio-Hazard Nível 4</p>
                </div>
                <ShieldCheck className="w-5 h-5 text-primary/40 flex-shrink-0" />
              </div>

              {hasColdChain && (
                <div className="flex items-center gap-2 bg-primary/5 px-3 py-2 rounded-xl">
                  <Snowflake className="w-4 h-4 text-primary flex-shrink-0" />
                  <p className="text-[10px] font-bold text-primary uppercase tracking-wider">Cadeia fria necessária — 2–8°C</p>
                </div>
              )}

              {/* Admin-only cost */}
              {isAdmin && (
                <div className="pt-4 border-t border-on-surface-variant/10">
                  <p className="text-[10px] text-on-surface-variant font-bold uppercase tracking-widest mb-1">
                    Valor Estimado da Ordem
                  </p>
                  <p className="text-2xl font-black tracking-tighter text-on-surface">
                    {totalValue > 0
                      ? totalValue.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
                      : '—'}
                  </p>
                  <p className="text-[9px] text-on-surface-variant/50 mt-1 uppercase tracking-wider">Visível somente para Admins</p>
                </div>
              )}
            </div>

            {/* Validation summary */}
            {!isFormReady && (
              <div className="mb-4 space-y-1.5">
                {!client.trim() && <p className="text-[10px] text-on-surface-variant flex items-center gap-1.5"><span className="w-1.5 h-1.5 rounded-full bg-error/40 flex-shrink-0" /> Nome do cliente</p>}
                {!destination.trim() && <p className="text-[10px] text-on-surface-variant flex items-center gap-1.5"><span className="w-1.5 h-1.5 rounded-full bg-error/40 flex-shrink-0" /> CD de destino</p>}
                {!deliveryDate && <p className="text-[10px] text-on-surface-variant flex items-center gap-1.5"><span className="w-1.5 h-1.5 rounded-full bg-error/40 flex-shrink-0" /> Data de entrega</p>}
                {items.length === 0 && <p className="text-[10px] text-on-surface-variant flex items-center gap-1.5"><span className="w-1.5 h-1.5 rounded-full bg-error/40 flex-shrink-0" /> Pelo menos 1 medicamento</p>}
              </div>
            )}

            {isFormReady && (
              <div className="flex items-center gap-1.5 mb-4 text-[10px] font-bold text-primary">
                <CheckCircle2 className="w-4 h-4" /> Pronto para finalizar
              </div>
            )}

            <button
              type="button"
              onClick={() => handleSubmit('Em Separação')}
              disabled={submitting}
              className="w-full bg-primary text-on-primary py-4 rounded-2xl font-bold uppercase tracking-widest text-xs shadow-xl shadow-primary/20 transition-all hover:scale-[1.02] active:scale-[0.98] disabled:opacity-60 disabled:cursor-not-allowed disabled:scale-100"
            >
              {submitting ? 'Registrando...' : 'Finalizar Ordem'}
            </button>
            <button
              type="button"
              onClick={() => handleSubmit('Aguardando')}
              disabled={submitting || !client.trim() || !destination.trim()}
              className="w-full mt-3 text-on-surface-variant font-bold text-[10px] uppercase tracking-widest py-2 hover:text-primary transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
              Salvar Rascunho
            </button>
          </div>

          {/* Thermal stability */}
          <div className="bg-surface-container-lowest p-6 rounded-3xl tonal-shadow">
            <div className="flex items-center gap-3 mb-4">
              <Snowflake className={cn('w-5 h-5', hasColdChain ? 'text-primary' : 'text-on-surface-variant/30')} />
              <h4 className="text-sm font-bold tracking-tight">Estabilidade Térmica</h4>
            </div>
            <div className="h-2 w-full bg-surface-container-highest rounded-full overflow-hidden">
              <motion.div
                className={cn('h-full rounded-full', hasColdChain ? 'bg-primary' : 'bg-surface-container-high')}
                animate={{ width: hasColdChain ? '85%' : '0%' }}
                transition={{ duration: 0.5 }}
              />
            </div>
            <p className="text-[10px] uppercase font-bold text-on-surface-variant mt-2 tracking-tighter">
              {hasColdChain ? 'Faixa necessária: 2,0°C — 8,0°C' : 'Sem itens de cadeia fria'}
            </p>
          </div>
        </aside>
      </div>
    </motion.div>
  );
}
