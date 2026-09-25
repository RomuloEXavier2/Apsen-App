package com.mynd.watch.core

import java.io.ByteArrayOutputStream

/**
 * Protobuf mínimo (só varint e length-delimited), suficiente para o protocolo do Haylou S6.
 * Espelha pb_encode/pb_decode de reference/python/haylou_s6.py.
 */
sealed class PbValue {
    data class VarInt(val v: Long) : PbValue()
    class Bytes(val v: ByteArray) : PbValue() {
        fun text(): String = v.toString(Charsets.UTF_8)
        override fun toString() = "Bytes(${text()})"
    }
}

data class PbField(val num: Int, val value: PbValue)

object Protobuf {

    fun encode(fields: List<Pair<Int, Any>>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((num, v) in fields) {
            when (v) {
                is Int -> { writeVarint(out, (num shl 3).toLong()); writeVarint(out, v.toLong()) }
                is Long -> { writeVarint(out, (num shl 3).toLong()); writeVarint(out, v) }
                is String -> writeBytes(out, num, v.toByteArray(Charsets.UTF_8))
                is ByteArray -> writeBytes(out, num, v)
                else -> error("tipo não suportado: ${v::class}")
            }
        }
        return out.toByteArray()
    }

    private fun writeBytes(out: ByteArrayOutputStream, num: Int, b: ByteArray) {
        writeVarint(out, ((num shl 3) or 2).toLong())
        writeVarint(out, b.size.toLong())
        out.write(b)
    }

    fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var n = value
        while (true) {
            val b = (n and 0x7F).toInt()
            n = n ushr 7
            if (n != 0L) out.write(b or 0x80) else { out.write(b); return }
        }
    }

    fun decode(b: ByteArray): List<PbField> {
        val out = mutableListOf<PbField>()
        var i = 0
        fun readVarint(): Long {
            var v = 0L; var s = 0
            while (true) {
                val x = b[i++].toInt() and 0xFF
                v = v or ((x and 0x7F).toLong() shl s); s += 7
                if (x < 0x80) return v
            }
        }
        while (i < b.size) {
            val k = readVarint()
            val num = (k ushr 3).toInt()
            when ((k and 7).toInt()) {
                0 -> out += PbField(num, PbValue.VarInt(readVarint()))
                2 -> {
                    val n = readVarint().toInt()
                    out += PbField(num, PbValue.Bytes(b.copyOfRange(i, i + n))); i += n
                }
                else -> return out
            }
        }
        return out
    }

    fun List<PbField>.int(num: Int): Long? = firstOrNull { it.num == num }?.value.let { (it as? PbValue.VarInt)?.v }
    fun List<PbField>.text(num: Int): String? = firstOrNull { it.num == num }?.value.let { (it as? PbValue.Bytes)?.text() }
}
