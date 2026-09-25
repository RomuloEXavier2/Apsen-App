package com.mynd.watch.studio

/**
 * Composição do FUNDO em camadas (papel de parede + moldura HUD) e aplicação no mostrador.
 * Entradas e saídas são ARGB 240x296 (IntArray). O recorte/escala da foto é feito na UI (Bitmap) antes.
 *
 *  fundo = moldura  sobre  (papel de parede escurecido + tingido)
 *  A moldura (os PNGs em assets/studio/frames) tem alpha: onde é transparente aparece o papel de parede.
 */
object FaceComposer {

    data class WallpaperFx(val darken: Int = 45, val tint: Int = 0, val tintArgb: Int = argb(255, 0, 200, 255))

    fun compose(wall: IntArray, frame: IntArray?, fx: WallpaperFx = WallpaperFx()): IntArray {
        require(wall.size == JL.W * JL.H)
        val k = (100 - fx.darken.coerceIn(0, 100)) / 100.0
        val t = fx.tint.coerceIn(0, 100) / 100.0
        val tr = fx.tintArgb shr 16 and 255; val tg = fx.tintArgb shr 8 and 255; val tb = fx.tintArgb and 255
        val out = IntArray(wall.size)
        for (i in wall.indices) {
            val p = wall[i]
            var r = (p shr 16 and 255); var g = (p shr 8 and 255); var b = (p and 255)
            if (t > 0) { val l = (r * 0.3 + g * 0.59 + b * 0.11) / 255.0; r = (r * (1 - t) + tr * l * t).toInt(); g = (g * (1 - t) + tg * l * t).toInt(); b = (b * (1 - t) + tb * l * t).toInt() }
            r = (r * k).toInt(); g = (g * k).toInt(); b = (b * k).toInt()
            if (frame != null) {
                val q = frame[i]; val a = q ushr 24
                if (a > 0) {
                    val ia = 255 - a
                    r = ((q shr 16 and 255) * a + r * ia) / 255; g = ((q shr 8 and 255) * a + g * ia) / 255; b = ((q and 255) * a + b * ia) / 255
                }
            }
            out[i] = argb(255, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
        }
        return out
    }

    /** Troca o fundo e REGERA a prévia 156x214 a partir do render (a prévia nunca fica desatualizada). */
    fun withBackground(f: FaceFile, bg: IntArray, sim: SimValues = SimValues()): FaceFile {
        val nf = f.copy(bg = Rgb565.encode(bg))
        return nf.copy(preview = FaceRenderer.previewFrom(FaceRenderer.render(nf, sim).pixels))
    }

    /** Recalcula só a prévia (usar depois de mexer em campos/cores). */
    fun refreshPreview(f: FaceFile, sim: SimValues = SimValues()): FaceFile =
        f.copy(preview = FaceRenderer.previewFrom(FaceRenderer.render(f, sim).pixels))
}
