package com.mynd.watch.studio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Testes do núcleo Studio. Mesmos casos já validados com kotlinc 2.4 contra os mostradores reais. */
class StudioTest {
    private fun res(n: String) = javaClass.classLoader!!.getResourceAsStream(n)!!.readBytes()
    private val bambu = File("src/main/assets/faces/MYND_HUD_BAMBU.bin").readBytes()
    private val hud = File("src/main/assets/faces/MYND_HUD.bin").readBytes()

    @Test fun roundtripByteAByte() {
        for (b in listOf(res("planeta_oficial.bin"), bambu, hud)) assertArrayEquals(b, FaceFile.parse(b).build())
    }

    @Test fun spriteIgualAoPython() {
        val bb = res("sprite_argb.bin")
        val px = IntArray(bb.size / 4) { i -> ((bb[i*4].toInt() and 255) shl 24) or ((bb[i*4+1].toInt() and 255) shl 16) or ((bb[i*4+2].toInt() and 255) shl 8) or (bb[i*4+3].toInt() and 255) }
        val s = Sprite.fromArgb(42, 20, px)
        assertEquals(44, s.w)                       // largura completada p/ múltiplo de 4
        assertArrayEquals(res("sprite_vec.bin"), s.toBytes())
    }

    @Test fun validador() {
        val f = FaceFile.parse(bambu)
        assertFalse(FaceValidator.hasErrors(FaceValidator.check(f, DataContract.BAMBU_V4)))
        val tipoRuim = f.copy(texts = f.texts.map { if (it.type == 33) it.copy(type = 36) else it })
        assertTrue(FaceValidator.hasErrors(FaceValidator.check(tipoRuim)))
        val larguraRuim = f.copy(images = f.images.map { if (it.type == 2) it.copy(sprites = it.sprites.map { s -> Sprite(5, 42, s.h, 0, ByteArray(42 * s.h * 3)) }) else it })
        assertTrue(FaceValidator.check(larguraRuim).any { "múltiplo de 4" in it.msg })
    }

    @Test fun contratoV4() {
        val ch = ContractMapper.channels(DataContract.BAMBU_V4, PrintValues(percent = 34, remainingMin = 844, nozzle = 220))
        assertEquals(WeatherChannels(34, 14, 4, 10), ch)
        assertEquals(DataContract.BAMBU_V4, DataContract.parse(DataContract.BAMBU_V4.serialize()))
        val t = FaceRenderer.render(FaceFile.parse(bambu), SimValues(weather = ch)).texts.associate { it.type to it.text }
        assertEquals("34°", t[24]); assertEquals("14/4°C", t[33])
    }

    @Test fun contratoGeraMesmoClimaQueMapperV4() {
        val now = java.util.Calendar.getInstance()
        val st = com.mynd.watch.bambu.PrintState()
        st.merge("""{"print":{"gcode_state":"RUNNING","mc_percent":34,"mc_remaining_time":844,"nozzle_temper":219.6,"bed_temper":64.8}}""")
        assertArrayEquals(com.mynd.watch.bambu.BambuWeatherMapper.toWeather(st, now).toBytes(),
            ContractWeather.build(DataContract.BAMBU_V4, st, now)!!.toBytes())
        assertEquals(null, ContractWeather.build(DataContract.REAL_WEATHER, st, now))
    }

    @Test fun biblioteca() {
        val root = File("build/tmp/libtest").apply { deleteRecursively() }
        val lib = FaceLibrary(root)
        val r1 = lib.save("MYND HUD Bambu", bambu, "v4", DataContract.BAMBU_V4)
        assertEquals(1, lib.save("MYND HUD Bambu", bambu, "igual").v)      // não duplica
        val nf = FaceComposer.withBackground(FaceFile.parse(bambu), IntArray(JL.W * JL.H) { argb(255, 10, 20, 30) })
        val r2 = lib.save("MYND HUD Bambu", nf.build(), "fundo novo")
        assertEquals(2, r2.v)
        lib.markApproved(r1); lib.markInstalled(r2)
        assertEquals(r1, lib.lastApproved()); assertEquals(r2, lib.installed())
        assertArrayEquals(bambu, lib.bytes(r1))
        val zip = java.io.ByteArrayOutputStream(); lib.exportZip(zip)
        val lib2 = FaceLibrary(File("build/tmp/libtest2").apply { deleteRecursively() }); lib2.importZip(zip.toByteArray().inputStream())
        assertEquals(2, lib2.get("mynd_hud_bambu")!!.versions.size)
    }
}
