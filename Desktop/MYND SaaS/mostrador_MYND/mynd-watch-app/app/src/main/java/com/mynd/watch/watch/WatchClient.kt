package com.mynd.watch.watch

import android.util.Log
import com.mynd.watch.ble.Gatt
import com.mynd.watch.ble.Incoming
import com.mynd.watch.ble.WatchLink
import com.mynd.watch.core.Cmd
import com.mynd.watch.core.FaceUpload
import com.mynd.watch.core.Frame
import com.mynd.watch.core.Protobuf.int
import com.mynd.watch.core.Protobuf.text
import com.mynd.watch.core.WatchCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

data class WatchInfo(val serial: String?, val model: String?, val firmware: String?)

/**
 * API de alto nível do relógio (equivale à classe Watch do haylou_s6.py).
 * Cada método envia o quadro e espera o ACK/resposta correspondente.
 */
class WatchClient(private val link: WatchLink, scope: CoroutineScope) {

    private val tag = "MYND/Watch"

    init {
        // responde automaticamente aos pedidos de sync do canal 8803 (igual ao app oficial)
        scope.launch {
            link.incoming.collect { m ->
                if (m.channel == Gatt.CH_SYNC && m.frame.cmd == Cmd.SYNC_REQ) {
                    link.send(Gatt.CH_SYNC, WatchCommands.syncAck(m.frame.fields.int(1) ?: 0))
                } else if (m.channel == Gatt.CH_EVT) {
                    Log.i(tag, "evento do relógio: ${m.frame}")   // TODO(agente): mapear 0x7D1/0x7D6/0x7D7
                }
            }
        }
    }

    private suspend fun await(timeoutMs: Long = 5_000, pred: (Incoming) -> Boolean): Incoming =
        withTimeout(timeoutMs) { link.incoming.filter(pred).first() }

    /** Começa a escutar ANTES de enviar (SharedFlow não tem replay) e então espera a resposta. */
    private suspend fun request(ch: UUID, fr: Frame, timeoutMs: Long = 5_000, pred: (Incoming) -> Boolean): Incoming =
        coroutineScope {
            val resp = async(start = CoroutineStart.UNDISPATCHED) { await(timeoutMs, pred) }
            link.send(ch, fr)
            resp.await()
        }

    private suspend fun sendAndAck(fr: Frame, timeoutMs: Long = 5_000) {
        runCatching {
            request(Gatt.CH_CMD, fr, timeoutMs) {
                it.channel == Gatt.CH_CMD && it.frame.cmd == Cmd.ACK && it.frame.fields.int(1) == fr.cmd.toLong()
            }
        }.onFailure { Log.w(tag, "sem ACK para 0x%04X".format(fr.cmd)) }
    }

    suspend fun info(): WatchInfo {
        val f = request(Gatt.CH_CMD, WatchCommands.getInfo()) { it.channel == Gatt.CH_CMD && it.frame.cmd == Cmd.INFO }.frame.fields
        return WatchInfo(f.text(1), f.text(2), f.text(3))
    }

    suspend fun battery(): Int? = runCatching {
        request(Gatt.CH_CMD, WatchCommands.getBattery()) { it.frame.cmd == Cmd.BATTERY }.frame.fields.int(2)?.toInt()
    }.getOrNull()

    suspend fun syncTime() = sendAndAck(WatchCommands.setTime())
    suspend fun notify(title: String, text: String, appId: Int = 110) = sendAndAck(WatchCommands.notify(title, text, appId))
    suspend fun weather(fr: Frame) = sendAndAck(fr)

    /** Envia um mostrador .bin. onProgress(0..100). Lança exceção se o relógio recusar. */
    suspend fun uploadFace(file: ByteArray, onProgress: (Int) -> Unit = {}) {
        val up = FaceUpload(file)
        sendAndAck(WatchCommands.transferMode(true))
        val isStatus: (Incoming) -> Boolean = { it.channel == Gatt.CH_FCTL && it.frame.cmd == Cmd.FILE_STATUS }
        try {
            var st = request(Gatt.CH_FCTL, up.startFrame(), 20_000, isStatus)
            while (true) {
                when (val a = up.onStatus(st.frame)) {
                    is FaceUpload.Action.SendBlock -> {
                        // escuta o próximo status antes de mandar o bloco
                        st = coroutineScope {
                            val next = async(start = CoroutineStart.UNDISPATCHED) { await(20_000, isStatus) }
                            link.writeRaw(Gatt.CH_FDAT, a.data)
                            onProgress(up.progress(a.offset + a.data.size))
                            next.await()
                        }
                        continue
                    }
                    FaceUpload.Action.Done -> { onProgress(100); break }
                    is FaceUpload.Action.Failed -> error("relógio recusou (${a.code}): ${a.reason}")
                    FaceUpload.Action.Ignore -> st = await(20_000, isStatus)
                }
            }
        } finally {
            runCatching { sendAndAck(WatchCommands.transferMode(false)) }
        }
    }

    fun channelName(u: UUID) = u.toString().substring(4, 8)
}
