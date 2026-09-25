package com.mynd.watch.studio

import com.mynd.watch.bambu.PrintState
import com.mynd.watch.core.Frame
import com.mynd.watch.core.WatchCommands
import com.mynd.watch.core.WeatherPayload
import java.util.Calendar

/**
 * Monta o comando de clima (0x14) a partir do CONTRATO do mostrador instalado.
 * Para DataContract.BAMBU_V4 gera exatamente os mesmos bytes de BambuWeatherMapper.toWeather() (teste).
 * Retorna null se o contrato não usa Bambu (nesse caso o app NÃO manda clima da Bambu).
 */
object ContractWeather {

    fun values(s: PrintState) = PrintValues(
        percent = s.percent, remainingMin = s.remainingMin, nozzle = s.nozzle, nozzleTarget = s.nozzleTarget,
        bed = s.bed, bedTarget = s.bedTarget, layer = s.layer, totalLayers = s.totalLayers,
    )

    fun build(contract: DataContract, s: PrintState, now: Calendar = Calendar.getInstance()): Frame? {
        if (!contract.usesBambu) return null
        val ch = ContractMapper.channels(contract, values(s))
        val base = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 6); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis / 1000
        val rows = listOf(
            ch.lo to ch.hi,                    // dia 1 = canal "par" do contrato (texto 33)
            s.nozzle to s.nozzleTarget,        // dias 2..4: detalhes na tela Clima do relógio
            s.bed to s.bedTarget,
            s.layer to s.totalLayers,
            s.percent to 100, s.percent to 100, s.percent to 100,
        )
        val days = rows.mapIndexed { k, (lo, hi) ->
            WeatherPayload.Day(ch.icon, lo, hi, base + k * 86400L, base + k * 86400L + 12 * 3600, mapOf(11 to s.percent))
        }
        val city = "Bambu ${s.percent}% ${s.statePt}".take(24)
        return WatchCommands.weather(WeatherPayload(ch.current, city, days))
    }
}
