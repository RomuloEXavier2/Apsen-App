import { ServiceOrder, ActivityLog, RouteStop } from '@/src/types';

export interface DailyPerformance {
  date: string;
  performance: number | null;
  avgSeparationTime: number | null;
  operationHours: number | null;
  maintenanceHours: number | null;
  totalSeparations: number | null;
}

export interface TopMedication {
  rank: number;
  name: string;
  substance: string;
  count: number;
  change: number;
}

function rng(s: number): number {
  const x = Math.sin(s * 9301 + 49297) * 10000;
  return x - Math.floor(x);
}

export const MACHINE_PERFORMANCE_DATA: DailyPerformance[] = (() => {
  const result: DailyPerformance[] = [];
  const base = new Date(2026, 3, 5); // April 5, 2026
  for (let i = 0; i < 30; i++) {
    const d = new Date(base);
    d.setDate(base.getDate() + i);
    const isWeekend = d.getDay() === 0 || d.getDay() === 6;
    const label = d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
    if (isWeekend) {
      result.push({ date: label, performance: null, avgSeparationTime: null, operationHours: null, maintenanceHours: null, totalSeparations: null });
    } else {
      result.push({
        date: label,
        performance: Math.round(68 + rng(i * 7 + 1) * 27),
        avgSeparationTime: +((2.8 + rng(i * 7 + 2) * 4.2).toFixed(1)),
        operationHours: Math.round(8 + rng(i * 7 + 3) * 6),
        maintenanceHours: +((rng(i * 7 + 4) * 2.8).toFixed(1)),
        totalSeparations: Math.round(28 + rng(i * 7 + 5) * 52),
      });
    }
  }
  return result;
})();

export const TOP_MEDICATIONS: TopMedication[] = [
  { rank: 1, name: 'Donaren®',  substance: 'Trazodona HCl',           count: 14820, change: +5.2 },
  { rank: 2, name: 'Alois®',    substance: 'Memantina HCl',            count: 12350, change: +2.1 },
  { rank: 3, name: 'Flancox®',  substance: 'Etodolaco',                count: 9870,  change: -1.3 },
  { rank: 4, name: 'Miosan',    substance: 'Ciclobenzaprina HCl',      count: 8640,  change: +8.7 },
  { rank: 5, name: 'Atentah',   substance: 'Atomoxetina HCl',          count: 7210,  change: +3.4 },
  { rank: 6, name: 'Benicar®',  substance: 'Olmesartana Medoxomila',   count: 6890,  change: -4.2 },
  { rank: 7, name: 'Crestor®',  substance: 'Rosuvastatina Cálcica',    count: 5340,  change: +1.9 },
  { rank: 8, name: 'Novalgina®',substance: 'Dipirona Sódica',          count: 4980,  change: -0.8 },
];

export const MOCK_ORDERS: ServiceOrder[] = [
  {
    id: 'OS-2024-001',
    client: 'Droga Raia S.A.',
    destination: 'Hub Logístico — Cajamar, SP',
    date: '12 Out, 2023',
    status: 'Em Separação',
    priority: 'Alta',
    origin: 'Centro de Distribuição Valinhos — SP',
    temperature: '2.4°C',
    eta: '14:35',
    totalValue: 62450.0,
    items: [
      { name: 'Donaren®', subName: 'Trazodona HCl', qty: '850', batch: '#B24-APS-442', coldChain: false, stockBox: 'ARMZ-B-L3-C04' },
      { name: 'Flancox®', subName: 'Etodolaco', qty: '1.200', batch: '#B24-APS-091', coldChain: false, stockBox: 'ARMZ-B-L3-C07' },
      { name: 'Alois®', subName: 'Memantina HCl', qty: '2.400', batch: '#B24-APS-218', coldChain: true, stockBox: 'ARMZ-C-L1-C02' },
      { name: 'Miosan', subName: 'Ciclobenzaprina HCl', qty: '600', batch: '#B24-APS-331', coldChain: false, stockBox: 'ARMZ-B-L4-C11' },
      { name: 'Atentah', subName: 'Atomoxetina HCl', qty: '900', batch: '#B24-APS-157', coldChain: false, stockBox: 'ARMZ-B-L4-C15' },
    ],
  },
  {
    id: 'OS-2024-002',
    client: 'Hospital Albert Einstein',
    destination: 'Unidade Morumbi — São Paulo, SP',
    date: '13 Out, 2023',
    status: 'Aguardando',
    priority: 'Normal',
    origin: 'Centro de Distribuição Valinhos — SP',
    totalValue: 38900.0,
    items: [
      { name: 'Alois®', subName: 'Memantina HCl', qty: '1.200', batch: '#B24-APS-219', coldChain: true, stockBox: 'ARMZ-C-L1-C03' },
      { name: 'Donaren®', subName: 'Trazodona HCl', qty: '600', batch: '#B24-APS-443', coldChain: false, stockBox: 'ARMZ-B-L3-C05' },
      { name: 'Atentah', subName: 'Atomoxetina HCl', qty: '450', batch: '#B24-APS-158', coldChain: false, stockBox: 'ARMZ-B-L4-C16' },
    ],
  },
  {
    id: 'OS-2024-003',
    client: 'Pague Menos Distribuição',
    destination: 'CD Fortaleza — CE',
    date: '13 Out, 2023',
    status: 'Expedido',
    priority: 'Normal',
    origin: 'Centro de Distribuição Valinhos — SP',
    totalValue: 44200.0,
    items: [
      { name: 'Donaren®', subName: 'Trazodona HCl', qty: '1.000', batch: '#B24-APS-444', coldChain: false, stockBox: 'ARMZ-B-L3-C06' },
      { name: 'Flancox®', subName: 'Etodolaco', qty: '800', batch: '#B24-APS-092', coldChain: false, stockBox: 'ARMZ-B-L3-C08' },
      { name: 'Miosan', subName: 'Ciclobenzaprina HCl', qty: '1.200', batch: '#B24-APS-332', coldChain: false, stockBox: 'ARMZ-B-L4-C12' },
    ],
  },
  {
    id: 'OS-2024-004',
    client: 'Drogaria São Paulo',
    destination: 'CD Osasco — SP',
    date: '14 Out, 2023',
    status: 'Em Separação',
    priority: 'Urgente',
    origin: 'Centro de Distribuição Valinhos — SP',
    totalValue: 29800.0,
    items: [
      { name: 'Flancox®', subName: 'Etodolaco', qty: '2.000', batch: '#B24-APS-093', coldChain: false, stockBox: 'ARMZ-B-L3-C09' },
      { name: 'Atentah', subName: 'Atomoxetina HCl', qty: '750', batch: '#B24-APS-159', coldChain: false, stockBox: 'ARMZ-B-L4-C17' },
    ],
  },
];

