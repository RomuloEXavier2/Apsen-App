package com.mynd.watch.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.util.Log
import com.mynd.watch.core.Deframer
import com.mynd.watch.core.Frame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** UUIDs 16-bit do relógio (serviços 0x8800 e 0x9900). */
object Gatt {
    private fun u(x: Int) = UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(x))
    val SVC_CMD: UUID = u(0x8800)
    val CH_CMD: UUID = u(0x8801)    // comandos: write-no-resp + notify (handle 0x000E)
    val CH_EVT: UUID = u(0x8802)    // eventos do relógio: notify (0x0011)
    val CH_SYNC: UUID = u(0x8803)   // sync: write-no-resp + notify (0x0014)
    val SVC_FILE: UUID = u(0x9900)
    val CH_FCTL: UUID = u(0x9901)   // controle de arquivo: write-no-resp + notify (0x0018)
    val CH_FDAT: UUID = u(0x9902)   // dados de arquivo: write-no-resp (0x001B)
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

enum class LinkState { DISCONNECTED, SCANNING, CONNECTING, READY }

data class Incoming(val channel: UUID, val frame: Frame)

/**
 * Camada BLE crua: scan, conexão, MTU, notificações e escrita serializada.
 * Todas as escritas passam por um Mutex e esperam onCharacteristicWrite (Android exige 1 operação GATT por vez).
 *
 * TODO(agente): testar no aparelho real; ver INSTRUCOES.txt fase 1.
 */
@SuppressLint("MissingPermission")   // permissões são pedidas na MainActivity antes de usar
class WatchLink(private val ctx: Context) {

    private val tag = "MYND/Link"
    private val manager = ctx.getSystemService(BluetoothManager::class.java)
    private var gatt: BluetoothGatt? = null
    private val chars = mutableMapOf<UUID, BluetoothGattCharacteristic>()
    private val deframers = mutableMapOf<UUID, Deframer>()
    private val gattMutex = Mutex()
    private var pendingOp: CompletableDeferred<Int>? = null

    val state = MutableStateFlow(LinkState.DISCONNECTED)
    val stateFlow: StateFlow<LinkState> get() = state
    var mtu = 23; private set

    private val _incoming = MutableSharedFlow<Incoming>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val incoming: SharedFlow<Incoming> = _incoming

    // ---------------- scan ----------------
    suspend fun scanForWatch(timeoutMs: Long = 12_000, nameContains: String = "HAYLOU"): BluetoothDevice {
        state.value = LinkState.SCANNING
        val found = CompletableDeferred<BluetoothDevice>()
        val scanner = manager.adapter.bluetoothLeScanner
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val n = result.device.name ?: result.scanRecord?.deviceName ?: return
                if (n.contains(nameContains, ignoreCase = true)) found.complete(result.device)
            }
        }
        scanner.startScan(cb)
        try {
            return withTimeout(timeoutMs) { found.await() }
        } finally {
            scanner.stopScan(cb)
        }
    }

    fun deviceByAddress(mac: String): BluetoothDevice = manager.adapter.getRemoteDevice(mac)

    // ---------------- conexão ----------------
    private val ready = MutableStateFlow(false)
    private var connectDeferred: CompletableDeferred<Unit>? = null

    suspend fun connect(device: BluetoothDevice, timeoutMs: Long = 20_000) {
        state.value = LinkState.CONNECTING
        val d = CompletableDeferred<Unit>(); connectDeferred = d
        gatt = device.connectGatt(ctx, false, callback, BluetoothDevice.TRANSPORT_LE)
        withTimeout(timeoutMs) { d.await() }
        // habilita notificações nos 4 canais (igual ao app oficial: CCCD 0100 em 0x0F,0x12,0x15,0x19)
        for (u in listOf(Gatt.CH_CMD, Gatt.CH_EVT, Gatt.CH_SYNC, Gatt.CH_FCTL)) enableNotify(u)
        state.value = LinkState.READY
    }

    fun disconnect() {
        gatt?.disconnect(); gatt?.close(); gatt = null
        chars.clear(); state.value = LinkState.DISCONNECTED
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.i(tag, "conn status=$status state=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                g.requestMtu(247)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                state.value = LinkState.DISCONNECTED
                pendingOp?.complete(-1)
                connectDeferred?.completeExceptionally(IllegalStateException("desconectou (status $status)"))
                g.close()
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            mtu = newMtu; Log.i(tag, "MTU=$newMtu"); g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            for (s in g.services) for (c in s.characteristics) chars[c.uuid] = c
            Log.i(tag, "serviços: ${g.services.map { it.uuid }}")
            if (chars.containsKey(Gatt.CH_CMD) && chars.containsKey(Gatt.CH_FDAT)) connectDeferred?.complete(Unit)
            else connectDeferred?.completeExceptionally(IllegalStateException("características do relógio não encontradas"))
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pendingOp?.complete(status)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            pendingOp?.complete(status)
        }

        @Deprecated("API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            onData(c.uuid, c.value ?: return)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            onData(c.uuid, value)
        }
    }

    private fun onData(uuid: UUID, data: ByteArray) {
        val d = deframers.getOrPut(uuid) { Deframer() }
        for (f in d.feed(data)) {
            Log.d(tag, "RX ${uuid.toString().substring(4, 8)} $f")
            _incoming.tryEmit(Incoming(uuid, f))
        }
    }

    private suspend fun enableNotify(u: UUID) = gattMutex.withLock {
        val c = chars[u] ?: return@withLock
        val g = gatt ?: return@withLock
        g.setCharacteristicNotification(c, true)
        val desc = c.getDescriptor(Gatt.CCCD) ?: return@withLock
        val op = CompletableDeferred<Int>(); pendingOp = op
        if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        else @Suppress("DEPRECATION") { desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(desc) }
        withTimeout(5_000) { op.await() }
    }

    /** Escreve bytes crus, fatiando em (MTU-3). Todos os canais do relógio usam write-without-response. */
    suspend fun writeRaw(u: UUID, data: ByteArray) {
        val step = (mtu - 3).coerceIn(20, 241)
        var i = 0
        while (i < data.size) {
            writeOne(u, data.copyOfRange(i, minOf(i + step, data.size)))
            i += step
        }
    }

    suspend fun send(u: UUID, frame: Frame) {
        Log.d(tag, "TX ${u.toString().substring(4, 8)} $frame")
        writeRaw(u, frame.toBytes())
    }

    private suspend fun writeOne(u: UUID, chunk: ByteArray) = gattMutex.withLock {
        val c = chars[u] ?: error("característica $u não encontrada")
        val g = gatt ?: error("sem conexão")
        val op = CompletableDeferred<Int>(); pendingOp = op
        val type = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(c, chunk, type)
        else @Suppress("DEPRECATION") { c.writeType = type; c.value = chunk; g.writeCharacteristic(c) }
        withTimeout(5_000) { op.await() }   // no-response também gera onCharacteristicWrite (fila local)
    }
}
