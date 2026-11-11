package com.mynd.watch.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.UUID

/**
 * Link BLE com o relogio do carro MYND-Clock (ESP32 + display redondo).
 *
 * Mantem uma conexao ABERTA enquanto o ESP32 estiver por perto, para que as notificacoes
 * cheguem na hora. Tudo vai como texto na mesma caracteristica de antes:
 *
 *   T:1731240000              hora (epoch UTC) - no conectar e a cada RESYNC_MS
 *   C:25                      temperatura externa (C:- apaga)
 *   P:68,R                    impressora: % e estado (R imprimindo, P pausada, F concluida, X falhou, I parada)
 *   N:W|Fulano|Chego em 10    notificacao (W = WhatsApp, I = Instagram)
 *
 * - Scan BLE filtrado pelo UUID do servico (baixo consumo); ao ver o ESP32, conecta.
 * - Pede MTU 247 para a notificacao caber num pacote so.
 * - Uma escrita por vez (fila); temperatura e impressora guardam so o ultimo valor
 *   e sao reenviadas sempre que reconecta.
 * - Notificacoes ficam numa fila de ate 5 e descartadas se esperarem mais de 2 min.
 * - Conectar na multimidia (nome contendo CAR_NAME) apenas acelera a procura.
 *
 * Uso (dentro do WatchService):
 *   onCreate():  clockSync = ClockSync(this) { msg -> ... }; clockSync.start()
 *   onDestroy(): clockSync.stop()
 *   clockSync.setTemp(25); clockSync.setPrint(68, 'R'); clockSync.notify('W', "Fulano", "Oi")
 */
