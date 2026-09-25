package com.mynd.watch.studio

/**
 * Contrato de dados de um mostrador: o que o app manda em cada um dos 3 canais do comando de clima.
 *   current -> textos 13/24         pair -> texto 33 (lo/hi)         icon -> imagem 6 / texto 34
 * O contrato fica salvo junto do mostrador na biblioteca (FaceLibrary) e o WatchService usa o contrato
 * do mostrador INSTALADO para montar o clima. Assim mostrador e dados nunca ficam trocados.
 */
enum class CurrentSrc { REAL_WEATHER, BAMBU_PERCENT, BAMBU_NOZZLE, BAMBU_BED, BAMBU_LAYER }
enum class PairSrc { REAL_WEATHER, BAMBU_REMAINING_HM, BAMBU_PERCENT_REMAINING_MIN, BAMBU_NOZZLE_TARGET, BAMBU_BED_TARGET, BAMBU_LAYERS }
enum class IconSrc { REAL_WEATHER, BAMBU_PROGRESS_16, BAMBU_NOZZLE_LEVEL }

data class DataContract(val current: CurrentSrc, val pair: PairSrc, val icon: IconSrc) {

    val usesBambu get() = current != CurrentSrc.REAL_WEATHER || pair != PairSrc.REAL_WEATHER || icon != IconSrc.REAL_WEATHER

    fun serialize() = "${current.name},${pair.name},${icon.name}"

    companion object {
        val REAL_WEATHER = DataContract(CurrentSrc.REAL_WEATHER, PairSrc.REAL_WEATHER, IconSrc.REAL_WEATHER)
        /** Contrato do MYND_HUD_BAMBU v4 (validado no relógio). */
        val BAMBU_V4 = DataContract(CurrentSrc.BAMBU_PERCENT, PairSrc.BAMBU_REMAINING_HM, IconSrc.BAMBU_NOZZLE_LEVEL)

        fun parse(s: String?): DataContract = runCatching {
            val (a, b, c) = s!!.split(","); DataContract(CurrentSrc.valueOf(a), PairSrc.valueOf(b), IconSrc.valueOf(c))
        }.getOrDefault(REAL_WEATHER)

        val NOZZLE_LEVELS = intArrayOf(0, 60, 100, 150, 180, 190, 200, 205, 210, 215, 220, 225, 230, 240, 250, 260)
        fun nozzleIndex(t: Int): Int =
            if (t < 45) 0 else (1 until NOZZLE_LEVELS.size).minByOrNull { kotlin.math.abs(NOZZLE_LEVELS[it] - t) }!!
    }
}

/** Valores da impressora já numéricos (preenchido a partir de bambu.PrintState). */
data class PrintValues(
    val percent: Int = 0, val remainingMin: Int = 0, val nozzle: Int = 0, val nozzleTarget: Int = 0,
    val bed: Int = 0, val bedTarget: Int = 0, val layer: Int = 0, val totalLayers: Int = 0,
)

/** Os 3 números que vão no comando de clima (dia 0). */
data class WeatherChannels(val current: Int, val lo: Int, val hi: Int, val icon: Int)

object ContractMapper {
    fun channels(c: DataContract, p: PrintValues): WeatherChannels {
        val cur = when (c.current) {
            CurrentSrc.BAMBU_PERCENT -> p.percent.coerceIn(0, 99)
            CurrentSrc.BAMBU_NOZZLE -> p.nozzle
            CurrentSrc.BAMBU_BED -> p.bed
            CurrentSrc.BAMBU_LAYER -> p.layer
            CurrentSrc.REAL_WEATHER -> 0
        }
        val (lo, hi) = when (c.pair) {
            PairSrc.BAMBU_REMAINING_HM -> p.remainingMin / 60 to p.remainingMin % 60
            PairSrc.BAMBU_PERCENT_REMAINING_MIN -> p.percent to p.remainingMin
            PairSrc.BAMBU_NOZZLE_TARGET -> p.nozzle to p.nozzleTarget
            PairSrc.BAMBU_BED_TARGET -> p.bed to p.bedTarget
            PairSrc.BAMBU_LAYERS -> p.layer to p.totalLayers
            PairSrc.REAL_WEATHER -> 0 to 0
        }
        val icon = when (c.icon) {
            IconSrc.BAMBU_NOZZLE_LEVEL -> DataContract.nozzleIndex(p.nozzle)
            IconSrc.BAMBU_PROGRESS_16 -> Math.round(p.percent / 100.0 * 15).toInt().coerceIn(0, 15)
            IconSrc.REAL_WEATHER -> 0
        }
        return WeatherChannels(cur, lo, hi, icon)
    }
}
