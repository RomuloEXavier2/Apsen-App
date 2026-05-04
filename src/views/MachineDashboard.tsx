import { useState, useRef, useMemo, useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer,
} from 'recharts';
import {
  Clock, Zap, Wrench, TrendingUp, Package, Download,
  SlidersHorizontal, AlertCircle, X, Lock,
} from 'lucide-react';
import jsPDF from 'jspdf';
import html2canvas from 'html2canvas';
import { cn } from '@/src/lib/utils';
import { useAuth } from '@/src/contexts/AuthContext';
import { MACHINE_PERFORMANCE_DATA, TOP_MEDICATIONS } from '@/src/lib/mockData';

const METRICS = [
  { key: 'performance',        label: 'Performance',           unit: '%',  color: '#00328b' },
  { key: 'avgSeparationTime',  label: 'T. Médio / Sep.',       unit: 'min',color: '#942d00' },
  { key: 'operationHours',     label: 'Tempo de Operação',     unit: 'h',  color: '#0047bc' },
  { key: 'maintenanceHours',   label: 'Manutenção',            unit: 'h',  color: '#ba1a1a' },
  { key: 'totalSeparations',   label: 'Separações',            unit: '',   color: '#434653' },
] as const;

type MetricKey = (typeof METRICS)[number]['key'];

function ChartTooltip({ active, payload, label }: { active?: boolean; payload?: { dataKey: string; color: string; value: number }[]; label?: string }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-surface-container-lowest rounded-2xl shadow-xl border border-surface-container-high p-3 text-xs min-w-36">
      <p className="font-semibold text-on-surface mb-2">{label}</p>
      {payload.map((entry) => {
        const metric = METRICS.find(m => m.key === entry.dataKey);
        return (
          <div key={entry.dataKey} className="flex items-center gap-2 mb-1 last:mb-0">
            <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: entry.color }} />
            <span className="text-on-surface-variant flex-1">{metric?.label}</span>
            <span className="font-bold text-on-surface pl-2">
              {entry.value}{metric?.unit}
            </span>
          </div>
        );
      })}
    </div>
  );
}

interface KpiCardProps {
  icon: React.ReactNode;
  label: string;
  value: string;
  sub: string;
  accent: string;
}

function KpiCard({ icon, label, value, sub, accent }: KpiCardProps) {
  return (
    <div className="bg-surface-container-lowest rounded-3xl p-4 tonal-shadow flex flex-col gap-2.5">
      <div className={cn('w-9 h-9 rounded-2xl flex items-center justify-center', accent)}>
        {icon}
      </div>
      <div>
        <p className="text-[11px] text-on-surface-variant leading-tight">{label}</p>
        <p className="text-[22px] font-extrabold text-on-surface font-headline leading-tight mt-0.5">{value}</p>
        <p className="text-[10px] text-on-surface-variant opacity-60 mt-0.5">{sub}</p>
      </div>
    </div>
  );
}

