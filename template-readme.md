# Apsen Vision System

**Apsen Vision System** é um sistema de contagem industrial de caixas de medicamentos por visão computacional, construído com YOLOv11 e a SDK nativa da Ultralytics.  
Detecta e conta objetos em tempo real dentro de uma ROI (Região de Interesse) configurável, com logging automático por sessão e suporte a modelo próprio treinado via Roboflow.

[![Python](https://img.shields.io/badge/Python-3.10%2B-blue?logo=python)](https://python.org)
[![Ultralytics](https://img.shields.io/badge/YOLOv11-Ultralytics-orange)](https://docs.ultralytics.com)
[![Roboflow](https://img.shields.io/badge/Dataset-Roboflow-purple)](https://roboflow.com)

## Por que Apsen Vision System?

- **Modelo próprio** — treinado em imagens reais das caixas com dataset "Find Small Boxes" via Roboflow
- **ROI configurável** — define a zona de contagem via 4 pontos; objetos fora da área são ignorados
- **Contagem bidirecional** — rastreia entrada (`in_count`) e saída (`out_count`) separadamente
- **Logging por dia** — arquivo `.log` gerado automaticamente com timestamp de cada sessão
- **Zero dependência de nuvem em produção** — inferência 100% local após download do modelo
- **Pipeline de treino incluso** — scripts prontos para re-treinar com novos dados

---

## Features

### Detecção & Contagem

- Inferência com **YOLOv11n** — modelo nano, rápido em CPU
- `solutions.ObjectCounter` nativo da Ultralytics com ByteTrack integrado (BoT-SORT)
- Contagem por cruzamento de ROI poligonal (não por frame) — elimina contagens duplicadas
- Confiança mínima configurável (padrão: `0.6`) para suprimir detecções fantasmas
- Suporte ao modelo OBB (`yolo11n-obb.pt`) para detecção com caixas orientadas

### Dataset & Treinamento

- Dataset "Find Small Boxes" — anotado no Roboflow, exportado em formato YOLOv11
- 2 classes: `Medicamento` (classe 0) e variante de orientação (classe 1)
- 17 imagens de treino com labels YOLO normalizados
- 2 experimentos completos de treinamento: `detector_caixas_real` e `detector_caixas_real-2`
- 100 épocas, `imgsz=640`, otimizador automático, AMP ativado
- Scripts separados para treino via Roboflow (`treinar.py`) e local (`treinar_local.py`)

### Coleta de Dados

- `coletar_dados.py` — captura frames da câmera com tecla `S` e salva com UUID único
- Imagens salvas diretamente em `dataset/train/images/` no formato correto para re-treino
- Ideal para ampliar o dataset com novas variações de iluminação ou posicionamento

### Logging

- Log diário em `logs/contagem_YYYYMMDD.log`
- Registra: início do modelo, erros de câmera, contagem final da sessão
- Output simultâneo no terminal e no arquivo

---

## Quick Start

### 1. Instalar dependências

```bash
pip install -r requirements.txt
```

`requirements.txt` atual:

```
ultralytics
opencv-python
numpy
torch
torchvision
```

### 2. Rodar com modelo OBB (raiz do projeto)

```bash
cd mynd_vision_system
python main.py
```

Usa `yolo11n-obb.pt` por padrão. Pressione `Q` para encerrar.

### 3. Rodar com modelo treinado (subpasta)

```bash
cd mynd_vision_system/mynd_vision_system
python main.py
```

Usa `models/best.pt` — o modelo treinado nas caixas reais.

---

## Calibração da ROI

A Região de Interesse define a zona onde os objetos são contados. Edite `region_points` no `main.py`:

```python
# Formato: [(x1, y1), (x2, y2), (x3, y3), (x4, y4)]
# Coordenadas em pixels para resolução 1280x720
self.region_points = [(100, 600), (1180, 600), (1180, 100), (100, 100)]
```

Para alinhar com sua bancada/esteira:
1. Rode o sistema e observe o frame exibido
2. Identifique as coordenadas dos cantos da área de contagem
3. Atualize os 4 pontos e reinicie

**Dica:** Use `cv2.imshow` com `cv2.setMouseCallback` para clicar e capturar coordenadas precisas.

---

## Treinamento

### Opção 1 — Dataset local (recomendado)

Configure o caminho do dataset no `dataset_local.yaml`:

```yaml
path: C:/Users/SEU_USUARIO/Downloads/Find small boxes.v2-...yolov11
train: train/images
val: valid/images
test: test/images

names:
  0: Medicamento
```

Execute:

```bash
python treinar_local.py
```

O modelo treinado ficará em:

```
mynd_finance_v1/detector_caixas_real/weights/best.pt
```

Copie para `models/best.pt` antes de rodar o sistema.

### Opção 2 — Download via Roboflow API

```bash
python download_model.py
```

Requer API Key do Roboflow configurada no script:

```python
rf = Roboflow(api_key="SUA_API_KEY_AQUI")
project = rf.workspace("mynds-workspace").project("find-small-boxes-pyutr")
```

### Parâmetros de treinamento usados

| Parâmetro | Valor |
|---|---|
| Base model | `yolo11n.pt` |
| Epochs | 100 |
| Image size | 640 |
| Batch | 16 |
| Device | CPU (mude para `0` com GPU NVIDIA) |
| Tracker | BoT-SORT (`botsort.yaml`) |
| Optimizer | Auto |
| IoU threshold | 0.7 |
| AMP | Ativado |

### Resultados de treinamento

Artefatos gerados automaticamente em `runs/detect/mynd_finance_v1/`:

| Arquivo | Descrição |
|---|---|
| `weights/best.pt` | Melhor checkpoint do treino |
| `weights/last.pt` | Último checkpoint |
| `results.csv` | Métricas por época |
| `results.png` | Gráficos de loss, precisão e recall |
| `confusion_matrix.png` | Matriz de confusão |
| `BoxF1_curve.png` | Curva F1 por confiança |
| `BoxPR_curve.png` | Curva precisão-recall |
| `val_batch0_pred.jpg` | Predições no batch de validação |

---

## Coleta de Novos Dados

Para expandir o dataset com novas imagens:

```bash
python coletar_dados.py
```

| Tecla | Ação |
|---|---|
| `S` | Salva o frame atual com nome UUID |
| `Q` | Encerra a coleta |

Imagens são salvas em `dataset/train/images/`. Depois anote no Roboflow e re-treine.

---

## Configuração do Projeto Externo (Utilitário)

Para reconfigurar o projeto apontando para um novo dataset baixado:

```bash
python project-update.py
```

O script detecta automaticamente o caminho `~/Downloads/Find small boxes.v2-...` e regera o `dataset_local.yaml` e o `treinar_local.py`.

---

## Estrutura do Projeto

```
mynd_vision_system/
├── main.py                        # Ponto de entrada com MyndVision (modelo OBB)
├── requirements.txt
├── yolo11n-obb.pt                 # Modelo base OBB pré-treinado
├── logs/
│   └── contagem_YYYYMMDD.log      # Log diário da sessão
│
├── mynd_vision_system/            # Módulo principal com modelo customizado
│   ├── main.py                    # MyndIndustrialEngine (modelo best.pt)
│   ├── treinar.py                 # Treino via Roboflow (50 épocas)
│   ├── treinar_local.py           # Treino com dataset local (100 épocas)
│   ├── coletar_dados.py           # Captura de imagens para dataset
│   ├── download_model.py          # Download do dataset via API Roboflow
│   ├── dataset_local.yaml         # Config do dataset para treino local
│   ├── yolo11n.pt                 # Modelo base para fine-tuning
│   │
│   ├── models/
│   │   └── best.pt                # Modelo treinado (5.2 MB)
│   │
│   ├── dataset/
│   │   ├── data.yaml              # Config do dataset interno
│   │   └── train/
│   │       ├── images/            # 17 imagens de treino
│   │       └── labels/            # Labels YOLO normalizados
│   │
│   └── runs/detect/mynd_finance_v1/
│       ├── detector_caixas_real/  # Experimento 1
│       └── detector_caixas_real-2/ # Experimento 2 (melhor)
│           └── weights/
│               ├── best.pt
│               └── last.pt
│
├── scan-project.py                # Varre o projeto e gera relatorio.txt
└── setup_project.py               # Gera a estrutura inicial do projeto
```

---

## Classes do Modelo

| ID | Nome | Descrição |
|---|---|---|
| `0` | `Medicamento` | Caixa de medicamento em posição padrão |
| `1` | *(variante)* | Caixa em orientação alternativa (dataset WhatsApp) |

---

## Contexto — Projeto APSEN

Este módulo faz parte do **Projeto APSEN**: célula automatizada com CNC cartesiana para separação de amostras farmacêuticas com zero divergência.

O Mynd Vision System atua como a **terceira camada de validação** na redundância tripla:

```
CNC (ciclos executados)  ==  Balança (peso / unitário)  ==  Visão (in_count)
```

Divergência entre as três fontes bloqueia a operação até intervenção do operador.

---

## Riscos Conhecidos

| Risco | Mitigação |
|---|---|
| Dataset pequeno (17 imagens) | Coletar mais amostras com `coletar_dados.py` e re-treinar |
| Treino apenas em CPU | Mude `device="cpu"` para `device=0` se tiver GPU NVIDIA |
| ROI em pixels fixos | Recalibrar `region_points` ao mudar câmera ou resolução |
| `in_count` vs `in_counts` — API instável entre versões | Código usa `getattr` com fallback para compatibilidade |
| Confiança 0.4 no `main.py` raiz pode gerar falsos positivos | Use 0.6 do `MyndIndustrialEngine` em produção |

---

## Stats

### Dataset de treinamento

| Item | Quantidade |
|---|---|
| Imagens de treino | 17 |
| Labels anotados | ~30 instâncias (múltiplas por imagem) |
| Classes | 2 (`Medicamento`, variante) |
| Formato | YOLO normalizado (`.txt` por imagem) |
| Fonte | Roboflow + fotos via WhatsApp (17/04/2026) |

### Codebase

| Arquivo | Linhas | Responsabilidade |
|---|---|---|
| `main.py` (raiz) | 90 | MyndVision com modelo OBB |
| `mynd_vision_system/main.py` | 69 | MyndIndustrialEngine com best.pt |
| `treinar_local.py` | 29 | Pipeline de treino local |
| `treinar.py` | 21 | Pipeline de treino via Roboflow |
| `coletar_dados.py` | 26 | Captura de frames para dataset |
| `download_model.py` | 20 | Download do dataset via API |
| `project-update.py` | 71 | Reconfiguração para dataset externo |
| `setup_project.py` | 127 | Geração da estrutura do projeto |
| `scan-project.py` | 218 | Scanner de projeto → relatorio.txt |
| **Total** | **~671** | |