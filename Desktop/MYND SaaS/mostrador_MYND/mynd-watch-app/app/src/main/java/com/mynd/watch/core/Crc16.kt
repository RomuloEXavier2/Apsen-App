package com.mynd.watch.core

/**
 * CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF, sem reflexão, xorout 0).
 * Usado no campo 5 do comando 9001 (envio de mostrador):
 *   campo5 = (FACE_ID shl 16) or crc16(arquivo inteiro)
 * Validado nos 4 mostradores oficiais capturados (ver reference/testvectors).
 */
object Crc16 {
    private val table = IntArray(256) { i ->
        var c = i shl 8
        repeat(8) { c = if (c and 0x8000 != 0) (c shl 1) xor 0x1021 else c shl 1 }
        c and 0xFFFF
    }

    fun ccittFalse(data: ByteArray, init: Int = 0xFFFF): Int {
        var crc = init
        for (b in data) crc = ((crc shl 8) and 0xFFFF) xor table[((crc ushr 8) xor (b.toInt() and 0xFF)) and 0xFF]
        return crc
    }

    /** 12 bits altos: ID do mostrador. 0x0A80 é o do modelo "planeta" e já foi aceito pelo relógio. */
    const val DEFAULT_FACE_ID = 0x0A80

    fun faceField5(file: ByteArray, faceId: Int = DEFAULT_FACE_ID): Long =
        (faceId.toLong() shl 16) or ccittFalse(file).toLong()
}
