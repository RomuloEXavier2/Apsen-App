package com.mynd.watch.core

import com.mynd.watch.bambu.PrintEvents
import com.mynd.watch.bambu.PrintState
import com.mynd.watch.core.Protobuf.int
import com.mynd.watch.core.Protobuf.text
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Vetores de teste tirados do btsnoop real (app oficial Haylou -> relógio).
 * Se estes testes passam, o app fala o protocolo exatamente como o app oficial.
 */
class ProtocolTest {

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes()

    @Test fun framesIdenticosAoLog() {
        assertArrayEquals(hex("ea040002000803ef"), WatchCommands.getInfo().toBytes())
        assertArrayEquals(hex("ea040034000801ef"), WatchCommands.transferMode(true).toBytes())
        assertArrayEquals(hex("ea02003400ef"), WatchCommands.transferMode(false).toBytes())
        assertArrayEquals(hex("ea0700b80b08bb171003ef"), WatchCommands.syncAck(3).toBytes())
        // início do envio do mostrador "glitch" (xfer0) exatamente como no log
        val start = Frame.of(Cmd.FILE_START, 1 to 7, 3 to 268388, 4 to 268388, 5 to 80100118L,
            6 to 111149056L, 7 to 2048, 8 to 1, 9 to 1)
        assertArrayEquals(hex("ea1d002923080718e4b01020e4b0102896f69826308080803538801040014801ef"), start.toBytes())
    }

    @Test fun climaReproduzOAppOficial() {
        val cap = res("weather_frame_app_oficial.bin")
        val fr = Deframer().feed(cap).single()
        assertEquals(Cmd.WEATHER, fr.cmd)
        val f = fr.fields
        val days = f.filter { it.num == 4 }.map {
            val d = Protobuf.decode((it.value as PbValue.Bytes).v)
            val extra = listOf(8, 9, 10, 11, 15).mapNotNull { k -> d.int(k)?.let { k to it.toInt() } }.toMap()
            WeatherPayload.Day((d.int(1) ?: 0).toInt(), d.int(3)!!.toInt(), d.int(4)!!.toInt(), d.int(5)!!, d.int(6)!!, extra)
        }
        val rebuilt = WatchCommands.weather(WeatherPayload(f.int(2)!!.toInt(), f.text(3)!!, days))
        assertArrayEquals(cap, rebuilt.toBytes())
    }

    @Test fun deframerRemontaPacotesDe20Bytes() {
        val cap = res("weather_frame_app_oficial.bin")
        val d = Deframer(); val got = mutableListOf<Frame>()
        cap.toList().chunked(20).forEach { got += d.feed(it.toByteArray()) }
        assertEquals(1, got.size); assertArrayEquals(cap, got[0].toBytes())
    }

    @Test fun crcDoMostradorOficial() {
        val planeta = res("planeta_oficial.bin")
        assertEquals(176202336L, Crc16.faceField5(planeta, 0x0A80))   // valor do log
    }

    @Test fun envioDeMostradorSimulado() {
        val file = ByteArray(5000) { (it * 7).toByte() }
        val up = FaceUpload(file)
        val rx = java.io.ByteArrayOutputStream()
        var a = up.onStatus(Frame.of(Cmd.FILE_STATUS, 1 to 9001))
        while (a is FaceUpload.Action.SendBlock) {
            rx.write(a.data)
            val next = a.offset + a.data.size
            a = up.onStatus(if (next >= file.size) Frame.of(Cmd.FILE_STATUS, 1 to 9001, 2 to 5)
                            else Frame.of(Cmd.FILE_STATUS, 1 to 9001, 2 to 3, 3 to next))
        }
        assertTrue(a is FaceUpload.Action.Done)
        assertArrayEquals(file, rx.toByteArray())
        assertTrue(up.onStatus(Frame.of(Cmd.FILE_STATUS, 1 to 9001, 2 to 4)) is FaceUpload.Action.Failed)
    }

    @Test fun horaNoFormatoDoLog() {
        val c = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 24, 14, 18, 2) }
        val f = WatchCommands.setTime(c).fields
        assertEquals(listOf(2026L, 9L, 24L, 14L, 18L, 2L, 1L, 2L, 3L), (1..9).map { f.int(it) })
    }

    @Test fun contratoMostradorBambuV4() {
        val st = PrintState()
        st.merge("""{"print":{"gcode_state":"RUNNING","mc_percent":40,"mc_remaining_time":844,"nozzle_temper":219.6}}""")
        val f = com.mynd.watch.bambu.BambuWeatherMapper.toWeather(st).fields
        val d0 = Protobuf.decode((f.first { it.num == 4 }.value as PbValue.Bytes).v)
        assertEquals(40L, f.int(2))             // texto 24 (painel IMPRESSÃO) -> "40"
        assertEquals(14L, d0.int(3))            // texto 33 -> "14/4"
        assertEquals(4L, d0.int(4))
        assertEquals(10L, d0.int(1))            // bico 219.6 -> nível 220 (índice 10)
        assertEquals(0, com.mynd.watch.bambu.BambuWeatherMapper.nozzleIndex(25))
        assertEquals(13, com.mynd.watch.bambu.BambuWeatherMapper.nozzleIndex(241))
    }

    @Test fun eventosDaImpressora() {
        val st = PrintState(); val ev = PrintEvents(); val out = mutableListOf<Pair<String, String>>()
        listOf(
            """{"print":{"gcode_state":"IDLE","mc_percent":0}}""",
            """{"print":{"gcode_state":"RUNNING","mc_percent":1,"subtask_name":"suporte.3mf","mc_remaining_time":95}}""",
            """{"print":{"mc_percent":26}}""",
            """{"print":{"mc_percent":52}}""",
            """{"print":{"gcode_state":"PAUSE"}}""",
            """{"print":{"gcode_state":"RUNNING","mc_percent":77}}""",
            """{"print":{"mc_percent":91,"mc_remaining_time":7}}""",
            """{"print":{"gcode_state":"FINISH","mc_percent":100}}""",
        ).forEach { st.merge(it); out += ev.check(st) }
        assertEquals(listOf(
            "Bambu: iniciou" to "suporte", "Bambu 25%" to "Faltam 1h35", "Bambu 50%" to "Faltam 1h35",
            "Bambu: PAUSADA" to "suporte em 52%", "Bambu 75%" to "Faltam 1h35", "Bambu 90%" to "Faltam 0h07",
            "Bambu: concluída" to "suporte"), out)
    }
}