@SuppressLint("MissingPermission")
class ClockSync(
    private val ctx: Context,
    private val log: (String) -> Unit = { Log.i(TAG, it) }
) {

    companion object {
        private const val TAG = "ClockSync"

        // Mesmos UUIDs do firmware do ESP32
        val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")

        /** Trecho do nome Bluetooth da multimidia (ignora maiusculas/minusculas). */
        const val CAR_NAME = "KRC"

        private const val MTU = 247
        private const val RESYNC_MS = 5 * 60_000L        // reenvia a hora a cada 5 min
        private const val RETRY_GAP_MS = 5_000L          // espera minima entre tentativas de conexao
        private const val CONNECT_TIMEOUT_MS = 20_000L   // conectar + MTU + servicos
        private const val WRITE_TIMEOUT_MS = 8_000L
        private const val RESCAN_MS = 15 * 60_000L       // reinicia o scan de tempos em tempos (seguranca)
        private const val SCAN_RETRY_MS = 30_000L
        private const val NOTIF_MAX_AGE_MS = 2 * 60_000L
        private const val NOTIF_MAX = 5

        /** Texto para o display: sem acentos/emoji, so ASCII imprimivel, sem '|' (separador). */
        fun clean(s: String, max: Int): String {
            val n = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            val sb = StringBuilder()
            for (ch in n) {
                when {
                    ch == '\n' || ch == '\r' || ch == '\t' -> sb.append(' ')
                    ch == '|' -> sb.append('/')
                    ch.code in 0x20..0x7E -> sb.append(ch)
                }
            }
            return sb.toString().replace(Regex(" {2,}"), " ").trim().take(max)
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter?
        get() = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var started = false
    private var scanning = false
    private var gatt: BluetoothGatt? = null
    private var ch: BluetoothGattCharacteristic? = null
    private var ready = false                // conectado, MTU ok e servico encontrado
    private var inFlight: String? = null     // escrita aguardando onCharacteristicWrite
    private var lastAttemptMs = 0L
    private var lastTimeSyncMs = 0L

    // O que precisa ser enviado
    private var timeDue = true
    private var tempMsg: String? = null
    private var tempDirty = false
    private var printMsg: String? = null
    private var printDirty = false
    private val notifs = ArrayDeque<Pair<Long, String>>()

    /** true enquanto o relogio do carro estiver conectado e pronto. */
    val isConnected: Boolean get() = ready

    // ------------------------------------------------------------------ API publica (qualquer thread)

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        if (adapter?.isEnabled == true) startWatching()
        else log("Bluetooth desligado, aguardando ligar")
        main.postDelayed(tick, 30_000)
    }

    fun stop() {
        if (!started) return
        started = false
        try { ctx.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
        main.removeCallbacksAndMessages(null)
        stopScan()
        closeGatt()
    }

    /** Forca o envio da hora (pode ser ligado a um botao na UI). */
    fun syncNow() {
        main.post {
            if (!started) return@post
            timeDue = true
            if (ready) { pump(); return@post }
            lastAttemptMs = 0L
            if (adapter?.isEnabled == true) {
                if (scanning) restartScan() else startWatching()
            } else log("Bluetooth desligado")
        }
    }

    /** Temperatura externa em graus C (null = apaga no display). */
    fun setTemp(c: Int?) {
        main.post {
            val m = if (c == null) "C:-" else "C:$c"
            if (m != tempMsg) { tempMsg = m; tempDirty = true; pump() }
        }
    }

    /** Progresso da impressora. state: R, P, F, X ou I (pct -1 = sem impressao). */
    fun setPrint(pct: Int, state: Char) {
        main.post {
            val m = "P:$pct,$state"
            if (m != printMsg) { printMsg = m; printDirty = true; pump() }
        }
    }

    /** Notificacao para o buddy mostrar em tela cheia. app: 'W' WhatsApp, 'I' Instagram. */
    fun notify(app: Char, title: String, text: String) {
        main.post {
            val m = "N:$app|${clean(title, 32)}|${clean(text, 150)}"
            notifs.addLast(SystemClock.elapsedRealtime() to m)
            while (notifs.size > NOTIF_MAX) notifs.removeFirst()
            if (ready) pump() else log("Notificação guardada (relógio do carro desconectado)")
        }
    }

    // ------------------------------------------------------------------ eventos do sistema

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        BluetoothAdapter.STATE_ON -> {
                            log("Bluetooth ligado")
                            startWatching()
                        }
                        BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                            log("Bluetooth desligado")
                            stopScan()
                            closeGatt()
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val dev = if (Build.VERSION.SDK_INT >= 33)
                        i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val name = runCatching { dev?.name }.getOrNull()
                    if (name?.contains(CAR_NAME, ignoreCase = true) == true && !ready) {
                        log("Multimídia conectada ($name): procurando o relógio agora")
                        lastAttemptMs = 0L
                        if (scanning) restartScan() else startWatching()
                    }
                }
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!started) return
            if (ready && SystemClock.elapsedRealtime() - lastTimeSyncMs > RESYNC_MS) {
                timeDue = true
                pump()
            }
            main.postDelayed(this, 30_000)
        }
    }

    // ------------------------------------------------------------------ scan

    private val rescan = Runnable { restartScan() }

    private fun restartScan() {
        stopScan()
        startWatching()
    }

    private fun startWatching() {
        if (!started || scanning) return
        val scanner = adapter?.bluetoothLeScanner ?: return
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
        try {
            scanner.startScan(filters, settings, scanCb)
            scanning = true
            log("Procurando o relógio MYND-Clock...")
        } catch (e: SecurityException) {
            log("Sem permissão de Bluetooth para o scan: ${e.message}")
        } catch (e: IllegalStateException) {
            log("Scan indisponível: ${e.message}")
        }
        main.removeCallbacks(rescan)
        main.postDelayed(rescan, if (scanning) RESCAN_MS else SCAN_RETRY_MS)
    }

    private fun stopScan() {
        main.removeCallbacks(rescan)
        if (!scanning) return
        scanning = false
        try { adapter?.bluetoothLeScanner?.stopScan(scanCb) } catch (_: Exception) {}
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            main.post { handleSeen(result.device) }
        }

        override fun onScanFailed(errorCode: Int) {
            log("Scan falhou (código $errorCode), tentando de novo em 30 s")
            main.post {
                scanning = false
                main.removeCallbacks(rescan)
                main.postDelayed(rescan, SCAN_RETRY_MS)
            }
        }
    }

    private fun handleSeen(dev: BluetoothDevice) {
        if (gatt != null) return                          // ja conectado ou conectando
        val now = SystemClock.elapsedRealtime()
        if (lastAttemptMs != 0L && now - lastAttemptMs < RETRY_GAP_MS) return
        connect(dev)
    }

    // ------------------------------------------------------------------ GATT

    private val connectTimeout = Runnable {
        log("Tempo esgotado na conexão com o relógio do carro")
        closeGatt()
    }

    private val writeTimeout = Runnable {
        log("Relógio do carro não respondeu à escrita, reconectando")
        closeGatt()
    }

    private fun connect(dev: BluetoothDevice) {
        lastAttemptMs = SystemClock.elapsedRealtime()
        log("Relógio do carro encontrado, conectando...")
        main.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
        try {
            gatt = dev.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            log("Sem permissão para conectar: ${e.message}")
            closeGatt()
        }
    }

    private fun closeGatt() {
        main.removeCallbacks(connectTimeout)
        main.removeCallbacks(writeTimeout)
        val wasReady = ready
        ready = false
        ch = null
        inFlight?.let { if (it.startsWith("N:")) notifs.addFirst(SystemClock.elapsedRealtime() to it) }
        inFlight = null
        // ao reconectar manda tudo de novo (o ESP32 pode ter reiniciado)
        timeDue = true
        tempDirty = tempMsg != null
        printDirty = printMsg != null
        val g = gatt ?: return
        gatt = null
        try { g.close() } catch (_: Exception) {}
        if (wasReady) log("Relógio do carro desconectado")
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (g !== gatt) return@post
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    if (!g.requestMtu(MTU)) g.discoverServices()
                } else {
                    closeGatt()
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            main.post { if (g === gatt && !ready) g.discoverServices() }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                if (g !== gatt) return@post
                val c = g.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
                if (status != BluetoothGatt.GATT_SUCCESS || c == null) {
                    log("Serviço do relógio do carro não encontrado")
                    g.disconnect()
                    return@post
                }
                ch = c
                ready = true
                main.removeCallbacks(connectTimeout)
                log("Relógio do carro conectado")
                pump()
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            main.post {
                if (g !== gatt) return@post
                main.removeCallbacks(writeTimeout)
                val m = inFlight
                inFlight = null
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    when {
                        m == null -> {}
                        m.startsWith("T:") -> { lastTimeSyncMs = SystemClock.elapsedRealtime(); log("Hora enviada ao relógio do carro") }
                        m.startsWith("N:") -> log("Notificação enviada ao relógio do carro")
                        m.startsWith("C:") -> log("Temperatura enviada: ${m.substring(2)}°C")
                        m.startsWith("P:") -> log("Impressora enviada: ${m.substring(2)}")
                    }
                } else {
                    log("Falha ao enviar \"${m?.take(12)}\" (status $status)")
                    if (m?.startsWith("N:") == true) notifs.addFirst(SystemClock.elapsedRealtime() to m)
                }
                pump()
            }
        }
    }

    /** Envia a proxima mensagem pendente (uma por vez). Sempre na thread principal. */
    private fun pump() {
        if (!ready || inFlight != null) return
        val g = gatt ?: return
        val c = ch ?: return
        val msg: String = when {
            timeDue -> { timeDue = false; "T:${System.currentTimeMillis() / 1000L}" }
            tempDirty && tempMsg != null -> { tempDirty = false; tempMsg!! }
            printDirty && printMsg != null -> { printDirty = false; printMsg!! }
            else -> nextNotif() ?: return
        }
        write(g, c, msg)
    }

    private fun nextNotif(): String? {
        val now = SystemClock.elapsedRealtime()
        while (notifs.isNotEmpty()) {
            val (t, m) = notifs.removeFirst()
            if (now - t <= NOTIF_MAX_AGE_MS) return m
        }
        return null
    }

    private fun write(g: BluetoothGatt, c: BluetoothGattCharacteristic, msg: String) {
        val bytes = msg.toByteArray(Charsets.UTF_8)
        inFlight = msg
        main.postDelayed(writeTimeout, WRITE_TIMEOUT_MS)
        val ok = try {
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    c.value = bytes
                    g.writeCharacteristic(c)
                }
            }
        } catch (e: SecurityException) {
            false
        }
        if (!ok) {
            log("writeCharacteristic recusado, reconectando")
            g.disconnect()                     // closeGatt() acontece em onConnectionStateChange / timeout
        }
    }
}