export function MachineDashboard() {
  const { isAdmin } = useAuth();
  const reportRef = useRef<HTMLDivElement>(null);
  const filterRef = useRef<HTMLDivElement>(null);
  const [activeMetrics, setActiveMetrics] = useState<Set<MetricKey>>(new Set(['performance']));
  const [filterOpen, setFilterOpen] = useState(false);
  const [generatingPdf, setGeneratingPdf] = useState(false);

  useEffect(() => {
    if (!filterOpen) return;
    const onDown = (e: MouseEvent) => {
      if (filterRef.current && !filterRef.current.contains(e.target as Node)) {
        setFilterOpen(false);
      }
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [filterOpen]);

  const workingDays = useMemo(
    () => MACHINE_PERFORMANCE_DATA.filter(d => d.performance !== null),
    []
  );

  const kpis = useMemo(() => {
    const len = workingDays.length || 1;
    return {
      totalOp:    workingDays.reduce((s, d) => s + (d.operationHours ?? 0), 0),
      avgSep:     workingDays.reduce((s, d) => s + (d.avgSeparationTime ?? 0), 0) / len,
      avgPerf:    workingDays.reduce((s, d) => s + (d.performance ?? 0), 0) / len,
      avgMaint:   workingDays.reduce((s, d) => s + (d.maintenanceHours ?? 0), 0) / len,
      totalSep:   workingDays.reduce((s, d) => s + (d.totalSeparations ?? 0), 0),
      errorRate:  1.4,
    };
  }, [workingDays]);

  const toggleMetric = (key: MetricKey) => {
    setActiveMetrics(prev => {
      const next = new Set(prev);
      if (next.has(key) && next.size > 1) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  };

  const handleDownload = async () => {
    if (!isAdmin || !reportRef.current) return;
    setGeneratingPdf(true);
    try {
      const el = reportRef.current;
      const canvas = await html2canvas(el, {
        scale: 1.5,
        backgroundColor: '#f8f9fc',
        useCORS: true,
        logging: false,
        width: el.scrollWidth,
        height: el.scrollHeight,
      });
      const img = canvas.toDataURL('image/png');
      const pdf = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
      const pageW = pdf.internal.pageSize.getWidth();
      const pageH = pdf.internal.pageSize.getHeight();
      const ratio = canvas.width / pageW;
      const imgH = canvas.height / ratio;

      let remaining = imgH;
      let offset = 0;
      pdf.addImage(img, 'PNG', 0, 0, pageW, imgH);
      remaining -= pageH;
      while (remaining > 0) {
        offset += pageH;
        pdf.addPage();
        pdf.addImage(img, 'PNG', 0, -offset, pageW, imgH);
        remaining -= pageH;
      }
      pdf.save('dashboard-maquina-separacao.pdf');
    } catch (err) {
      console.error(err);
    } finally {
      setGeneratingPdf(false);
    }
  };

  if (!isAdmin) {
    return (
      <motion.div
        key="dashboard-locked"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -12 }}
        className="flex flex-col items-center justify-center min-h-[60vh] gap-4 px-8"
      >
        <div className="w-16 h-16 rounded-full bg-surface-container flex items-center justify-center">
          <Lock className="w-7 h-7 text-on-surface-variant" />
        </div>
        <p className="text-base font-semibold text-on-surface text-center">Acesso restrito</p>
        <p className="text-sm text-on-surface-variant text-center">
          Este dashboard está disponível apenas para administradores.
        </p>
      </motion.div>
    );
  }

  return (
    <motion.div
      key="dashboard"
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -12 }}
      transition={{ duration: 0.25 }}
      className="pb-32"
    >
      <div ref={reportRef} className="px-4 pt-4 space-y-4 bg-surface">

        {/* Header */}
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-xl font-extrabold text-on-surface font-headline">Performance</h2>
            <p className="text-xs text-on-surface-variant mt-0.5">
              Máquina de Separação · Últimos 30 dias
            </p>
          </div>
          <button
            onClick={handleDownload}
            disabled={generatingPdf}
            className="flex items-center gap-2 bg-primary text-on-primary text-xs font-bold px-4 py-2.5 rounded-2xl tonal-shadow active:scale-95 transition-all disabled:opacity-60 flex-shrink-0"
          >
            {generatingPdf ? (
              <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            ) : (
              <Download className="w-4 h-4" />
            )}
            {generatingPdf ? 'Gerando…' : 'Exportar PDF'}
          </button>
        </div>

        {/* KPI Cards — 2 col grid */}
        <div className="grid grid-cols-2 gap-3">
          <KpiCard
            icon={<Clock className="w-5 h-5" />}
            label="Tempo de Operação"
            value={`${kpis.totalOp.toFixed(0)}h`}
            sub="total no período"
            accent="bg-primary/10 text-primary"
          />
          <KpiCard
            icon={<Zap className="w-5 h-5" />}
            label="T. Médio / Separação"
            value={`${kpis.avgSep.toFixed(1)} min`}
            sub="por separação"
            accent="bg-tertiary-container/20 text-tertiary-container"
          />
          <KpiCard
            icon={<TrendingUp className="w-5 h-5" />}
            label="Performance"
            value={`${kpis.avgPerf.toFixed(1)}%`}
            sub="eficiência média"
            accent="bg-primary/10 text-primary"
          />
          <KpiCard
            icon={<Wrench className="w-5 h-5" />}
            label="T. Médio Manutenção"
            value={`${kpis.avgMaint.toFixed(1)} h/dia`}
            sub="tempo médio diário"
            accent="bg-error-container/40 text-error"
          />
          <KpiCard
            icon={<Package className="w-5 h-5" />}
            label="Total de Separações"
            value={kpis.totalSep.toLocaleString('pt-BR')}
            sub="no período"
            accent="bg-primary/10 text-primary"
          />
          <KpiCard
            icon={<AlertCircle className="w-5 h-5" />}
            label="Taxa de Erro"
            value={`${kpis.errorRate}%`}
            sub="itens com divergência"
            accent="bg-tertiary-container/20 text-tertiary-container"
          />
        </div>

        {/* Chart Section */}
        <div className="bg-surface-container-lowest rounded-3xl p-4 tonal-shadow">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-sm font-bold text-on-surface font-headline">Evolução dos Indicadores</h3>
              <p className="text-[11px] text-on-surface-variant mt-0.5">Use Filtrar para escolher métricas</p>
            </div>

            {/* Filter button + popover */}
            <div className="relative" ref={filterRef}>
              <button
                onClick={() => setFilterOpen(v => !v)}
                className={cn(
                  'flex items-center gap-1.5 text-xs font-bold px-3 py-2 rounded-xl border transition-all',
                  filterOpen
                    ? 'bg-primary text-on-primary border-primary'
                    : 'bg-surface-container border-surface-container-highest text-on-surface'
                )}
              >
                <SlidersHorizontal className="w-3.5 h-3.5" />
                Filtrar
              </button>

              <AnimatePresence>
                {filterOpen && (
                  <motion.div
                    initial={{ opacity: 0, scale: 0.95, y: -6 }}
                    animate={{ opacity: 1, scale: 1, y: 0 }}
                    exit={{ opacity: 0, scale: 0.95, y: -6 }}
                    transition={{ duration: 0.15 }}
                    className="absolute right-0 top-full mt-2 w-60 bg-surface-container-lowest rounded-2xl shadow-xl border border-surface-container-high z-50 p-3"
                  >
                    <div className="flex items-center justify-between mb-3">
                      <span className="text-xs font-bold text-on-surface">Indicadores no Gráfico</span>
                      <button onClick={() => setFilterOpen(false)} className="text-on-surface-variant hover:text-on-surface transition-colors">
                        <X className="w-4 h-4" />
                      </button>
                    </div>
                    <div className="space-y-0.5">
                      {METRICS.map(metric => {
                        const active = activeMetrics.has(metric.key);
                        return (
                          <button
                            key={metric.key}
                            onClick={() => toggleMetric(metric.key)}
                            className="w-full flex items-center gap-3 px-2 py-2 rounded-xl hover:bg-surface-container-low transition-colors text-left"
                          >
                            <span
                              className="w-4 h-4 rounded flex items-center justify-center flex-shrink-0 border-2 transition-all"
                              style={active ? { backgroundColor: metric.color, borderColor: metric.color } : { borderColor: '#e1e2e5' }}
                            >
                              {active && (
                                <svg className="w-2.5 h-2.5 text-white" viewBox="0 0 10 10" fill="none">
                                  <path d="M1.5 5L4 7.5L8.5 2.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                                </svg>
                              )}
                            </span>
                            <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: metric.color }} />
                            <span className="text-xs text-on-surface flex-1">{metric.label}</span>
                            {metric.unit && (
                              <span className="text-[10px] text-on-surface-variant">{metric.unit}</span>
                            )}
                          </button>
                        );
                      })}
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          </div>

          {/* Recharts line chart */}
          <ResponsiveContainer width="100%" height={220}>
            <LineChart
              data={MACHINE_PERFORMANCE_DATA}
              margin={{ top: 4, right: 4, bottom: 0, left: -22 }}
            >
              <CartesianGrid strokeDasharray="3 3" stroke="#e1e2e5" vertical={false} />
              <XAxis
                dataKey="date"
                tick={{ fontSize: 9, fill: '#434653' }}
                tickLine={false}
                axisLine={false}
                interval={4}
              />
              <YAxis
                tick={{ fontSize: 9, fill: '#434653' }}
                tickLine={false}
                axisLine={false}
              />
              <Tooltip content={<ChartTooltip />} />
              {METRICS.filter(m => activeMetrics.has(m.key)).map(metric => (
                <Line
                  key={metric.key}
                  type="monotone"
                  dataKey={metric.key}
                  stroke={metric.color}
                  strokeWidth={2}
                  dot={false}
                  activeDot={{ r: 4, strokeWidth: 0 }}
                  connectNulls={false}
                />
              ))}
            </LineChart>
          </ResponsiveContainer>

          {/* Chart legend */}
          <div className="flex flex-wrap gap-x-4 gap-y-1.5 mt-3 pt-3 border-t border-surface-container">
            {METRICS.filter(m => activeMetrics.has(m.key)).map(metric => (
              <div key={metric.key} className="flex items-center gap-1.5">
                <span className="w-5 h-0.5 rounded-full" style={{ backgroundColor: metric.color }} />
                <span className="text-[10px] text-on-surface-variant">
                  {metric.label}{metric.unit ? ` (${metric.unit})` : ''}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Top Medications */}
        <div className="bg-surface-container-lowest rounded-3xl p-4 tonal-shadow">
          <h3 className="text-sm font-bold text-on-surface font-headline mb-1">Maior Saída de Medicamentos</h3>
          <p className="text-[11px] text-on-surface-variant mb-3">Ranking acumulado no período</p>
          <div className="space-y-0">
            {TOP_MEDICATIONS.map((med) => (
              <div
                key={med.rank}
                className="flex items-center gap-3 py-2.5 border-b border-surface-container last:border-0"
              >
                <span className="text-xs font-black text-on-surface-variant w-5 text-center flex-shrink-0">
                  {med.rank}
                </span>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-on-surface truncate">{med.name}</p>
                  <p className="text-[11px] text-on-surface-variant truncate">{med.substance}</p>
                </div>
                <div className="text-right flex-shrink-0">
                  <p className="text-sm font-bold text-on-surface">
                    {med.count.toLocaleString('pt-BR')}
                  </p>
                  <p className={cn(
                    'text-[11px] font-bold',
                    med.change > 0 ? 'text-primary' : 'text-error'
                  )}>
                    {med.change > 0 ? '+' : ''}{med.change}%
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Footer note */}
        <p className="text-[10px] text-on-surface-variant/50 text-center pb-2">
          Relatório gerado em {new Date().toLocaleDateString('pt-BR', { day: '2-digit', month: 'long', year: 'numeric' })}
        </p>
      </div>
    </motion.div>
  );
}
