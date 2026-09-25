package com.mynd.watch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import com.mynd.watch.R
import com.mynd.watch.bambu.BambuCloud
import com.mynd.watch.bambu.BambuWeatherMapper
import com.mynd.watch.bambu.PrintEvents
import com.mynd.watch.bambu.PrintState
import com.mynd.watch.ble.LinkState
import com.mynd.watch.ble.WatchLink
import com.mynd.watch.data.AppLog
import com.mynd.watch.data.Library
import com.mynd.watch.data.Settings
import com.mynd.watch.studio.ContractWeather
import com.mynd.watch.studio.DataContract
import com.mynd.watch.studio.FaceFile
import com.mynd.watch.studio.FaceLibrary
import com.mynd.watch.studio.FaceValidator
import com.mynd.watch.watch.WatchClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class UploadResult {
    data class Ok(val ref: FaceLibrary.Ref) : UploadResult()
    data class Fail(val msg: String) : UploadResult()
}

data class PrintSnapshot(
    val gcodeState: String?,
    val statePt: String,
    val percent: Int,
    val remainH: Int,
    val remainM: Int,
    val nozzle: Int,
    val nozzleTarget: Int,
    val bed: Int,
    val bedTarget: Int,
    val jobName: String,
    val layer: Int,
    val totalLayers: Int
)

class WatchService : Service() {

