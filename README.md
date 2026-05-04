# Apsen App

**Apsen App** é um sistema de gestão logística de alta precisão para a Apsen Farmacêuticos, construído com React 19, TypeScript e Tailwind CSS 4.  
Gerencia ordens de serviço farmacêuticas com autenticação por perfil, rastreamento de cadeia fria, scanner QR, geração de PDF e auditoria completa de histórico.

[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react)](https://react.dev)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.8-3178C6?logo=typescript)](https://typescriptlang.org)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind-v4-06B6D4?logo=tailwindcss)](https://tailwindcss.com)
[![Vite](https://img.shields.io/badge/Vite-6-646CFF?logo=vite)](https://vitejs.dev)
[![Gemini](https://img.shields.io/badge/Gemini_API-Google_AI-4285F4?logo=google)](https://ai.google.dev)

## Por que Apsen App?

- **Login com controle de acesso** — perfis Admin e Operador com visibilidade diferenciada de dados financeiros e KPIs
- **Formulário de ordem funcional** — criação real de ordens com manifesto dinâmico, validação, auto-geração de lotes/caixas e redirecionamento imediato para detalhes
- **Invoice PDF real** — botão de download em Detalhes gera `.pdf` com manifesto, rota, requisitos especiais e campos de assinatura via jsPDF
- **Histórico de auditoria** — rastreabilidade completa por ordem: quem separou, de qual caixa, quantas unidades, timestamps e rota
- **Rastreamento de cadeia fria** — monitoramento de temperatura em tempo real para medicamentos que exigem controle térmico
- **Scanner QR integrado** — leitura de códigos com câmera nativa, sem dependência de SDK externo

---

## Credenciais de Acesso

| Perfil | Usuário | Senha | Visibilidade extra |
|---|---|---|---|
| **Admin** | `admin` | `apsen@admin` | Valor das ordens, taxa de serviço, KPIs de eficiência, totais do arquivo |
| **Operador** | `joao.silva` | `apsen@op` | Dados operacionais padrão |
| **Operador** | `carlos.ferreira` | `apsen@op` | Dados operacionais padrão |

---

## Features

### Autenticação

- Tela de login com validação de credenciais
- Contexto global `AuthContext` com `user`, `isAdmin`, `login()` e `logout()`
- Botão de logout e badge de perfil no cabeçalho
- Dados financeiros e KPIs visíveis somente para Admins

### Expedições

- Listagem reativa de ordens — novas ordens criadas aparecem em tempo real via `OrdersContext`
- Badges de status com código de cor: `Em Separação`, `Aguardando`, `Expedido`, `Em Trânsito`, `Concluído`
- Indicadores de prioridade: `Urgente`, `Alta`, `Normal`, `Baixa`
- Busca por ID, cliente ou medicamento
- Criação de nova ordem direto do dashboard

### Criação de Ordem (Nova Ordem)

- **Campos completos:** cliente, CD de destino, prioridade, data de entrega, origem (pré-preenchida) e observações
- **Manifesto dinâmico:** adicione/remova medicamentos com dropdown dos 5 fármacos Apsen
  - Nº lote e caixa de estoque gerados automaticamente ao selecionar o fármaco
  - Todos os campos são editáveis manualmente
  - Cadeia fria determinada automaticamente pelo fármaco
- **Validação inline** com checklist de pendências ao vivo na sidebar
- **Sidebar reativa:** exibe cliente, contagem de itens, total de unidades, rota e valor estimado (Admin)
- **"Finalizar Ordem"** → status `Em Separação`, registra log de criação, redireciona para Detalhes
- **"Salvar Rascunho"** → status `Aguardando`, mesma lógica

### Detalhes da Ordem

- Manifesto completo com lote, quantidade, cadeia fria e caixa de estoque por item
- Métricas críticas: temperatura atual e ETA (valor total visível somente para Admins)
- Rota de envio: origem → destino com visualização gráfica
- **Download de Invoice PDF** — gera `Pedido_Separacao_<ID>.pdf` real com jsPDF, incluindo cabeçalho Apsen, tabela do manifesto, rota, requisitos especiais e campos de assinatura

### Scanner QR

- Interface de câmera fullscreen com visor animado
- Linha de varredura em loop com indicação visual
- Controles de lanterna e upload de imagem da galeria
- Fallback para inserção manual de código

### Arquivo

- Histórico de ordens concluídas com busca
- Totais mensais (número de remessas visível somente para Admins)
- Cards de ordens com status `Concluído`

### Relatório de Histórico

- Seleção de ordem por abas
- **Linha do tempo completa:** cada evento com timestamp, nome do operador, perfil (Admin/Operador), ação, detalhes, caixa de estoque e temperatura
- Filtros por perfil (Admin/Operador) e busca por texto
- Rota de envio com paradas e status por checkpoint
- Resumo de itens separados com caixas de estoque
- **KPIs exclusivos para Admin:** tempo de separação, eficiência, divergências, custo por unidade e valor total

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

Acesse `http://localhost:3000` e faça login com as credenciais acima.

---

## Scripts Disponíveis

| Comando | Descrição |
|---|---|
| `npm run dev` | Servidor de desenvolvimento na porta 3000 |
| `npm run build` | Build de produção em `dist/` |
| `npm run preview` | Preview do build de produção |
| `npm run lint` | Verificação de tipos TypeScript |
| `npm run clean` | Remove a pasta `dist/` |

---

## Estrutura do Projeto

```
src/
├── contexts/
│   ├── AuthContext.tsx       # Autenticação, perfis, login/logout
│   └── OrdersContext.tsx     # Estado global reativo de ordens e logs
│
├── lib/
│   ├── generateInvoice.ts   # Geração de PDF com jsPDF + jspdf-autotable
│   ├── mockData.ts          # Dados iniciais: ordens, logs e rotas
│   └── utils.ts             # cn() — merge de classes Tailwind
│
├── views/
│   ├── Login.tsx            # Tela de autenticação
│   ├── Expeditions.tsx      # Dashboard de ordens ativas
│   ├── Details.tsx          # Manifesto, métricas e download de invoice
│   ├── Scanning.tsx         # Interface do scanner QR
│   ├── NewOrder.tsx         # Formulário funcional de criação de ordem
│   ├── Archive.tsx          # Histórico de ordens concluídas
│   └── HistoryReport.tsx    # Auditoria completa com linha do tempo
│
└── components/
    ├── TopAppBar.tsx        # Cabeçalho com usuário, perfil e logout
    └── BottomNavBar.tsx     # Navegação inferior com 4 abas
```

---

## Medicamentos Cadastrados

| Medicamento | Substância Ativa | Cadeia Fria | Armazém |
|---|---|---|---|
| Donaren® | Trazodona HCl | Não | ARMZ-B |
| Flancox® | Etodolaco | Não | ARMZ-B |
| Alois® | Memantina HCl | **Sim (2–8°C)** | ARMZ-C |
| Miosan | Ciclobenzaprina HCl | Não | ARMZ-B |
| Atentah | Atomoxetina HCl | Não | ARMZ-B |

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
| PDF | jsPDF + jspdf-autotable | 2.x |
| IA | Google Generative AI SDK | 1.29 |
| Classnames | clsx + tailwind-merge | — |

---

## Design System

### Paleta de Cores (Tema "Clinical Architect")

| Token | Valor | Uso |
|---|---|---|
| `--color-primary` | `#00328b` | Azul navy — ações primárias |
| `--color-primary-container` | `#0047bc` | Azul brilhante — containers ativos |
| `--color-tertiary` | `#6d1f00` | Ferrugem — alertas e urgência |
| `--color-error` | `#ba1a1a` | Vermelho — erros e críticos |

### Tipografia

| Família | Pesos | Uso |
|---|---|---|
| Inter | 400, 500, 600 | Corpo, rótulos, dados |
| Manrope | 400–800 | Títulos e headings |

---

## Contexto — Projeto APSEN

Este portal é a interface web do **Projeto APSEN**: ecossistema integrado de automação logística farmacêutica com rastreabilidade ponta a ponta.

```
Portal (ordens / manifesto)  →  Separação física  →  Expedição rastreada
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
| Persistência de dados | Em memória (React state) — recarregar perde as ordens criadas |
| Scanner QR | Interface implementada — lógica de câmera pendente |
| Autenticação | Mock local — sem JWT ou sessão real |
| Integração Gemini | SDK configurado — endpoints não conectados às views |
