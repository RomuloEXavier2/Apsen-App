# Apsen Logistics Portal

**Apsen Logistics Portal** é um sistema de gestão logística de alta precisão para a Apsen Farmacêuticos, construído com React 19, TypeScript e Tailwind CSS 4.  
Gerencia ordens de serviço farmacêuticas com rastreamento de cadeia fria, leitura de QR code e monitoramento de expedição em tempo real.

[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react)](https://react.dev)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.8-3178C6?logo=typescript)](https://typescriptlang.org)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind-v4-06B6D4?logo=tailwindcss)](https://tailwindcss.com)
[![Vite](https://img.shields.io/badge/Vite-6-646CFF?logo=vite)](https://vitejs.dev)
[![Gemini](https://img.shields.io/badge/Gemini_API-Google_AI-4285F4?logo=google)](https://ai.google.dev)

## Por que Apsen Logistics Portal?

- **Rastreamento de cadeia fria** — monitoramento de temperatura em tempo real para medicamentos que exigem controle térmico
- **Scanner QR integrado** — leitura de códigos com câmera nativa, sem dependência de SDK externo
- **Manifesto detalhado** — por item: nome, sub-nome, quantidade, lote e flag de cold chain
- **Gestão de ordens de serviço** — criação, acompanhamento, expedição e arquivamento em um único portal
- **Design farmacêutico** — tema "Clinical Architect" com sistema de cores Material Design 3 adaptado
- **Integração Google Gemini** — base para automação inteligente via API de IA generativa

---

## Features

### Expedições

- Listagem de todas as ordens de serviço ativas com status em tempo real
- Badges de status com código de cor: `Em Separação`, `Aguardando`, `Expedido`, `Em Trânsito`, `Concluído`
- Indicadores de prioridade: `Urgente`, `Alta`, `Normal`, `Baixa`
- Barra de busca com atalho para scanner QR
- Filtros por status e configurações da visualização
- Criação de nova ordem diretamente do dashboard

### Detalhes da Ordem

- Visão completa do manifesto com todos os itens, lotes e quantidades
- Indicador visual de cold chain por item (ícone de floco de neve)
- Métricas críticas: temperatura atual e ETA estimado
- Rota de envio: origem → destino com visualização gráfica
- Seção de documentação com suporte a download de PDF
- Botão flutuante "Atualizar Status" para transições rápidas

### Scanner QR

- Interface de câmera fullscreen com visor de scanning animado
- Linha de varredura em loop contínuo (3 s) com indicação visual
- Controles de lanterna e upload de imagem da galeria
- Fallback para inserção manual de código
- Indicador de status "Scanner Ativo"

### Nova Ordem de Serviço

- Formulário estruturado: cliente, CD de destino, prioridade e data de entrega
- Seção de manifesto com adição dinâmica de itens
- Sidebar de resumo com rota estimada, protocolo de manuseio e estimativa de custo
- Barra de progresso de verificação de estabilidade térmica
- Salvar como rascunho ou finalizar ordem

### Arquivo

- Histórico de ordens concluídas com busca por ordem ou cliente
- Card de destaque para o item arquivado mais recente
- Contador total de remessas arquivadas
- Grade de cards com todas as ordens marcadas como `COMPLETED`

---

## Quick Start

### Pré-requisitos

- Node.js 18+
- Chave de API do Google Gemini ([obter aqui](https://aistudio.google.com/app/apikey))

### 1. Instalar dependências

```bash
npm install
```

### 2. Configurar variáveis de ambiente

Copie o arquivo de exemplo e preencha com suas credenciais:

```bash
cp .env.example .env.local
```

Edite `.env.local`:

```env
GEMINI_API_KEY="sua_chave_gemini_aqui"
APP_URL="http://localhost:3000"
```

### 3. Iniciar o servidor de desenvolvimento

```bash
npm run dev
```

O app estará disponível em `http://localhost:3000`.

---

## Scripts Disponíveis

| Comando | Descrição |
|---|---|
| `npm run dev` | Servidor de desenvolvimento na porta 3000 |
| `npm run build` | Build de produção otimizado em `dist/` |
| `npm run preview` | Preview do build de produção |
| `npm run lint` | Verificação de tipos TypeScript sem emissão |
| `npm run clean` | Remove a pasta `dist/` |

---

## Estrutura do Projeto

```
Apsen-App-main/
├── index.html                   # Entry point HTML
├── metadata.json                # Configuração para Google AI Studio
├── vite.config.ts               # Configuração do bundler Vite
├── tsconfig.json                # Opções do compilador TypeScript
├── .env.example                 # Template de variáveis de ambiente
│
└── src/
    ├── main.tsx                 # Bootstrap do React 19 em StrictMode
    ├── App.tsx                  # Roteamento e estado global por view
    ├── types.ts                 # Definições de tipos TypeScript
    ├── index.css                # Tema global, variáveis CSS e Tailwind
    │
    ├── lib/
    │   └── utils.ts             # Utilitário cn() para merge de classes
    │
    ├── components/
    │   ├── TopAppBar.tsx        # Header com título, subtítulo e avatar
    │   └── BottomNavBar.tsx     # Navegação inferior com 3 abas
    │
    └── views/
        ├── Expeditions.tsx      # Dashboard de ordens ativas
        ├── Details.tsx          # Manifesto e rastreamento da ordem
        ├── Scanning.tsx         # Interface do scanner QR
        ├── NewOrder.tsx         # Formulário de criação de ordem
        └── Archive.tsx          # Histórico de ordens concluídas
```

---

## Tipos Principais

```typescript
type View = 'expeditions' | 'details' | 'scanning' | 'new-order' | 'archive'

interface ServiceOrder {
  id: string
  client: string
  destination: string
  date: string
  status: 'Em Separação' | 'Aguardando' | 'Expedido' | 'Em Trânsito' | 'Concluído'
  priority: 'Alta' | 'Normal' | 'Urgente' | 'Baixa'
  items?: ManifestItem[]
  temperature?: string
  eta?: string
}

interface ManifestItem {
  name: string
  subName: string
  qty: string
  batch: string
  coldChain: boolean
}
```

---

## Design System

### Paleta de Cores (Tema "Clinical Architect")

| Token | Valor | Uso |
|---|---|---|
| `--color-primary` | `#00328b` | Azul navy — ações primárias |
| `--color-primary-container` | `#0047bc` | Azul brilhante — containers ativos |
| `--color-tertiary` | `#6d1f00` | Ferrugem — alertas e urgência |
| `--color-error` | `#ba1a1a` | Vermelho — erros e críticos |
| `--color-surface` | `#f8f9fc` | Fundo principal |

### Tipografia

| Família | Pesos | Uso |
|---|---|---|
| Inter | 400, 500, 600 | Corpo, rótulos, dados |
| Manrope | 400–800 | Títulos e headings |

### Classes Utilitárias

| Classe | Efeito |
|---|---|
| `.glass` | `backdrop-blur` com fundo semi-transparente |
| `.tonal-shadow` | Sombra azul suave `rgba(0, 50, 139, 0.06)` |
| `.no-scrollbar` | Esconde scrollbar preservando funcionalidade |

---

## Stack Técnica

| Camada | Tecnologia | Versão |
|---|---|---|
| Framework | React | 19 |
| Linguagem | TypeScript | ~5.8 |
| Bundler | Vite | 6 |
| Estilos | Tailwind CSS | 4 |
| Animações | Framer Motion (motion) | 12 |
| Ícones | Lucide React | 0.546 |
| IA | Google Generative AI SDK | 1.29 |
| Classnames | clsx + tailwind-merge | — |

---

## Contexto — Projeto APSEN

Este portal é a interface web do **Projeto APSEN**: ecossistema integrado de automação logística farmacêutica com zero divergência entre expedição física e registros digitais.

O Apsen Logistics Portal atua como a **camada de orquestração de ordens**:

```
Portal (ordens / manifesto)  →  Separação física (CNC / robótica)  →  Expedição rastreada
```

A integração com o Google Gemini abre caminho para automação de preenchimento de manifesto, detecção de anomalias em cold chain e geração de documentação de conformidade.

---

## Permissões Requeridas

| Permissão | Motivo |
|---|---|
| `camera` | Scanner QR para leitura de códigos de caixas e etiquetas |

---

## Limitações Atuais

| Item | Status |
|---|---|
| Dados das ordens | Mock estático — sem backend conectado |
| Scanner QR | Interface implementada — lógica de câmera pendente |
| Formulário de nova ordem | Layout completo — submissão sem persistência |
| Autenticação | Não implementada |
| Integração Gemini | SDK configurado — endpoints não conectados às views |
