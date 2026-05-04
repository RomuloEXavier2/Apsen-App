export type View = 'expeditions' | 'details' | 'scanning' | 'new-order' | 'archive' | 'history';

export type UserRole = 'admin' | 'operator';

export interface User {
  id: string;
  name: string;
  username: string;
  password: string;
  role: UserRole;
}

export interface ServiceOrder {
  id: string;
  client: string;
  destination: string;
  date: string;
  status: 'Em Separação' | 'Aguardando' | 'Expedido' | 'Em Trânsito' | 'Concluído';
  priority: 'Alta' | 'Normal' | 'Urgente' | 'Baixa';
  items?: ManifestItem[];
  temperature?: string;
  eta?: string;
  origin?: string;
  totalValue?: number;
}

export interface ManifestItem {
  name: string;
  subName: string;
  qty: string;
  batch: string;
  coldChain: boolean;
  stockBox?: string;
}

export type ActionType =
  | 'Criação'
  | 'Separação iniciada'
  | 'Item coletado'
  | 'Verificação'
  | 'Expedição'
  | 'Em trânsito'
  | 'Entrega confirmada'
  | 'Arquivado';

export interface ActivityLog {
  id: string;
  timestamp: string;
  orderId: string;
  userId: string;
  userName: string;
  userRole: UserRole;
  action: ActionType;
  details: string;
  stockBox?: string;
  units?: number;
  itemName?: string;
  temperature?: string;
}

export interface RouteStop {
  name: string;
  type: 'origem' | 'parada' | 'destino';
  time: string;
  status: 'concluído' | 'em andamento' | 'pendente';
}
