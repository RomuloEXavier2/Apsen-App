export type View = 'expeditions' | 'details' | 'scanning' | 'new-order' | 'archive';

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
}

export interface ManifestItem {
  name: string;
  subName: string;
  qty: string;
  batch: string;
  coldChain: boolean;
}
