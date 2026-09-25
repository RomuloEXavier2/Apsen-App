package com.mynd.watch.bambu

import com.mynd.watch.core.WatchCommands
import com.mynd.watch.core.WeatherPayload
import com.mynd.watch.core.Frame
import org.json.JSONObject
import java.util.Calendar

/**
 * Estado da impressora montado a partir das mensagens MQTT "device/<serial>/report".
 * A1/P1 mandam só os campos que mudaram -> sempre fazer merge (update).
 */
class PrintState {
    val raw = mutableMapOf<String, Any?>()

    /** Recebe o payload JSON inteiro; usa só o objeto "print". Retorna true se algo mudou. */
    fun merge(json: String): Boolean {
        val p = runCatching { JSONObject(json).optJSONObject("print") }.getOrNull() ?: return false
        var changed = false
        for (k in p.keys()) {
            val v = p.get(k)
            if (raw[k] != v) { raw[k] = v; changed = true }
        }
        return changed
    }

    private fun num(k: String): Double = when (val v = raw[k]) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    val gcodeState: String? get() = raw["gcode_state"] as? String
    val percent: Int get() = num("mc_percent").toInt()
    val remainingMin: Int get() = num("mc_remaining_time").toInt()
    val nozzle: Int get() = Math.round(num("nozzle_temper")).toInt()
    val nozzleTarget: Int get() = Math.round(num("nozzle_target_temper")).toInt()
    val bed: Int get() = Math.round(num("bed_temper")).toInt()
    val bedTarget: Int get() = Math.round(num("bed_target_temper")).toInt()
    val layer: Int get() = num("layer_num").toInt()
    val totalLayers: Int get() = num("total_layer_num").toInt()
    val jobName: String
        get() = ((raw["subtask_name"] as? String) ?: "").removeSuffix(".gcode").removeSuffix(".3mf").take(40)

    val statePt: String
        get() = mapOf(
            "IDLE" to "Parada", "PREPARE" to "Preparando", "RUNNING" to "Imprimindo", "PAUSE" to "Pausada",
            "FINISH" to "Concluída", "FAILED" to "Falhou", "SLICING" to "Fatiando"
        )[gcodeState] ?: (gcodeState ?: "?")

    companion object {
        fun fmtRemaining(min: Int) = "${min / 60}h%02d".format(min % 60)
    }
}

/**
 * Converte o estado da Bambu no comando de clima (mostrador MYND_HUD_BAMBU v4).
 * Contrato com o mostrador (NÃO mudar um sem o outro):
 *   current (campo 2)      -> texto tipo 24 no painel IMPRESSÃO = "34" (largura corta o "°"; máx 99)
 *   dia0.lo / dia0.hi      -> texto tipo 33 na barra        = "13/51" (horas/minutos restantes)
 *   dia0.icon (0..15)      -> imagem tipo 6 no painel BICO = NOZZLE_LEVELS[icon] ("220°")
 */
object BambuWeatherMapper {
    /** Níveis desenhados nas 16 imagens do mostrador v4 (igual a NOZZLE_LEVELS em reference/python/hud.py). */
    val NOZZLE_LEVELS = intArrayOf(0, 60, 100, 150, 180, 190, 200, 205, 210, 215, 220, 225, 230, 240, 250, 260)

    fun nozzleIndex(t: Int): Int =
        if (t < 45) 0 else (1 until NOZZLE_LEVELS.size).minByOrNull { kotlin.math.abs(NOZZLE_LEVELS[it] - t) }!!

    fun toWeather(s: PrintState, now: Calendar = Calendar.getInstance()): Frame {
        val icon = nozzleIndex(s.nozzle)   // v4: imagem tipo 6 = temperatura do bico
        val base = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 6); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis / 1000
        val rows = listOf(
            s.remainingMin / 60 to s.remainingMin % 60,   // dia 1: HORAS / MINUTOS restantes -> texto 33 "13/51"
            s.nozzle to s.nozzleTarget,        // dia 2: bico
            s.bed to s.bedTarget,              // dia 3: mesa
            s.layer to s.totalLayers,          // dia 4: camada
            s.percent to 100, s.percent to 100, s.percent to 100
        )
        val days = rows.mapIndexed { k, (lo, hi) ->
            WeatherPayload.Day(icon, lo, hi, base + k * 86400L, base + k * 86400L + 12 * 3600, mapOf(11 to s.percent))
        }
        val city = "Bambu ${s.percent}% ${s.statePt}".take(24)
        return WatchCommands.weather(WeatherPayload(s.percent.coerceIn(0, 99), city, days))
    }
}

/** Detecta eventos que viram notificação. Port de Events do Python (mesmo comportamento, mesmos testes). */
class PrintEvents(private val milestones: List<Int> = listOf(25, 50, 75, 90)) {
    private var lastState: String? = null
    private var lastMile = -1

    fun check(s: PrintState): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val gs = s.gcodeState ?: return out
        val pct = s.percent
        val name = s.jobName
        if (lastState == null) {                 // 1a leitura: não dispara alertas antigos
            lastMile = milestones.filter { pct >= it }.maxOrNull() ?: -1
            lastState = gs
            return out
        }
        if (gs != lastState) {
            when {
                gs == "RUNNING" && lastState in setOf("IDLE", "PREPARE", "FINISH", "FAILED") -> {
                    out += "Bambu: iniciou" to name.ifEmpty { "Impressão iniciada" }; lastMile = -1
                }
                gs == "PAUSE" -> out += "Bambu: PAUSADA" to "$name em $pct%"
                gs == "FAILED" -> out += "Bambu: FALHOU" to "$name em $pct%"
                gs == "FINISH" -> out += "Bambu: concluída" to name.ifEmpty { "Impressão pronta" }
            }
        }
        if (gs == "RUNNING") {
            for (m in milestones) if (pct >= m && m > lastMile) {
                lastMile = m
                out += "Bambu $m%" to "Faltam ${PrintState.fmtRemaining(s.remainingMin)}"
            }
        }
        lastState = gs
        return out
    }
}
