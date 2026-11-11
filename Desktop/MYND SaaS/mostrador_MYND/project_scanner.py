#!/usr/bin/env python3
"""
scan_projeto.py - Escaneia um projeto e gera um relatorio de contexto (Markdown).

Uso:
    Coloque este arquivo na raiz do projeto e execute:
        python scan_projeto.py
    Ele analisa a pasta onde o script esta e todas as subpastas.
    (Opcional) para analisar outra pasta: python scan_projeto.py CAMINHO

Extrai: linguagens, frameworks, dependencias, estrutura de pastas, telas/paginas,
permissoes e servicos Android, metodos de requisicao (HTTP), URLs, uso de BLE,
UUIDs, e features por palavras-chave.

Nao envia nada para a internet. Revise o relatorio antes de compartilhar.
"""
import argparse
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

IGNORE_DIRS = {
    ".git", ".idea", ".vscode", "node_modules", "build", "dist", ".gradle",
    "Pods", "venv", ".venv", "__pycache__", ".dart_tool", ".next", "target",
    "DerivedData", ".expo", "out", "coverage",
}
MAX_FILE_BYTES = 1_500_000

LANGS = {
    ".kt": "Kotlin", ".kts": "Kotlin Script", ".java": "Java", ".dart": "Dart",
    ".swift": "Swift", ".m": "Objective-C", ".js": "JavaScript", ".jsx": "JavaScript (JSX)",
    ".ts": "TypeScript", ".tsx": "TypeScript (TSX)", ".py": "Python", ".cs": "C#",
    ".cpp": "C++", ".c": "C", ".h": "C/C++ header", ".ino": "Arduino", ".go": "Go",
    ".rs": "Rust", ".php": "PHP", ".html": "HTML", ".css": "CSS", ".xml": "XML",
    ".json": "JSON", ".yaml": "YAML", ".yml": "YAML", ".gradle": "Gradle",
}
CODE_EXTS = set(LANGS) - {".json", ".xml", ".yaml", ".yml"}
TEXT_EXTS = set(LANGS) | {".md", ".txt", ".properties", ".toml", ".plist"}

MANIFESTS = {
    "AndroidManifest.xml": "Android nativo",
    "build.gradle": "Gradle (Android/JVM)",
    "build.gradle.kts": "Gradle KTS (Android/JVM)",
    "pubspec.yaml": "Flutter/Dart",
    "package.json": "Node/React Native/Web",
    "Podfile": "iOS CocoaPods",
    "Package.swift": "Swift Package",
    "requirements.txt": "Python",
    "pyproject.toml": "Python",
    "Info.plist": "iOS",
    "app.json": "Expo/React Native",
}

