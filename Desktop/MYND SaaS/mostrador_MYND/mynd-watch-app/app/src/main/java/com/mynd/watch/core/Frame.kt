package com.mynd.watch.core

import java.io.ByteArrayOutputStream

/**
 * Quadro do protocolo:  EA | len u16 LE (= 2 + payload) | cmd u16 LE | payload (protobuf) | EF
 * Ex.: EA 0400 0200 0803 EF  = cmd 0x0002, payload {1:3}
 */
data class Frame(val cmd: Int, val payload: ByteArray) {

    val fields: List<PbField> get() = Protobuf.decode(payload)

    fun toBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        val len = payload.size + 2
        out.write(0xEA)
        out.write(len and 0xFF); out.write((len shr 8) and 0xFF)
        out.write(cmd and 0xFF); out.write((cmd shr 8) and 0xFF)
        out.write(payload)
        out.write(0xEF)
        return out.toByteArray()
    }

    companion object {
        fun of(cmd: Int, vararg fields: Pair<Int, Any>) = Frame(cmd, Protobuf.encode(fields.toList()))
    }

    override fun equals(other: Any?) = other is Frame && other.cmd == cmd && other.payload.contentEquals(payload)
    override fun hashCode() = cmd * 31 + payload.contentHashCode()
    override fun toString() = "Frame(0x%04X, %s)".format(cmd, runCatching { fields }.getOrNull())
}

/** Remonta quadros que chegam fatiados em várias notificações BLE (um por característica). */
class Deframer {
    private val buf = ByteArrayOutputStream()

    fun feed(data: ByteArray): List<Frame> {
        if (buf.size() == 0 && (data.isEmpty() || (data[0].toInt() and 0xFF) != 0xEA)) return emptyList()
        buf.write(data)
        val frames = mutableListOf<Frame>()
        var b = buf.toByteArray()
        while (b.size >= 3) {
            val n = (b[1].toInt() and 0xFF) or ((b[2].toInt() and 0xFF) shl 8)
            if (b.size < n + 4) break
            val cmd = (b[3].toInt() and 0xFF) or ((b[4].toInt() and 0xFF) shl 8)
            frames += Frame(cmd, b.copyOfRange(5, n + 3))
            b = b.copyOfRange(n + 4, b.size)
        }
        buf.reset(); buf.write(b)
        return frames
    }
}
