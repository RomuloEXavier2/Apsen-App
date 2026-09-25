package com.mynd.watch.studio

import java.security.MessageDigest

/**
 * Leitura/escrita COMPLETA do mostrador .bin "JL" do Haylou Watch S6 (240x296).
 * Port fiel de reference/python/jlface.py — reconstrói os mostradores oficiais byte a byte (ver StudioTest).
 * Código puro (sem Android) -> testável na JVM.
 */
object JL {
    const val W = 240
    const val H = 296
    const val TABLE = 0xC0
    const val IMG_REC = 0x84
    const val TXT_REC = 0x20
    const val PREV_OFF = 0x5E8
    const val PREV_W = 156
    const val PREV_H = 214
    const val BG_HDR = 0x10AB8
    const val RES_START = BG_HDR + 4 + W * H * 2   // 0x335BC
    const val BASE = 0x20B4                          // offset na tabela = posição no arquivo - BASE
    const val TABLE_BYTES = PREV_OFF - TABLE         // 1320 bytes para registros
    const val MAX_RECORDS = 0xB4 - 0x8D              // 39 (lista de tipos no cabeçalho)
    val TEXT_TAIL: ByteArray = "1000200000".toByteArray() + ByteArray(10)
}

/** Imagem de recurso: cf 4 = RGB565 BE opaco (2 B/px); cf 5 = A8 + RGB565 BE pré-multiplicado (3 B/px). */
class Sprite(val cf: Int, val w: Int, val h: Int, val extra: Int, val data: ByteArray) {
    val bpp get() = if (cf == 5) 3 else 2
    fun toBytes(): ByteArray = header(cf, w, h, extra) + data
    val rowBytes get() = w * bpp

    /** Pixels ARGB (não pré-multiplicados) para desenhar/compor. */
    fun argb(): IntArray = IntArray(w * h) { i ->
        if (cf == 5) {
            val a = data[i * 3].u; val c = (data[i * 3 + 1].u shl 8) or data[i * 3 + 2].u
            val (r, g, b) = rgb565(c)
            if (a == 0) 0 else argb(a, minOf(255, r * 255 / a), minOf(255, g * 255 / a), minOf(255, b * 255 / a))
        } else {
            val c = (data[i * 2].u shl 8) or data[i * 2 + 1].u
            val (r, g, b) = rgb565(c); argb(255, r, g, b)
        }
    }

    companion object {
        fun header(cf: Int, w: Int, h: Int, extra: Int = 0): ByteArray {
            val v = cf or (extra shl 5) or (w shl 10) or (h shl 21)
            return byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        }

        fun parse(b: ByteArray, off: Int): Sprite {
            val v = (b[off].u) or (b[off + 1].u shl 8) or (b[off + 2].u shl 16) or (b[off + 3].u shl 24)
            val cf = v and 0x1F; val extra = (v shr 5) and 0x1F
            val w = (v shr 10) and 0x7FF; val h = (v shr 21) and 0x7FF
            val n = w * h * (if (cf == 5) 3 else 2)
            return Sprite(cf, w, h, extra, b.copyOfRange(off + 4, off + 4 + n))
        }

        /**
         * ARGB -> recurso cf 5. REGRA DO FIRMWARE: bytes por linha múltiplo de 4
         * -> a largura é completada com colunas transparentes até múltiplo de 4.
         */
        fun fromArgb(w0: Int, h: Int, px: IntArray): Sprite {
            val w = (w0 + 3) / 4 * 4
            val out = ByteArray(w * h * 3)
            for (y in 0 until h) for (x in 0 until w0) {
                val p = px[y * w0 + x]; val a = p ushr 24
                val r = ((p shr 16 and 255) * a + 127) / 255
                val g = ((p shr 8 and 255) * a + 127) / 255
                val bl = ((p and 255) * a + 127) / 255
                val c = to565(r, g, bl); val o = (y * w + x) * 3
                out[o] = a.toByte(); out[o + 1] = (c shr 8).toByte(); out[o + 2] = c.toByte()
            }
            return Sprite(5, w, h, 0, out)
        }
    }
}

enum class Align(val code: Int) { LEFT(1), CENTER(2), RIGHT(3) ;
    companion object { fun of(c: Int) = when (c) { 2 -> CENTER; 3 -> RIGHT; else -> LEFT } } }

data class ImageElem(val type: Int, val x: Int, val y: Int, val sprites: List<Sprite>)

data class TextElem(
    val type: Int, val x: Int, val y: Int, val width: Int,
    val r: Int, val g: Int, val b: Int, val align: Int = 1,
    val b11: Int = 1, val tail: ByteArray = JL.TEXT_TAIL.copyOf(),
) {
    fun toBytes(): ByteArray {
        val o = ByteArray(JL.TXT_REC)
        o[0] = 5; o[1] = type.toByte(); o.putU16(2, x); o.putU16(4, y); o[6] = width.toByte()
        o[7] = r.toByte(); o[8] = g.toByte(); o[9] = b.toByte(); o[10] = align.toByte(); o[11] = b11.toByte()
        tail.copyInto(o, 12, 0, minOf(20, tail.size))
        return o
    }
    /** Texto fixo do campo tipo 19 (os bytes do próprio registro). */
    val fixedText: String get() = String(tail).trimEnd('\u0000')
}