HTTP_PATTERNS = [
    ("Retrofit", re.compile(r"@(GET|POST|PUT|PATCH|DELETE|HEAD)\(\s*\"([^\"]*)\"")),
    ("fetch/axios/http", re.compile(
        r"\b(?:axios|http|dio|client|api)\.(get|post|put|patch|delete)\s*\(\s*[`'\"]([^`'\"]+)", re.I)),
    ("fetch()", re.compile(r"fetch\(\s*[`'\"]([^`'\"]+)[`'\"](?:\s*,\s*\{[^}]*method\s*:\s*[`'\"](\w+))?", re.S)),
    ("requests (py)", re.compile(r"\brequests\.(get|post|put|patch|delete)\(\s*[f]?[\"']([^\"']+)")),
    ("OkHttp/Volley/URLConnection", re.compile(
        r"(HttpURLConnection|OkHttpClient|Request\.Builder|StringRequest|URLSession|HttpClient)")),
    ("requestMethod", re.compile(r"requestMethod\s*=\s*\"(\w+)\"")),
]
URL_RE = re.compile(r"https?://[^\s\"'<>)\]}\\]+")
SECRET_HINT = re.compile(r"(key|token|secret|passw|auth|apikey)", re.I)
UUID_RE = re.compile(r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b")

BLE_KEYWORDS = {
    "BluetoothGatt (Android nativo)": r"BluetoothGatt\b|BluetoothGattCallback",
    "BluetoothLeScanner (scan BLE)": r"BluetoothLeScanner|startScan\(",
    "flutter_blue_plus / flutter_reactive_ble": r"FlutterBluePlus|flutter_blue_plus|flutter_reactive_ble|FlutterBlue\b",
    "react-native-ble-plx / ble-manager": r"react-native-ble-plx|BleManager|react-native-ble-manager",
    "CoreBluetooth (iOS)": r"CBCentralManager|CBPeripheral",
    "Nordic BLE lib": r"no\.nordicsemi|NordicSemi",
    "Bluetooth classico (RFCOMM/SPP)": r"createRfcommSocket|RFCOMM|BluetoothSocket",
    "Receiver de conexao ACL": r"ACL_CONNECTED|ACTION_ACL_|BluetoothDevice\.ACTION",
    "Foreground Service": r"startForeground|ForegroundService|foregroundServiceType",
    "WorkManager/JobScheduler": r"WorkManager|JobScheduler|AlarmManager",
    "Companion Device Manager": r"CompanionDeviceManager",
    "Escrita em caracteristica": r"writeCharacteristic|\.write\(|writeValue|writeWithoutResponse",
}

FEATURES = {
    "Notificacoes": r"NotificationListenerService|NotificationManager|onNotificationPosted|notification",
    "Imagens/papel de parede do relogio": r"wallpaper|watchface|watch.?face|background.?image|dial",
    "Impressora 3D": r"octoprint|moonraker|klipper|gcode|3d.?print|marlin|bambu|creality",
    "Sincronizacao de hora": r"setTime|syncTime|currentTimeMillis|DateTime\.now|time.?sync",
    "Mapas/GPS/Localizacao": r"LocationManager|FusedLocation|geolocator|GoogleMap|mapbox",
    "Camera": r"CameraX|camera2|ImagePicker|image_picker|MediaStore",
    "Login/Autenticacao": r"FirebaseAuth|OAuth|login|signIn|JWT",
    "Banco local": r"Room\b|SQLite|sqflite|AsyncStorage|SharedPreferences|CoreData|Realm|Hive",
    "Firebase": r"firebase",
    "WebSocket/MQTT": r"WebSocket|MQTT|socket\.io",
    "Saude/Sensores": r"HeartRate|heart.?rate|SensorManager|steps|health_connect|HealthKit",
    "Musica/Midia": r"MediaSession|MediaPlayer|audio_service|spotify",
    "Ligacoes/SMS": r"TelephonyManager|PhoneStateListener|READ_SMS|CALL_LOG",
}

ARCH_HINTS = {
    "MVVM (ViewModel)": r"ViewModel\b|extends ViewModel|ChangeNotifier",
    "Repository pattern": r"Repository\b",
    "Dependency Injection": r"@Inject|Hilt|Dagger|Koin|get_it|GetIt",
    "Jetpack Compose": r"@Composable",
    "XML Views/Fragments": r"extends Fragment|: Fragment\(|setContentView|R\.layout\.",
    "Bloc/Cubit": r"\bBloc\b|\bCubit\b|flutter_bloc",
    "Provider/Riverpod": r"Provider\b|riverpod|ConsumerWidget",
    "Redux/Zustand/MobX": r"redux|zustand|mobx",
    "Coroutines/Flow": r"suspend fun|CoroutineScope|Flow<",
    "RxJava": r"io\.reactivex|Observable<",
    "React Navigation / Router": r"react-navigation|NavigationContainer|GoRouter|NavHost",
}

SCREEN_RE = re.compile(
    r"class\s+(\w+(?:Activity|Fragment|Screen|Page|View|Scene))\b"
    r"|@Composable\s+fun\s+(\w+(?:Screen|Page))\b"
    r"|(?:export\s+(?:default\s+)?(?:function|const)\s+)(\w+(?:Screen|Page))\b"
)


def walk(root: Path):
    for p in root.rglob("*"):
        if any(part in IGNORE_DIRS for part in p.relative_to(root).parts):
            continue
        if p.is_file():
            yield p


def read(p: Path) -> str:
    try:
        if p.stat().st_size > MAX_FILE_BYTES:
            return ""
        return p.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return ""


def clean_url(u: str) -> str:
    u = u.split("?")[0].rstrip(".,;")
    return u


def tree(root: Path, depth=2):
    lines = []

    def rec(d: Path, level: int):
        if level > depth:
            return
        try:
            items = sorted(d.iterdir(), key=lambda x: (x.is_file(), x.name.lower()))
        except OSError:
            return
        dirs = [i for i in items if i.is_dir() and i.name not in IGNORE_DIRS and not i.name.startswith(".")]
        for i in dirs:
            n = sum(1 for _ in i.rglob("*") if _.is_file())
            lines.append(f"{'  ' * level}- {i.name}/ ({n} arquivos)")
            rec(i, level + 1)

    rec(root, 0)
    return lines


def main():
    ap = argparse.ArgumentParser(description="Gera relatorio de contexto de um projeto.")
    ap.add_argument("path", nargs="?", default=None,
                    help="pasta raiz (padrao: a pasta onde este script esta)")
    ap.add_argument("-o", "--out", default=None,
                    help="arquivo de saida (padrao: contexto_projeto.md na raiz)")
    args = ap.parse_args()

    script = Path(__file__).resolve()
    root = Path(args.path).resolve() if args.path else script.parent
    if not root.is_dir():
        sys.exit(f"Pasta nao encontrada: {root}")
    out_path = Path(args.out).resolve() if args.out else root / "contexto_projeto.md"
    print(f"Analisando: {root}")

    lang_files, lang_lines = Counter(), Counter()
    manifests = []
    http_hits = defaultdict(set)
    http_methods = Counter()
    urls = Counter()
    uuids = Counter()
    ble = defaultdict(set)
    features = defaultdict(set)
    arch = defaultdict(set)
    screens = defaultdict(set)
    permissions, components, deps = set(), [], set()

    for p in walk(root):
        if p.resolve() in (script, out_path):
            continue          # ignora o proprio script e o relatorio
        rel = str(p.relative_to(root))
        ext = p.suffix.lower()

        if ext in LANGS:
            lang_files[LANGS[ext]] += 1
        if p.name in MANIFESTS:
            manifests.append((rel, MANIFESTS[p.name]))
        if ext not in TEXT_EXTS and p.name not in MANIFESTS:
            continue

        txt = read(p)
        if not txt:
            continue
        if ext in CODE_EXTS:
            lang_lines[LANGS[ext]] += txt.count("\n") + 1

        # Android manifest
        if p.name == "AndroidManifest.xml":
            permissions.update(re.findall(r'uses-permission[^>]*android:name="([^"]+)"', txt))
            for tag in ("service", "receiver", "activity", "provider"):
                for m in re.findall(rf"<{tag}[^>]*android:name=\"([^\"]+)\"", txt):
                    components.append(f"{tag}: {m}")
        # dependencias
        if p.name in ("build.gradle", "build.gradle.kts"):
            deps.update(re.findall(r"(?:implementation|api|kapt|ksp)\s*\(?\s*[\"']([^\"']+)[\"']", txt))
        elif p.name == "pubspec.yaml":
            sec = re.search(r"^dependencies:\s*\n((?:[ \t]+.*\n?)+)", txt, re.M)
            if sec:
                deps.update(re.findall(r"^\s{2}([a-z_0-9]+):", sec.group(1), re.M))
        elif p.name == "package.json":
            deps.update(re.findall(r'"([@\w/.-]+)"\s*:\s*"[\^~]?[\d.*x]', txt))
        elif p.name == "requirements.txt":
            deps.update(l.split("==")[0].strip() for l in txt.splitlines() if l.strip() and not l.startswith("#"))

        if ext not in CODE_EXTS and ext not in {".html"}:
            continue

        # HTTP
        for label, rx in HTTP_PATTERNS:
            for m in rx.finditer(txt):
                g = [x for x in m.groups() if x]
                if label == "Retrofit":
                    http_methods[g[0].upper()] += 1
                    http_hits[label].add(f"{g[0].upper()} {g[1]}")
                elif label in ("fetch/axios/http", "requests (py)"):
                    http_methods[g[0].upper()] += 1
                    http_hits[label].add(f"{g[0].upper()} {clean_url(g[1])}")
                elif label == "fetch()":
                    meth = (g[1] if len(g) > 1 else "GET").upper()
                    http_methods[meth] += 1
                    http_hits[label].add(f"{meth} {clean_url(g[0])}")
                elif label == "requestMethod":
                    http_methods[g[0].upper()] += 1
                else:
                    http_hits["Cliente HTTP usado"].add(g[0])

        for u in URL_RE.findall(txt):
            if not SECRET_HINT.search(u):
                urls[clean_url(u)] += 1
        for u in UUID_RE.findall(txt):
            uuids[u.lower()] += 1

        for name, rx in BLE_KEYWORDS.items():
            if re.search(rx, txt):
                ble[name].add(rel)
        for name, rx in FEATURES.items():
            if re.search(rx, txt, re.I):
                features[name].add(rel)
        for name, rx in ARCH_HINTS.items():
            if re.search(rx, txt):
                arch[name].add(rel)
        for m in SCREEN_RE.finditer(txt):
            name = next(g for g in m.groups() if g)
            screens[name].add(rel)

    # ---------------- relatorio ----------------
    out = [f"# Contexto do projeto: {root.name}\n"]

    out.append("## Linguagens (arquivos / linhas de codigo)")
    for lang, n in lang_files.most_common():
        out.append(f"- {lang}: {n} arquivos" + (f", ~{lang_lines[lang]} linhas" if lang_lines[lang] else ""))

    out.append("\n## Tipo de projeto (arquivos de manifesto)")
    for rel, kind in manifests[:25]:
        out.append(f"- {kind}: `{rel}`")

    out.append("\n## Estrutura de pastas")
    out.extend(tree(root) or ["- (vazia)"])

    out.append("\n## Arquitetura detectada")
    for name, files in sorted(arch.items(), key=lambda x: -len(x[1])):
        out.append(f"- {name} ({len(files)} arquivos, ex.: `{sorted(files)[0]}`)")

    out.append("\n## Telas / paginas")
    for name, files in sorted(screens.items()):
        out.append(f"- {name} (`{sorted(files)[0]}`)")
    if not screens:
        out.append("- nenhuma detectada pelo padrao de nomes")

    out.append("\n## Features (por palavra-chave)")
    for name, files in sorted(features.items(), key=lambda x: -len(x[1])):
        out.append(f"- {name}: {len(files)} arquivos, ex.: `{sorted(files)[0]}`")

    out.append("\n## Bluetooth / BLE / segundo plano (importante para o ESP32)")
    for name, files in sorted(ble.items()):
        ex = ", ".join(f"`{f}`" for f in sorted(files)[:3])
        out.append(f"- {name}: {ex}")
    if not ble:
        out.append("- nenhum uso de Bluetooth detectado")
    if uuids:
        out.append("\n### UUIDs encontrados")
        for u, n in uuids.most_common(30):
            out.append(f"- `{u}` ({n}x)")

    out.append("\n## Requisicoes HTTP")
    if http_methods:
        out.append("Metodos: " + ", ".join(f"{m} ({n})" for m, n in http_methods.most_common()))
    for label, items in http_hits.items():
        out.append(f"\n### {label}")
        for i in sorted(items)[:40]:
            out.append(f"- `{i}`")
    if urls:
        out.append("\n### URLs (sem query string)")
        for u, n in urls.most_common(30):
            out.append(f"- {u} ({n}x)")

    if permissions:
        out.append("\n## Permissoes Android")
        out.extend(f"- {p}" for p in sorted(permissions))
    if components:
        out.append("\n## Componentes Android (service/receiver/activity)")
        out.extend(f"- {c}" for c in components[:60])

    if deps:
        out.append("\n## Dependencias")
        out.extend(f"- {d}" for d in sorted(deps)[:120])

    out_path.write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"Relatorio salvo em: {out_path}")


if __name__ == "__main__":
    main()