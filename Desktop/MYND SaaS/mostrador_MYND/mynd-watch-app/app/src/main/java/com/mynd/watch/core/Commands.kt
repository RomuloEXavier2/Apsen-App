package com.mynd.watch.core

import com.mynd.watch.core.Protobuf.int
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Comandos conhecidos do Haylou Watch S6 (firmware V25.26.001.2.1.9.1.26).
 * Tudo aqui foi observado no btsnoop do app oficial e/ou validado no relógio real.
 * Veja docs/PROTOCOLO.md para a tabela completa.
 */
object Cmd {
    const val ACK = 0x0001            // relógio -> app: {1: cmd confirmado}
    const val GET = 0x0002            // app -> relógio: {1: 3}=info, {1: 9}=bateria
    const val INFO = 0x0003           // resposta: {1 serial, 2 modelo, 3 firmware, 5 ?, 7 MAC, ...}
    const val USER_PROFILE = 0x0004   // perfil do usuário (idade/altura/peso...) - campos ainda não mapeados 100%
    const val SET_TIME = 0x0005
    const val BATTERY = 0x0009        // resposta a GET{1:9}: {2: %}
    const val NOTIFY = 0x0013
    const val WEATHER = 0x0014
    const val TRANSFER_MODE = 0x0034  // {1:1} antes de enviar arquivo; {} depois

    // canal de sincronização (8803)
    const val SYNC_REQ = 0x0BBB       // relógio pede confirmação
    const val SYNC_ACK = 0x0BB8       // app responde {1: 0x0BBB, 2: campo1 recebido}

    // canal de arquivos (9901)
    const val FILE_START = 9001       // 0x2329
    const val FILE_STATUS = 9000      // 0x2328

    // eventos espontâneos (8802) - significado parcial
    const val EVT_7D1 = 0x07D1
    const val EVT_7D6 = 0x07D6
    const val EVT_7D7 = 0x07D7
}

object WatchCommands {

    fun getInfo() = Frame.of(Cmd.GET, 1 to 3)
    fun getBattery() = Frame.of(Cmd.GET, 1 to 9)
    fun transferMode(on: Boolean) = if (on) Frame.of(Cmd.TRANSFER_MODE, 1 to 1) else Frame.of(Cmd.TRANSFER_MODE)
    fun syncAck(field1: Long) = Frame.of(Cmd.SYNC_ACK, 1 to Cmd.SYNC_REQ, 2 to field1)

    /**
     * Acerto de hora. Observado: {1 ano, 2 mês, 3 dia, 4 hora, 5 min, 6 seg, 7:1, 8:2, 9: dia da semana (seg=0)}
     * Os campos 7 e 8 foram 1 e 2 no log (provável formato 24h / idioma) - mantidos iguais.
     */
    fun setTime(c: Calendar = Calendar.getInstance()): Frame {
        val dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7   // domingo(1)->6, segunda(2)->0
        return Frame.of(
            Cmd.SET_TIME,
            1 to c.get(Calendar.YEAR), 2 to c.get(Calendar.MONTH) + 1, 3 to c.get(Calendar.DAY_OF_MONTH),
            4 to c.get(Calendar.HOUR_OF_DAY), 5 to c.get(Calendar.MINUTE), 6 to c.get(Calendar.SECOND),
            7 to 1, 8 to 2, 9 to dow
        )
    }

    /** Notificação. appId 110 = o que o app oficial usou (ícone pode variar; mapear outros ids é tarefa aberta). */
    fun notify(title: String, text: String, appId: Int = 110, whenMs: Long = System.currentTimeMillis()): Frame {
        val ts = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).format(whenMs)
        return Frame.of(Cmd.NOTIFY, 3 to appId, 4 to title.take(64), 5 to text.take(240), 6 to ts)
    }

    fun weather(w: WeatherPayload) = Frame(Cmd.WEATHER, w.encode())
}

/**
 * Comando de clima (0x0014) - reproduz byte a byte o que o app oficial envia (teste WeatherPayloadTest).
 * No mostrador: texto 13 = current + "°C", texto 33 = dia0 lo/hi, imagem tipo 6 = ícone do dia0 (0..15).
 */
data class WeatherPayload(val current: Int, val city: String, val days: List<Day>) {
    data class Day(
        val icon: Int, val lo: Int, val hi: Int, val sunrise: Long, val sunset: Long,
        val extra: Map<Int, Int> = emptyMap()   // campos 8,9,10,11(umidade),15(UV)
    )

    fun encode(): ByteArray {
        val f = mutableListOf<Pair<Int, Any>>(2 to current, 3 to city)
        for (d in days.take(7)) {
            val sub = mutableListOf<Pair<Int, Any>>()
            if (d.icon != 0) sub += 1 to d.icon
            sub += listOf(3 to d.lo, 4 to d.hi, 5 to d.sunrise, 6 to d.sunset)
            for (k in listOf(8, 9, 10, 11, 15)) d.extra[k]?.let { sub += k to it }
            f += 4 to Protobuf.encode(sub)
        }
        return Protobuf.encode(f)
    }
}

/**
 * Máquina de estados do envio de mostrador (canal 9901/9902). Pura, sem Android -> testável.
 *  app  -> 9901: 9001 {1:7, 3:size, 4:size, 5:crc, 6:F6, 7:2048, 8:1, 9:1}
 *  watch-> 9901: 9000 {1:9001}            = aceito, mande o bloco 0
 *  watch-> 9901: 9000 {1:9001,2:3,3:off}  = mande o bloco a partir de 'off'
 *  watch-> 9901: 9000 {1:9001,2:5}        = sucesso
 *  watch-> 9901: 9000 {1:9001,2:4}        = arquivo recusado (CRC / validação)
 * Cada bloco de 2048 bytes vai em pacotes de (MTU-3) bytes, write-without-response, na 9902.
 */
class FaceUpload(
    val file: ByteArray,
    val field5: Long = Crc16.faceField5(file),
    val field6: Long = 111149056L,
    val chunk: Int = 2048,
) {
    sealed class Action {
        data class SendBlock(val offset: Int, val data: ByteArray) : Action()
        object Done : Action()
        data class Failed(val code: Long, val reason: String) : Action()
        object Ignore : Action()
    }

    fun startFrame() = Frame.of(
        Cmd.FILE_START, 1 to 7, 3 to file.size, 4 to file.size, 5 to field5, 6 to field6,
        7 to chunk, 8 to 1, 9 to 1
    )

    fun onStatus(fr: Frame): Action {
        if (fr.cmd != Cmd.FILE_STATUS) return Action.Ignore
        val f = fr.fields
        return when (val st = f.int(2)) {
            null -> block(0)
            3L -> block((f.int(3) ?: 0L).toInt())
            5L -> Action.Done
            4L -> Action.Failed(4, "checksum/validação do arquivo falhou")
            else -> Action.Failed(st, "código desconhecido")
        }
    }

    private fun block(off: Int): Action =
        if (off >= file.size) Action.Ignore
        else Action.SendBlock(off, file.copyOfRange(off, minOf(off + chunk, file.size)))

    fun progress(off: Int) = (off * 100L / file.size).toInt()
}