class FaceFile(
    val head: ByteArray,                 // 0x00..0xC0 (ID, lista de tipos, flags)
    val images: List<ImageElem>,
    val texts: List<TextElem>,
    val preview: ByteArray,              // 156x214 RGB565 BE
    val bgHeader: ByteArray,             // 4 bytes
    val bg: ByteArray,                   // 240x296 RGB565 BE
    val extraTail: ByteArray = ByteArray(0),   // bytes após os registros (normalmente zeros)
) {
    val id: String get() = String(head, 8, 8).trimEnd('\u0000')
    val tableBytes get() = images.size * JL.IMG_REC + texts.size * JL.TXT_REC

    fun copy(
        images: List<ImageElem> = this.images, texts: List<TextElem> = this.texts,
        preview: ByteArray = this.preview, bg: ByteArray = this.bg,
    ) = FaceFile(head.copyOf(), images, texts, preview, bgHeader, bg, extraTail)

    /** Gera o .bin (mesmo algoritmo do Python: recursos em ordem de 1º uso, sem duplicar). */
    fun build(): ByteArray {
        require(images.size + texts.size <= JL.MAX_RECORDS) { "muitos registros" }
        require(tableBytes <= JL.TABLE_BYTES) { "tabela cheia: $tableBytes > ${JL.TABLE_BYTES}" }
        val out = ByteArray(JL.RES_START)
        val h = head.copyOf()
        h.fill(0, 0x8C, 0xB4); h[0x8C] = 1
        var k = 0x8D
        repeat(images.size) { h[k++] = 6 }
        repeat(texts.size) { h[k++] = 5 }
        h.copyInto(out, 0)
        val res = java.io.ByteArrayOutputStream()
        val placed = HashMap<String, Int>()
        var o = JL.TABLE
        for (im in images) {
            out[o] = 6; out[o + 1] = im.type.toByte(); out.putU16(o + 2, im.x); out.putU16(o + 4, im.y)
            im.sprites.forEachIndexed { i, s ->
                val bytes = s.toBytes(); val key = sha(bytes)
                val off = placed.getOrPut(key) { val p = JL.RES_START + res.size() - JL.BASE; res.write(bytes); p }
                out.putU32(o + 8 + 4 * i, off)
            }
            o += JL.IMG_REC
        }
        for (t in texts) { t.toBytes().copyInto(out, o); o += JL.TXT_REC }
        preview.copyInto(out, JL.PREV_OFF)
        bgHeader.copyInto(out, JL.BG_HDR)
        bg.copyInto(out, JL.BG_HDR + 4)
        return out + res.toByteArray()
    }

    companion object {
        fun parse(d: ByteArray): FaceFile {
            require(d.size > JL.RES_START && d[14] == 'J'.code.toByte() && d[15] == 'L'.code.toByte()) { "não é um mostrador JL" }
            val images = mutableListOf<ImageElem>(); val texts = mutableListOf<TextElem>()
            var o = JL.TABLE
            while (d[o].toInt() == 6) {
                val offs = (0 until 31).map { d.u32(o + 8 + 4 * it) }.filter { it != 0 }
                images += ImageElem(d[o + 1].u, d.u16(o + 2), d.u16(o + 4), offs.map { Sprite.parse(d, it + JL.BASE) })
                o += JL.IMG_REC
            }
            while (o + JL.TXT_REC <= JL.PREV_OFF && d[o].toInt() == 5) {
                texts += TextElem(d[o + 1].u, d.u16(o + 2), d.u16(o + 4), d[o + 6].u, d[o + 7].u, d[o + 8].u, d[o + 9].u,
                    d[o + 10].u, d[o + 11].u, d.copyOfRange(o + 12, o + 32))
                o += JL.TXT_REC
            }
            return FaceFile(
                d.copyOfRange(0, JL.TABLE), images, texts,
                d.copyOfRange(JL.PREV_OFF, JL.BG_HDR), d.copyOfRange(JL.BG_HDR, JL.BG_HDR + 4),
                d.copyOfRange(JL.BG_HDR + 4, JL.RES_START), d.copyOfRange(o, JL.PREV_OFF),
            )
        }

        fun sha(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    }
}

// ---------------- conversões RGB565 ----------------
fun rgb565(c: Int) = Triple(((c shr 11) and 31) * 255 / 31, ((c shr 5) and 63) * 255 / 63, (c and 31) * 255 / 31)
fun to565(r: Int, g: Int, b: Int) = ((r shr 3) shl 11) or ((g shr 2) shl 5) or (b shr 3)
fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

object Rgb565 {
    fun decode(b: ByteArray, w: Int, h: Int, off: Int = 0) = IntArray(w * h) { i ->
        val c = (b[off + i * 2].u shl 8) or b[off + i * 2 + 1].u; val (r, g, bl) = rgb565(c); argb(255, r, g, bl)
    }
    fun encode(px: IntArray): ByteArray {
        val o = ByteArray(px.size * 2)
        for (i in px.indices) { val p = px[i]; val c = to565(p shr 16 and 255, p shr 8 and 255, p and 255); o[i * 2] = (c shr 8).toByte(); o[i * 2 + 1] = c.toByte() }
        return o
    }
}

internal val Byte.u: Int get() = toInt() and 0xFF
internal fun ByteArray.u16(i: Int) = this[i].u or (this[i + 1].u shl 8)
internal fun ByteArray.u32(i: Int) = this[i].u or (this[i + 1].u shl 8) or (this[i + 2].u shl 16) or (this[i + 3].u shl 24)
internal fun ByteArray.putU16(i: Int, v: Int) { this[i] = v.toByte(); this[i + 1] = (v shr 8).toByte() }
internal fun ByteArray.putU32(i: Int, v: Int) { putU16(i, v and 0xFFFF); putU16(i + 2, v ushr 16) }
