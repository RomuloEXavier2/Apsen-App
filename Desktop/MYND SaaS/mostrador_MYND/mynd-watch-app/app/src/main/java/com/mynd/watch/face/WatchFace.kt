package com.mynd.watch.face

import android.graphics.Bitmap
import android.graphics.Color

val TEXT_NAMES = mapOf(
    1 to "Hora D", 2 to "Hora U", 3 to "Min D", 4 to "Min U",
    5 to "Batimento (BPM)", 13 to "Temperatura °C", 14 to "Passos",
    16 to "Texto fixo", 17 to "Calorias", 18 to "Bateria %",
    21 to "Bateria (nº)", 22 to "Segundos", 23 to "Distância",
    24 to "Progresso °", 28 to "Dia semana", 29 to "Dia semana longo",
    30 to "Minutos", 31 to "AM/PM", 32 to "Horas",
    33 to "H/Min restantes", 34 to "Estado"
)

data class TextElem(
    val offset: Int, val tipo: Int,
    val x: Int, val y: Int, val largura: Int,
    val r: Int, val g: Int, val b: Int,
    val alignment: Int
) {
    val name: String get() = TEXT_NAMES[tipo] ?: "Tipo $tipo"
}

class WatchFace(val raw: ByteArray, val name: String) {
    companion object {
        const val TABLE    = 0xC0
        const val IMG_SZ   = 0x84
        const val TXT_SZ   = 0x20
        const val PREV_OFF = 0x5E8
        const val PREV_W   = 156
        const val PREV_H   = 214
        const val BG_OFF   = 0x10AB8
        const val BG_W     = 240
        const val BG_H     = 296
    }

    val textElems: List<TextElem> = run {
        val list = mutableListOf<TextElem>()
        var off = TABLE
        while (off < raw.size && raw[off] == 0x06.toByte()) off += IMG_SZ
        while (off + TXT_SZ <= PREV_OFF && off < raw.size && raw[off] == 0x05.toByte()) {
            list += TextElem(off, raw[off+1].u, raw.u16le(off+2), raw.u16le(off+4),
                raw[off+6].u, raw[off+7].u, raw[off+8].u, raw[off+9].u, raw[off+10].u)
            off += TXT_SZ
        }
        list
    }

    fun build(elems: List<TextElem>, customBg: Bitmap? = null): ByteArray {
        val out = raw.copyOf()
        for (el in elems) {
            out.putU16le(el.offset + 2, el.x); out.putU16le(el.offset + 4, el.y)
            out[el.offset + 7] = el.r.toByte(); out[el.offset + 8] = el.g.toByte()
            out[el.offset + 9] = el.b.toByte(); out[el.offset + 10] = el.alignment.toByte()
        }
        if (customBg != null) {
            val scaled = Bitmap.createScaledBitmap(customBg, BG_W, BG_H, true)
            var idx = BG_OFF + 4
            for (y in 0 until BG_H) for (x in 0 until BG_W) {
                val px = scaled.getPixel(x, y)
                val v = ((Color.red(px) shr 3) shl 11) or ((Color.green(px) shr 2) shl 5) or (Color.blue(px) shr 3)
                out[idx++] = (v shr 8).toByte(); out[idx++] = (v and 0xFF).toByte()
            }
            scaled.recycle()
        }
        return out
    }

    fun decodeBg(): Bitmap = decodeRgb565(BG_OFF + 4, BG_W, BG_H)
    fun decodePreview(): Bitmap = decodeRgb565(PREV_OFF, PREV_W, PREV_H)

    private fun decodeRgb565(start: Int, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        var i = start
        for (y in 0 until h) for (x in 0 until w) {
            val v = (raw[i++].u shl 8) or raw[i++].u
            bmp.setPixel(x, y, Color.rgb(((v shr 11) and 31)*255/31, ((v shr 5) and 63)*255/63, (v and 31)*255/31))
        }
        return bmp
    }
}

private val Byte.u: Int get() = toInt() and 0xFF
private fun ByteArray.u16le(i: Int) = this[i].u or (this[i+1].u shl 8)
private fun ByteArray.putU16le(i: Int, v: Int) { this[i] = (v and 0xFF).toByte(); this[i+1] = (v shr 8).toByte() }
