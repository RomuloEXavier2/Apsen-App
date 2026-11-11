# Contexto do projeto: mostrador_MYND

## Linguagens (arquivos / linhas de codigo)
- Kotlin: 54 arquivos, ~6622 linhas
- Python: 26 arquivos, ~4613 linhas
- XML: 7 arquivos
- Kotlin Script: 6 arquivos, ~168 linhas
- JSON: 2 arquivos

## Tipo de projeto (arquivos de manifesto)
- Gradle KTS (Android/JVM): `mynd-watch-app\build.gradle.kts`
- Gradle KTS (Android/JVM): `_arquivo_antigo\mynd-watch-app_copia_antiga\build.gradle.kts`
- Gradle KTS (Android/JVM): `_arquivo_antigo\mynd-watch-app_copia_antiga\app\build.gradle.kts`
- Android nativo: `_arquivo_antigo\mynd-watch-app_copia_antiga\app\src\main\AndroidManifest.xml`
- Gradle KTS (Android/JVM): `mynd-watch-app\app\build.gradle.kts`
- Android nativo: `mynd-watch-app\app\src\main\AndroidManifest.xml`

## Estrutura de pastas
- _arquivo_antigo/ (45 arquivos)
  - mynd-watch-app_copia_antiga/ (45 arquivos)
    - app/ (21 arquivos)
    - docs/ (2 arquivos)
    - reference/ (16 arquivos)
- gerador/ (5 arquivos)
- mynd-watch-app/ (1420 arquivos)
  - _to_delete/ (1 arquivos)
  - app/ (1347 arquivos)
    - src/ (47 arquivos)
  - docs/ (5 arquivos)
    - legado/ (1 arquivos)
  - gradle/ (2 arquivos)
    - wrapper/ (2 arquivos)
  - reference/ (16 arquivos)
    - python/ (9 arquivos)
    - sondas/ (6 arquivos)
    - testvectors/ (1 arquivos)
  - studio_pack/ (18 arquivos)
    - assets/ (7 arquivos)
    - src/ (8 arquivos)
    - test/ (3 arquivos)
- mynd_bambu/ (8 arquivos)
- PATCH_BAMBU_v4/ (7 arquivos)
  - app/ (3 arquivos)
    - src/ (3 arquivos)
  - pc/ (3 arquivos)

## Arquitetura detectada
- Coroutines/Flow (15 arquivos, ex.: `_arquivo_antigo\mynd-watch-app_copia_antiga\app\src\main\java\com\mynd\watch\bambu\BambuCloud.kt`)
- Jetpack Compose (7 arquivos, ex.: `_arquivo_antigo\mynd-watch-app_copia_antiga\app\src\main\java\com\mynd\watch\ui\MainActivity.kt`)

## Telas / paginas
- LibraryScreen (`mynd-watch-app\app\src\main\java\com\mynd\watch\ui\LibraryScreen.kt`)
- MainActivity (`_arquivo_antigo\mynd-watch-app_copia_antiga\app\src\main\java\com\mynd\watch\ui\MainActivity.kt`)
- PreviewScreen (`mynd-watch-app\app\src\main\java\com\mynd\watch\ui\PreviewScreen.kt`)
- StudioScreen (`mynd-watch-app\app\src\main\java\com\mynd\watch\ui\StudioScreen.kt`)

