package com.mynd.watch.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mynd.watch.data.Library
import com.mynd.watch.service.WatchService
import com.mynd.watch.studio.*
import java.util.*

@Composable
fun PreviewScreen(ref: FaceLibrary.Ref, sendToWatch: (FaceLibrary.Ref) -> Unit, onBack: () -> Unit) {
    val entry   = remember(ref.id) { Library.lib.get(ref.id) }
    val face    = remember(ref) { runCatching { FaceFile.parse(Library.lib.bytes(ref)) }.getOrNull() }
    val contract = entry?.contract ?: DataContract.REAL_WEATHER
    val usesBambu = contract.usesBambu

    if (face == null) { onBack(); return }

    var showBoxes by remember { mutableStateOf(false) }
    var live      by remember { mutableStateOf(false) }

    // Sliders comuns
    var hour    by remember { mutableIntStateOf(10) }
    var minute  by remember { mutableIntStateOf(47) }
    var second  by remember { mutableIntStateOf(32) }
    var weekday by remember { mutableIntStateOf(6) }
    var day     by remember { mutableIntStateOf(28) }
    var month   by remember { mutableIntStateOf(6) }
    var steps   by remember { mutableIntStateOf(7420) }
    var bpm     by remember { mutableIntStateOf(72) }
    var battery by remember { mutableIntStateOf(78) }

    // Sliders Bambu
    var pct      by remember { mutableIntStateOf(34) }
    var remain   by remember { mutableIntStateOf(844) }
    var nozzle   by remember { mutableIntStateOf(220) }

    // Sliders clima real
    var curTemp  by remember { mutableIntStateOf(22) }
    var loTemp   by remember { mutableIntStateOf(14) }
    var hiTemp   by remember { mutableIntStateOf(28) }
    var icon     by remember { mutableIntStateOf(0) }

    // Dados ao vivo
    val printFlow by WatchService.printFlow.collectAsState()
    val batFlow   by WatchService.batteryFlow.collectAsState()

    val weatherChannels: WeatherChannels = if (live) {
        if (usesBambu) {
            val snap = printFlow
            if (snap != null)
                ContractMapper.channels(contract, PrintValues(snap.percent, snap.remainH * 60 + snap.remainM,
                    snap.nozzle, snap.nozzleTarget, snap.bed, snap.bedTarget, snap.layer, snap.totalLayers))
            else WeatherChannels(0, 0, 0, 0)
        } else WeatherChannels(curTemp, loTemp, hiTemp, icon)
    } else {
        if (usesBambu)
            ContractMapper.channels(contract, PrintValues(pct, remain, nozzle))
        else WeatherChannels(curTemp, loTemp, hiTemp, icon)
    }

    val liveNow = remember { Calendar.getInstance() }
    if (live) {
        LaunchedEffect(Unit) {
            while (true) {
                liveNow.time = Date()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    val sim = if (live) SimValues(
        hour = liveNow.get(Calendar.HOUR_OF_DAY), minute = liveNow.get(Calendar.MINUTE),
        second = liveNow.get(Calendar.SECOND), year = liveNow.get(Calendar.YEAR),
        month = liveNow.get(Calendar.MONTH) + 1, day = liveNow.get(Calendar.DAY_OF_MONTH),
        weekday = (liveNow.get(Calendar.DAY_OF_WEEK) - 1), steps = steps, bpm = batFlow ?: bpm,
        battery = batFlow ?: battery, weather = weatherChannels
    ) else SimValues(
        hour = hour, minute = minute, second = second, weekday = weekday,
        day = day, month = month, steps = steps, bpm = bpm, battery = battery,
        weather = weatherChannels
    )

    val issues = remember(face, contract) { FaceValidator.check(face, contract) }
    val hasErr = FaceValidator.hasErrors(issues)

    val DOW_LABELS = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")

    Column(Modifier.fillMaxSize()) {
        // AppBar
        Surface(color = BgCard, shadowElevation = 0.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Cyan) }
                Column(Modifier.weight(1f)) {
                    Text(entry?.name ?: ref.id, color = TxtPri, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("v${ref.v}", color = TxtSec, fontSize = 11.sp)
                }
                Button(
                    onClick = { sendToWatch(ref); onBack() },
                    enabled = !hasErr,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanDark, contentColor = Cyan,
                        disabledContainerColor = CyanDark.copy(alpha = 0.3f), disabledContentColor = TxtSec)
                ) { Text(if (hasErr) "Corrija os erros para enviar" else "Enviar ao relógio", fontSize = 12.sp) }
            }
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Preview
            item {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    FacePreview(face, sim, showBoxes, modifier = Modifier.width(180.dp))
                }
            }

            // Chips
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = showBoxes, onClick = { showBoxes = !showBoxes },
                        label = { Text("Caixas", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanDark, selectedLabelColor = Cyan,
                            containerColor = BgCard, labelColor = TxtSec))
                    FilterChip(selected = live, onClick = { live = !live },
                        label = { Text("Ao vivo", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanDark, selectedLabelColor = Cyan,
                            containerColor = BgCard, labelColor = TxtSec))
                }
            }

            // Painel SIMULAR
            if (!live) {
                item {
                    HudCard {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SIMULAR", color = TxtSec, fontSize = 10.sp, letterSpacing = 2.sp)
                            SimSlider("Hora", hour, 0, 23) { hour = it }
                            SimSlider("Minuto", minute, 0, 59) { minute = it }
                            SimSlider("Segundo", second, 0, 59) { second = it }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("Dia da semana", color = TxtSec, fontSize = 12.sp)
                                Text(DOW_LABELS[weekday], color = TxtPri, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(value = weekday.toFloat(), onValueChange = { weekday = it.toInt() }, valueRange = 0f..6f, steps = 5,
                                colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = CyanMid, inactiveTrackColor = CyanDark))
                            SimSlider("Dia", day, 1, 31) { day = it }
                            SimSlider("Mês", month, 1, 12) { month = it }
                            SimSlider("Passos", steps, 0, 30000, step = 10) { steps = it }
                            SimSlider("BPM", bpm, 40, 200) { bpm = it }
                            SimSlider("Bateria", battery, 0, 100) { battery = it }

                            if (usesBambu) {
                                HorizontalDivider(color = CyanDark, modifier = Modifier.padding(vertical = 4.dp))
                                Text("BAMBU", color = TxtSec, fontSize = 10.sp, letterSpacing = 2.sp)
                                SimSlider("Impressão %", pct, 0, 100) { pct = it }
                                SimSlider("Restante (min)", remain, 0, 2000) { remain = it }
                                SimSlider("Bico °C", nozzle, 20, 300) { nozzle = it }
                            } else {
                                HorizontalDivider(color = CyanDark, modifier = Modifier.padding(vertical = 4.dp))
                                Text("CLIMA", color = TxtSec, fontSize = 10.sp, letterSpacing = 2.sp)
                                SimSlider("Clima atual", curTemp, -10, 45) { curTemp = it }
                                SimSlider("Mín", loTemp, -10, 45) { loTemp = it }
                                SimSlider("Máx", hiTemp, -10, 45) { hiTemp = it }
                                SimSlider("Ícone", icon, 0, 15) { icon = it }
                            }
                        }
                    }
                }
            }

            // Painel VERIFICAÇÃO
            item {
                HudCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("VERIFICAÇÃO", color = TxtSec, fontSize = 10.sp, letterSpacing = 2.sp)
                        if (issues.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.CheckCircle, null, tint = GreenOk, modifier = Modifier.size(16.dp))
                                Text("Nenhum problema", color = GreenOk, fontSize = 13.sp)
                            }
                        } else {
                            issues.forEach { issue ->
                                val color = if (issue.level == FaceValidator.Level.ERRO) RedBad else Orange
                                val icon2 = if (issue.level == FaceValidator.Level.ERRO) Icons.Default.Error else Icons.Default.Warning
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(icon2, null, tint = color, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                                    Text(issue.msg, color = color, fontSize = 12.sp, lineHeight = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SimSlider(label: String, value: Int, min: Int, max: Int, step: Int = 1, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TxtSec, fontSize = 12.sp)
        Text("$value", color = TxtPri, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    val steps = if (step == 1) (max - min - 1).coerceAtLeast(0) else ((max - min) / step - 1).coerceAtLeast(0)
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(((it / step).toInt() * step).coerceIn(min, max)) },
        valueRange = min.toFloat()..max.toFloat(),
        steps = steps,
        modifier = Modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = CyanMid, inactiveTrackColor = CyanDark)
    )
}
