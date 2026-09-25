package com.mynd.watch.studio

/**
 * Simulador: desenha o mostrador como o firmware faz.
 *  1) fundo  2) elementos de imagem (índice escolhido pelos valores simulados)  3) campos de texto.
 * Os pixels (fundo+imagens) saem prontos em ARGB. Os textos saem como lista de TextDraw, que a UI desenha
 * com a fonte de assets/studio/fonts/firmware_like.ttf RECORTANDO na largura do campo (igual ao relógio).
 */
data class SimValues(
    val hour: Int = 10, val minute: Int = 47, val second: Int = 32,
    val year: Int = 2026, val month: Int = 6, val day: Int = 28, val weekday: Int = 6,   // 0=DOM..6=SÁB
    val steps: Int = 7420, val kcal: Int = 328, val bpm: Int = 72, val battery: Int = 78, val distanceKm: Double = 5.2,
    val weather: WeatherChannels = WeatherChannels(34, 14, 4, 10),
    val h24: Boolean = true,
)

data class TextDraw(val x: Int, val y: Int, val width: Int, val align: Int, val argb: Int, val text: String, val type: Int)

class RenderResult(val pixels: IntArray, val texts: List<TextDraw>) {
    val width get() = JL.W
    val height get() = JL.H
}

object FaceRenderer {
    private val MON = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val DOW = listOf("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")
    private val DESC = mapOf(0 to "Ensolarado", 1 to "Nublado", 2 to "Nublado", 3 to "Chuva", 5 to "Tempestade")

    fun imageIndex(type: Int, v: SimValues): Int {
        val h = if (v.h24) v.hour else ((v.hour + 11) % 12 + 1)
        return when (type) {
            1 -> h / 10; 2 -> h % 10; 3 -> v.minute / 10; 4 -> v.minute % 10
            6 -> v.weather.icon; 8 -> v.month / 10; 9 -> v.month % 10; 10 -> v.day / 10; 11 -> v.day % 10
            23 -> (v.battery / 20).coerceIn(0, 5); 24 -> if (v.hour >= 12) 1 else 0; 31 -> v.weekday
            else -> 0
        }
    }

    fun textValue(t: TextElem, v: SimValues): String {
        val h = if (v.h24) v.hour else ((v.hour + 11) % 12 + 1)
        return when (t.type) {
            1 -> "${h / 10}"; 2 -> "${h % 10}"; 3 -> "${v.minute / 10}"; 4 -> "${v.minute % 10}"
            5 -> "${v.bpm}"; 6 -> "${v.year}"; 7 -> "%02d".format(v.month); 8 -> "%02d".format(v.day)
            9 -> DOW[v.weekday].take(3); 13 -> "${v.weather.current}°C"; 14 -> "${v.steps}"; 16 -> "0"
            17 -> "${v.kcal}"; 18 -> "${v.battery}%"; 19 -> t.fixedText; 21 -> "${v.battery}"
            22 -> "%02d".format(v.second); 23 -> "%.2f".format(java.util.Locale.US, v.distanceKm); 24 -> "${v.weather.current}°"
            25 -> MON[v.month - 1].take(3); 26 -> MON[v.month - 1]; 27 -> MON[v.month - 1].take(3).uppercase()
            28 -> DOW[v.weekday].take(3).lowercase().replaceFirstChar { it.uppercase() }; 29 -> DOW[v.weekday]
            30 -> "%02d".format(v.minute); 31 -> if (v.hour >= 12) "PM" else "AM"; 32 -> "%02d".format(h)
            33 -> "${v.weather.lo}/${v.weather.hi}°C"; 34 -> DESC[v.weather.icon] ?: "Clima"
            else -> "Text"   // o firmware escreve "Text" para tipo inexistente
        }
    }

    fun render(f: FaceFile, v: SimValues = SimValues()): RenderResult {
        val px = Rgb565.decode(f.bg, JL.W, JL.H)
        for (im in f.images) {
            val s = im.sprites.getOrNull(imageIndex(im.type, v).coerceIn(0, im.sprites.size - 1)) ?: continue
            blit(px, s, im.x, im.y)
        }
        val texts = f.texts.map { TextDraw(it.x, it.y, it.width, it.align, argb(255, it.r, it.g, it.b), textValue(it, v), it.type) }
        return RenderResult(px, texts)
    }

    /** Compõe um sprite (cf5 pré-multiplicado ou cf4 opaco) sobre o fundo. */
    fun blit(dst: IntArray, s: Sprite, x0: Int, y0: Int) {
        for (y in 0 until s.h) {
            val dy = y0 + y; if (dy !in 0 until JL.H) continue
            for (x in 0 until s.w) {
                val dx = x0 + x; if (dx !in 0 until JL.W) continue
                val di = dy * JL.W + dx
                if (s.cf == 5) {
                    val o = (y * s.w + x) * 3; val a = s.data[o].u; if (a == 0) continue
                    val (r, g, b) = rgb565((s.data[o + 1].u shl 8) or s.data[o + 2].u)   // já pré-multiplicado
                    val d = dst[di]; val ia = 255 - a
                    dst[di] = argb(255, minOf(255, r + (d shr 16 and 255) * ia / 255),
                        minOf(255, g + (d shr 8 and 255) * ia / 255), minOf(255, b + (d and 255) * ia / 255))
                } else {
                    val o = (y * s.w + x) * 2; val (r, g, b) = rgb565((s.data[o].u shl 8) or s.data[o + 1].u)
                    dst[di] = argb(255, r, g, b)
                }
            }
        }
    }

    /** Miniatura 156x214 (prévia exibida na lista do relógio), cantos arredondados em preto. */
    fun previewFrom(full: IntArray): ByteArray {
        val out = IntArray(JL.PREV_W * JL.PREV_H)
        for (y in 0 until JL.PREV_H) for (x in 0 until JL.PREV_W) {
            // amostragem por área simples
            val sx0 = x * JL.W / JL.PREV_W; val sx1 = maxOf(sx0 + 1, (x + 1) * JL.W / JL.PREV_W)
            val sy0 = y * JL.H / JL.PREV_H; val sy1 = maxOf(sy0 + 1, (y + 1) * JL.H / JL.PREV_H)
            var r = 0; var g = 0; var b = 0; var n = 0
            for (yy in sy0 until sy1) for (xx in sx0 until sx1) { val p = full[yy * JL.W + xx]; r += p shr 16 and 255; g += p shr 8 and 255; b += p and 255; n++ }
            out[y * JL.PREV_W + x] = if (inRounded(x, y, JL.PREV_W, JL.PREV_H, 18)) argb(255, r / n, g / n, b / n) else argb(255, 0, 0, 0)
        }
        return Rgb565.encode(out)
    }

    private fun inRounded(x: Int, y: Int, w: Int, h: Int, r: Int): Boolean {
        val cx = if (x < r) r else if (x >= w - r) w - r - 1 else x
        val cy = if (y < r) r else if (y >= h - r) h - r - 1 else y
        val dx = x - cx; val dy = y - cy
        return dx * dx + dy * dy <= r * r
    }
}