    companion object {
        const val ACTION_UPLOAD_FACE    = "com.mynd.watch.UPLOAD_FACE"
        const val ACTION_UPLOAD_URI     = "com.mynd.watch.UPLOAD_URI"
        const val ACTION_UPLOAD_LIBRARY = "com.mynd.watch.UPLOAD_LIBRARY"

        private val notifQueue = Channel<Pair<String, String>>(Channel.BUFFERED)
        fun enqueueNotification(title: String, text: String) { notifQueue.trySend(title to text) }

        @Volatile var instance: WatchService? = null
        val statusFlow       = MutableStateFlow("parado")
        val linkStateFlow    = MutableStateFlow(LinkState.DISCONNECTED)
        val printFlow        = MutableStateFlow<PrintSnapshot?>(null)
        val batteryFlow      = MutableStateFlow<Int?>(null)
        val installedFlow    = MutableStateFlow<FaceLibrary.Ref?>(null)
        val lastUploadResult = MutableStateFlow<UploadResult?>(null)
        val anteriorFlow     = MutableStateFlow<FaceLibrary.Ref?>(null)

        fun start(ctx: Context) {
            instance?.let { ctx.stopService(Intent(ctx, WatchService::class.java)) }
            val i = Intent(ctx, WatchService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var link: WatchLink
    private lateinit var watch: WatchClient
    private lateinit var settings: Settings
    private val bleLock = Mutex()
    private val print = PrintState()
    private val events = PrintEvents()
    private var bambu: BambuCloud? = null
    @Volatile private var printDirty = false
    private var lastTimeSync = 0L
    private val pendingNotifs = ArrayDeque<Pair<String, String>>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Library.init(this)
        installedFlow.value = Library.lib.installed()
        statusFlow.value = "Iniciando..."
        settings = Settings(this)
        link = WatchLink(this)
        watch = WatchClient(link, scope)
        startForegroundNotif("Conectando ao relógio...")
        scope.launch { link.state.collect { linkStateFlow.value = it } }
        scope.launch { connectionLoop() }
        scope.launch { notificationLoop() }
        scope.launch { bambuLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPLOAD_FACE -> {
                val name = intent.getStringExtra("asset") ?: return START_STICKY
                scope.launch { uploadAsset(name) }
            }
            ACTION_UPLOAD_URI -> {
                val uri = intent.getStringExtra("uri")?.let { Uri.parse(it) } ?: return START_STICKY
                scope.launch { uploadUri(uri) }
            }
            ACTION_UPLOAD_LIBRARY -> {
                val id = intent.getStringExtra("id") ?: return START_STICKY
                val v  = intent.getIntExtra("v", -1).takeIf { it >= 0 } ?: return START_STICKY
                scope.launch { uploadLibrary(id, v) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        statusFlow.value = "parado"
        linkStateFlow.value = LinkState.DISCONNECTED
        bambu?.disconnect()
        link.disconnect()
        super.onDestroy()
    }

    // ── BLE ──────────────────────────────────────────────────────────────
    private suspend fun connectionLoop() {
        var backoffMs = 5_000L
        while (true) {
            if (link.state.value != LinkState.READY) {
                try {
                    val mac = settings.get(Settings.WATCH_MAC)
                    val dev = if (mac != null) link.deviceByAddress(mac) else link.scanForWatch()
                    settings.set(Settings.WATCH_MAC, dev.address)
                    link.connect(dev)
                    val info = watch.info()
                    batteryFlow.value = watch.battery()
                    AppLog.add("relógio conectado: ${info.model} ${info.firmware} (MTU ${link.mtu})")
                    watch.syncTime()
                    lastTimeSync = System.currentTimeMillis()
                    updateStatus("Conectado: ${info.model}")
                    backoffMs = 5_000L
                    printDirty = true
                    while (pendingNotifs.isNotEmpty()) {
                        val (t, x) = pendingNotifs.removeFirst()
                        runCatching { bleLock.withLock { watch.notify(t, x) } }
                    }
                } catch (e: Exception) {
                    AppLog.add("conexão falhou: ${e.message} — tentando em ${backoffMs / 1000}s")
                    link.disconnect()
                    updateStatus("Desconectado (retry ${backoffMs / 1000}s)")
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                    continue
                }
            }
            if (System.currentTimeMillis() - lastTimeSync > 6 * 3_600_000L) {
                runCatching { bleLock.withLock { watch.syncTime() } }
                lastTimeSync = System.currentTimeMillis()
            }
            delay(5_000)
        }
    }

    private suspend fun notificationLoop() {
        for ((title, text) in notifQueue) {
            if (link.state.value != LinkState.READY) {
                pendingNotifs.addLast(title to text)
                while (pendingNotifs.size > 10) pendingNotifs.removeFirst()
                continue
            }
            runCatching { bleLock.withLock { watch.notify(title, text) } }
                .onFailure { AppLog.add("notificação falhou: ${it.message}") }
        }
    }

    // ── Bambu ────────────────────────────────────────────────────────────
    private suspend fun bambuLoop() {
        if (!settings.getBool(Settings.BAMBU_ENABLED)) { AppLog.add("Bambu desativada"); return }
        val token  = settings.get(Settings.BAMBU_TOKEN)
        val uid    = settings.get(Settings.BAMBU_UID)
        val serial = settings.get(Settings.BAMBU_SERIAL)
        if (token == null || uid == null || serial == null) { AppLog.add("Bambu: falta login/serial"); return }
        val cloud = BambuCloud().also { bambu = it }
        cloud.connect(uid, token, serial,
            onReport = { js ->
                if (print.merge(js)) {
                    printDirty = true
                    printFlow.value = print.toSnapshot()
                }
            },
            onState = { AppLog.add("Bambu MQTT: $it") })
        var last = 0L
        while (true) {
            delay(1_000)
            if (link.state.value != LinkState.READY) continue
            for ((t, x) in events.check(print)) enqueueNotification(t, x)
            if (printDirty && System.currentTimeMillis() - last >= 15_000 && print.gcodeState != null) {
                printDirty = false; last = System.currentTimeMillis()
                val inst     = Library.lib.installed()
                val contract = inst?.let { Library.lib.get(it.id)?.contract } ?: DataContract.REAL_WEATHER
                val frame    = ContractWeather.build(contract, print)
                if (frame != null)
                    runCatching { bleLock.withLock { watch.weather(frame) } }
                        .onSuccess { AppLog.add("Bambu → relógio: ${print.statePt} ${print.percent}% faltam ${PrintState.fmtRemaining(print.remainingMin)}") }
                        .onFailure { AppLog.add("clima falhou: ${it.message}") }
            }
            if (System.currentTimeMillis() / 1000 % 300 == 0L) cloud.pushall(serial)
        }
    }

    // ── Mostradores ──────────────────────────────────────────────────────
    private suspend fun uploadLibrary(id: String, v: Int) {
        val ref = FaceLibrary.Ref(id, v)
        val bytes = runCatching { Library.lib.bytes(ref) }.getOrElse {
            AppLog.add("envio bloqueado: versão não encontrada $id v$v"); return
        }
        val face = runCatching { FaceFile.parse(bytes) }.getOrElse {
            AppLog.add("envio bloqueado: .bin corrompido"); return
        }
        val contract = Library.lib.get(id)?.contract
        val issues = FaceValidator.check(face, contract, bytes.size)
        if (FaceValidator.hasErrors(issues)) {
            val msg = issues.first { it.level == FaceValidator.Level.ERRO }.msg
            AppLog.add("envio bloqueado: $msg")
            updateStatus("Envio bloqueado")
            lastUploadResult.value = UploadResult.Fail(msg)
            return
        }
        anteriorFlow.value = Library.lib.installed() ?: Library.lib.lastApproved()
        uploadBytes(bytes, "$id v$v")
        // uploadBytes sets status; result is communicated via lastUploadResult below
    }

    private suspend fun uploadAsset(name: String) =
        uploadBytes(assets.open("faces/$name").readBytes(), name)

    private suspend fun uploadUri(uri: Uri) {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: run {
            AppLog.add("erro ao abrir arquivo"); return
        }
        if (bytes.size < 16 || bytes[14] != 'J'.code.toByte() || bytes[15] != 'L'.code.toByte()) {
            AppLog.add("arquivo inválido: não é um mostrador JL (.bin)"); return
        }
        uploadBytes(bytes, uri.lastPathSegment?.substringAfterLast('/') ?: "mostrador.bin")
    }

    private suspend fun uploadBytes(bytes: ByteArray, name: String) {
        AppLog.add("enviando $name (${bytes.size} bytes)")
        runCatching {
            bleLock.withLock { watch.uploadFace(bytes) { p -> if (p % 10 == 0) updateStatus("Enviando $name: $p%") } }
        }.onSuccess {
            AppLog.add("$name instalado"); updateStatus("Mostrador instalado")
            // se veio de uploadLibrary, marcar como instalado e emitir Ok
            val parts = name.split(" v")
            if (parts.size == 2) {
                val ref = runCatching { FaceLibrary.Ref(parts[0], parts[1].toInt()) }.getOrNull()
                if (ref != null) { Library.lib.markInstalled(ref); installedFlow.value = ref; lastUploadResult.value = UploadResult.Ok(ref) }
            }
        }.onFailure {
            AppLog.add("envio falhou: ${it.message}")
            lastUploadResult.value = UploadResult.Fail(it.message ?: "erro desconhecido")
        }
    }

    // ── Notificação do serviço ────────────────────────────────────────────
    private fun updateStatus(text: String) {
        statusFlow.value = text
        getSystemService(NotificationManager::class.java).notify(1, buildNotif(text))
    }

    private fun buildNotif(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("svc") == null)
            nm.createNotificationChannel(NotificationChannel("svc", getString(R.string.channel_service), NotificationManager.IMPORTANCE_LOW))
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "svc")
                else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("MYND Watch").setContentText(text)
                .setSmallIcon(R.drawable.ic_mynd).setOngoing(true).build()
    }

    private fun startForegroundNotif(text: String) {
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, buildNotif(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else
            startForeground(1, buildNotif(text))
    }
}

private fun PrintState.toSnapshot() = PrintSnapshot(
    gcodeState = gcodeState, statePt = statePt, percent = percent,
    remainH = remainingMin / 60, remainM = remainingMin % 60,
    nozzle = nozzle, nozzleTarget = nozzleTarget,
    bed = bed, bedTarget = bedTarget,
    jobName = jobName, layer = layer, totalLayers = totalLayers
)