## Features (por palavra-chave)
- Impressora 3D: 34 arquivos, ex.: `PATCH_BAMBU_v4\app\src\main\java\com\mynd\watch\bambu\PrintState.kt`
- Sincronizacao de hora: 21 arquivos, ex.: `PATCH_BAMBU_v4\app\src\test\java\com\mynd\watch\core\ProtocolTest.kt`
- WebSocket/MQTT: 13 arquivos, ex.: `PATCH_BAMBU_v4\app\src\main\java\com\mynd\watch\bambu\PrintState.kt`
- Login/Autenticacao: 8 arquivos, ex.: `_arquivo_antigo\mynd-watch-app_copia_antiga\app\build.gradle.kts`
- Notificacoes: 8 arquivos, ex.: `_arquivo_antigo\mynd-watch-app_copia_antiga\app\src\main\java\com\mynd\watch\ble\WatchLink.kt`
- Imagens/papel de parede do relogio: 6 arquivos, ex.: `mynd-watch-app\app\src\main\java\com\mynd\watch\studio\FaceComposer.kt`
- Banco local: 5 arquivos, ex.: `_arquivo_antigo\mynd-watch-app_copia_antiga\app\build.gradle.kts`
- Saude/Sensores: 5 arquivos, ex.: `_arquivo_antigo\mynd-watch-app_copia_antiga\reference\python\hud.py`

## Bluetooth / BLE / segundo plano (importante para o ESP32)
- BluetoothGatt (Android nativo): `_arquivo_antigo\mynd-watch-app_copia_antiga\app\src\main\java\com\mynd\watch\ble\WatchLink.kt`, `mynd-watch-app\app\src\main\java\com\mynd\watch\ble\WatchLink.kt`
- BluetoothLeScanner (scan BLE): `_arquivo_antigo\mynd-watch-app_copia_antiga\app\src\main\java\com\mynd\watch\ble\WatchLink.kt`, `mynd-watch-app\app\src\main\java\com\mynd\watch\ble\WatchLink.kt`
- Escrita em caracteristica: `PATCH_BAMBU_v4\app\src\test\java\com\mynd\watch\core\ProtocolTest.kt`, `_arquivo_antigo\mynd-watch-app_copia_antiga\app\src\main\java\com\mynd\watch\ble\WatchLink.kt`, `_arquivo_antigo\mynd-watch-app_copia_antiga\app\src\main\java\com\mynd\watch\core\Frame.kt`
- Foreground Service: `_arquivo_antigo\mynd-watch-app_copia_antiga\app\src\main\java\com\mynd\watch\service\WatchService.kt`, `mynd-watch-app\app\src\main\java\com\mynd\watch\service\WatchService.kt`

### UUIDs encontrados
- `7b96b73d-2d2c-5012-82f8-7111ae7ae94a` (3x)
- `00002902-0000-1000-8000-00805f9b34fb` (2x)

## Requisicoes HTTP

### Cliente HTTP usado
- `OkHttpClient`
- `Request.Builder`

### URLs (sem query string)
- https://api.bambulab.com (2x)

## Permissoes Android
- android.permission.ACCESS_FINE_LOCATION
- android.permission.BLUETOOTH
- android.permission.BLUETOOTH_ADMIN
- android.permission.BLUETOOTH_CONNECT
- android.permission.BLUETOOTH_SCAN
- android.permission.FOREGROUND_SERVICE
- android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE
- android.permission.INTERNET
- android.permission.POST_NOTIFICATIONS
- android.permission.RECEIVE_BOOT_COMPLETED
- android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
- android.permission.WAKE_LOCK

## Componentes Android (service/receiver/activity)
- service: .service.WatchService
- service: .service.NotificationForwarder
- receiver: .service.BootReceiver
- activity: .ui.MainActivity
- service: .service.WatchService
- service: .service.NotificationForwarder
- receiver: .service.BootReceiver
- activity: .ui.MainActivity
- provider: androidx.core.content.FileProvider

## Dependencias
- androidx.activity:activity-compose:1.9.3
- androidx.compose.material3:material3
- androidx.compose.material:material-icons-extended
- androidx.compose.ui:ui
- androidx.compose.ui:ui-tooling-preview
- androidx.core:core-ktx:1.15.0
- androidx.datastore:datastore-preferences:1.1.1
- androidx.lifecycle:lifecycle-runtime-ktx:2.8.7
- androidx.lifecycle:lifecycle-service:2.8.7
- com.hivemq:hivemq-mqtt-client:1.3.3
- com.squareup.okhttp3:okhttp:4.12.0
- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0