export const MOCK_LOGS: ActivityLog[] = [
  // OS-2024-001
  { id: 'l01', timestamp: '12/10/2023 08:15', orderId: 'OS-2024-001', userId: 'u1', userName: 'Dr. Ricardo Melo', userRole: 'admin', action: 'Criação', details: 'Ordem criada para Droga Raia S.A. — Hub Logístico Cajamar. Prioridade Alta.' },
  { id: 'l02', timestamp: '12/10/2023 09:22', orderId: 'OS-2024-001', userId: 'u2', userName: 'João Silva', userRole: 'operator', action: 'Separação iniciada', details: 'Início do processo de separação — Armazém B, Linha 3.', stockBox: 'ARMZ-B-L3' },
  { id: 'l03', timestamp: '12/10/2023 09:35', orderId: 'OS-2024-001', userId: 'u2', userName: 'João Silva', userRole: 'operator', action: 'Item coletado', details: 'Donaren® (trazodona) retirado do estoque.', stockBox: 'ARMZ-B-L3-C04', units: 850, itemName: 'Donaren®' },
  { id: 'l04', timestamp: '12/10/2023 09:48', orderId: 'OS-2024-001', userId: 'u2', userName: 'João Silva', userRole: 'operator', action: 'Item coletado', details: 'Flancox® (etodolaco) retirado do estoque.', stockBox: 'ARMZ-B-L3-C07', units: 1200, itemName: 'Flancox®' },
  { id: 'l05', timestamp: '12/10/2023 10:02', orderId: 'OS-2024-001', userId: 'u2', userName: 'João Silva', userRole: 'operator', action: 'Item coletado', details: 'Alois® (memantina) retirado — câmara fria ativada.', stockBox: 'ARMZ-C-L1-C02', units: 2400, itemName: 'Alois®', temperature: '2.4°C' },
  { id: 'l06', timestamp: '12/10/2023 10:15', orderId: 'OS-2024-001', userId: 'u2', userName: 'João Silva', userRole: 'operator', action: 'Item coletado', details: 'Miosan (ciclobenzaprina) retirado do estoque.', stockBox: 'ARMZ-B-L4-C11', units: 600, itemName: 'Miosan' },
  { id: 'l07', timestamp: '12/10/2023 10:28', orderId: 'OS-2024-001', userId: 'u2', userName: 'João Silva', userRole: 'operator', action: 'Item coletado', details: 'Atentah (atomoxetina) retirado do estoque.', stockBox: 'ARMZ-B-L4-C15', units: 900, itemName: 'Atentah' },
  { id: 'l08', timestamp: '12/10/2023 10:35', orderId: 'OS-2024-001', userId: 'u3', userName: 'Carlos Ferreira', userRole: 'operator', action: 'Verificação', details: 'QA concluído — 5 itens conferidos, lacre #APS-8801 aplicado. Temp. 2.4°C OK.' },
  { id: 'l09', timestamp: '12/10/2023 10:45', orderId: 'OS-2024-001', userId: 'u1', userName: 'Dr. Ricardo Melo', userRole: 'admin', action: 'Expedição', details: 'Carga aprovada para expedição. Veículo APS-7734 (Renault Master Frigorífico) designado.' },
  { id: 'l10', timestamp: '12/10/2023 11:00', orderId: 'OS-2024-001', userId: 'u2', userName: 'João Silva', userRole: 'operator', action: 'Em trânsito', details: 'Veículo APS-7734 saiu do CD Valinhos. Temperatura monitorada: 2.3°C.', temperature: '2.3°C' },

  // OS-2024-002
  { id: 'l11', timestamp: '13/10/2023 07:30', orderId: 'OS-2024-002', userId: 'u1', userName: 'Dr. Ricardo Melo', userRole: 'admin', action: 'Criação', details: 'Ordem criada para Hospital Albert Einstein — Unidade Morumbi.' },
  { id: 'l12', timestamp: '13/10/2023 08:45', orderId: 'OS-2024-002', userId: 'u3', userName: 'Carlos Ferreira', userRole: 'operator', action: 'Separação iniciada', details: 'Aguardando liberação de estoque — Armazém A, Linha 2.', stockBox: 'ARMZ-A-L2' },

  // OS-2024-003
  { id: 'l13', timestamp: '13/10/2023 06:00', orderId: 'OS-2024-003', userId: 'u1', userName: 'Dr. Ricardo Melo', userRole: 'admin', action: 'Criação', details: 'Ordem criada para Pague Menos Distribuição — CD Fortaleza.' },
  { id: 'l14', timestamp: '13/10/2023 09:15', orderId: 'OS-2024-003', userId: 'u2', userName: 'João Silva', userRole: 'operator', action: 'Separação iniciada', details: 'Processo iniciado — Armazém A, Linha 1.', stockBox: 'ARMZ-A-L1' },
  { id: 'l15', timestamp: '13/10/2023 10:10', orderId: 'OS-2024-003', userId: 'u2', userName: 'João Silva', userRole: 'operator', action: 'Item coletado', details: 'Donaren® coletado.', stockBox: 'ARMZ-B-L3-C06', units: 1000, itemName: 'Donaren®' },
  { id: 'l16', timestamp: '13/10/2023 10:22', orderId: 'OS-2024-003', userId: 'u2', userName: 'João Silva', userRole: 'operator', action: 'Item coletado', details: 'Flancox® coletado.', stockBox: 'ARMZ-B-L3-C08', units: 800, itemName: 'Flancox®' },
  { id: 'l17', timestamp: '13/10/2023 10:40', orderId: 'OS-2024-003', userId: 'u2', userName: 'João Silva', userRole: 'operator', action: 'Item coletado', details: 'Miosan coletado.', stockBox: 'ARMZ-B-L4-C12', units: 1200, itemName: 'Miosan' },
  { id: 'l18', timestamp: '13/10/2023 11:00', orderId: 'OS-2024-003', userId: 'u1', userName: 'Dr. Ricardo Melo', userRole: 'admin', action: 'Expedição', details: 'Carga aprovada. Transportadora Jamef — destino CD Fortaleza.' },

  // OS-2024-004
  { id: 'l19', timestamp: '14/10/2023 07:00', orderId: 'OS-2024-004', userId: 'u1', userName: 'Dr. Ricardo Melo', userRole: 'admin', action: 'Criação', details: 'Ordem URGENTE criada para Drogaria São Paulo — CD Osasco.' },
  { id: 'l20', timestamp: '14/10/2023 07:30', orderId: 'OS-2024-004', userId: 'u3', userName: 'Carlos Ferreira', userRole: 'operator', action: 'Separação iniciada', details: 'Processo urgente iniciado — Armazém B, Linha 2.', stockBox: 'ARMZ-B-L2' },
  { id: 'l21', timestamp: '14/10/2023 07:55', orderId: 'OS-2024-004', userId: 'u3', userName: 'Carlos Ferreira', userRole: 'operator', action: 'Item coletado', details: 'Flancox® coletado — produção recente, lote prioritário.', stockBox: 'ARMZ-B-L3-C09', units: 2000, itemName: 'Flancox®' },
];

export const MOCK_ROUTES: Record<string, RouteStop[]> = {
  'OS-2024-001': [
    { name: 'CD Valinhos — Valinhos, SP', type: 'origem', time: '11:00', status: 'concluído' },
    { name: 'Checkpoint Rodovia Anhanguera km 87', type: 'parada', time: '12:30', status: 'concluído' },
    { name: 'Hub Logístico Cajamar, SP', type: 'destino', time: '14:35', status: 'em andamento' },
  ],
  'OS-2024-002': [
    { name: 'CD Valinhos — Valinhos, SP', type: 'origem', time: '—', status: 'pendente' },
    { name: 'Unidade Morumbi — São Paulo, SP', type: 'destino', time: '—', status: 'pendente' },
  ],
  'OS-2024-003': [
    { name: 'CD Valinhos — Valinhos, SP', type: 'origem', time: '11:30', status: 'concluído' },
    { name: 'Terminal de Cargas Guarulhos', type: 'parada', time: '13:00', status: 'concluído' },
    { name: 'CD Fortaleza — CE', type: 'destino', time: '(próximo dia)', status: 'pendente' },
  ],
  'OS-2024-004': [
    { name: 'CD Valinhos — Valinhos, SP', type: 'origem', time: '—', status: 'pendente' },
    { name: 'CD Osasco — SP', type: 'destino', time: '—', status: 'pendente' },
  ],
};
