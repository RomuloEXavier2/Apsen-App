package com.mynd.watch.ui

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.mynd.watch.bambu.BambuCloud
import com.mynd.watch.ble.LinkState
import com.mynd.watch.data.AppLog
import com.mynd.watch.data.Settings
import com.mynd.watch.service.PrintSnapshot
import com.mynd.watch.service.WatchService
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private val perms = ActivityResultContracts.RequestMultiplePermissions()
    private val askPerms = registerForActivityResult(perms) { WatchService.start(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        askPerms.launch(needed.toTypedArray())
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Cyan, background = BgDeep, surface = BgCard,
                    onSurface = TxtPri, onBackground = TxtPri
                )
            ) { AppShell() }
        }
    }

    // ── Shell ───────────────────────────────────────────────────────────
    @Composable
    private fun AppShell() {
        val status    by WatchService.statusFlow.collectAsState()
        val linkState by WatchService.linkStateFlow.collectAsState()
        var tab by remember { mutableIntStateOf(0) }

        Scaffold(
            containerColor = BgDeep,
            topBar    = { TopBar(status, linkState) },
            bottomBar = { BottomNav(tab) { tab = it } }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize().background(BgDeep)) {
                when (tab) {
                    0 -> WatchTab()
                    1 -> FacesTab()
                    2 -> BambuTab()
                    else -> LogsTab()
                }
            }
        }
    }

    // ── Top bar ─────────────────────────────────────────────────────────
    @Composable
    private fun TopBar(status: String, linkState: LinkState) {
        val infinite = rememberInfiniteTransition(label = "dot")
        val dotAlpha by infinite.animateFloat(
            initialValue = 1f,
            targetValue  = when (linkState) { LinkState.READY -> 0.4f; else -> 0f },
            animationSpec = infiniteRepeatable(
                tween(if (linkState == LinkState.READY) 1800 else 350, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "alpha"
        )
        val dotColor = when (linkState) {
            LinkState.READY -> GreenOk
            LinkState.CONNECTING, LinkState.SCANNING -> Orange
            else -> RedBad
        }
        val stateLabel = when (linkState) {
            LinkState.READY -> "Conectado"
            LinkState.SCANNING -> "Buscando..."
            LinkState.CONNECTING -> "Conectando..."
            else -> "Desconectado"
        }
        Surface(color = BgCard, shadowElevation = 0.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("MYND", color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Text(" Watch", color = TxtPri, fontSize = 22.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(10.dp)
                        .drawBehind { drawCircle(dotColor.copy(alpha = 0.2f * dotAlpha), size.minDimension * 1.8f) }
                        .background(dotColor.copy(alpha = dotAlpha), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(stateLabel, color = dotColor.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    // ── Bottom nav ──────────────────────────────────────────────────────
    @Composable
    private fun BottomNav(current: Int, onSelect: (Int) -> Unit) {
        data class NavItem(val label: String, val icon: ImageVector)
        val items = listOf(
            NavItem("Relógio",     Icons.Default.Bluetooth),
            NavItem("Mostradores", Icons.Default.Style),
            NavItem("Bambu",       Icons.Default.Print),
            NavItem("Logs",        Icons.AutoMirrored.Filled.List)
        )
        NavigationBar(containerColor = BgCard, tonalElevation = 0.dp) {
            items.forEachIndexed { idx, item ->
                NavigationBarItem(
                    selected  = current == idx,
                    onClick   = { onSelect(idx) },
                    icon      = { Icon(item.icon, contentDescription = item.label) },
                    label     = { Text(item.label, fontSize = 10.sp) },
                    colors    = NavigationBarItemDefaults.colors(
                        selectedIconColor   = Cyan,  selectedTextColor   = Cyan,
                        unselectedIconColor = TxtSec, unselectedTextColor = TxtSec,
                        indicatorColor      = CyanDark
                    )
                )
            }
        }
    }

    // ── Watch tab ───────────────────────────────────────────────────────
    @Composable
    private fun WatchTab() {
        val linkState by WatchService.linkStateFlow.collectAsState()
        val status    by WatchService.statusFlow.collectAsState()
        val battery   by WatchService.batteryFlow.collectAsState()
        val ctx = this@MainActivity
        val pm  = remember { ctx.getSystemService(PowerManager::class.java) }
        var battOptNeeded by remember { mutableStateOf(!pm.isIgnoringBatteryOptimizations(ctx.packageName)) }
        val battOptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            battOptNeeded = !pm.isIgnoringBatteryOptimizations(ctx.packageName)
        }

        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Status card
            item {
                HudCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bluetooth, null, tint = Cyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("CONEXÃO", color = TxtSec, fontSize = 10.sp, letterSpacing = 2.sp)
                            Spacer(Modifier.weight(1f))
                            if (battery != null)
                                Text("🔋 $battery%", color = TxtSec, fontSize = 11.sp)
                        }
                        Text(status, color = TxtPri, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        val stateColor = when (linkState) {
                            LinkState.READY -> GreenOk; LinkState.SCANNING, LinkState.CONNECTING -> Orange; else -> TxtSec
                        }
                        val stateText = when (linkState) {
                            LinkState.READY -> "● Relógio conectado e ativo"
                            LinkState.SCANNING -> "◌ Procurando HAYLOU Watch S6..."
                            LinkState.CONNECTING -> "◌ Estabelecendo conexão BLE..."
                            else -> "○ Sem conexão com o relógio"
                        }
                        Text(stateText, color = stateColor, fontSize = 12.sp)
                    }
                }
            }
            // Ações
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CyanButton(Icons.Default.Refresh, "Iniciar / Reconectar") { WatchService.start(ctx) }
                    OutlineAction(Icons.Default.Notifications, "Enviar notificação de teste") {
                        WatchService.enqueueNotification("MYND", "Teste do app MYND Watch")
                    }
                    OutlineAction(Icons.Default.NotificationsActive, "Liberar acesso às notificações") {
                        startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }
            }
            // Card de otimização de bateria
            if (battOptNeeded) {
                item {
                    HudCard(borderColor = Orange.copy(alpha = 0.6f)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatteryAlert, null, tint = Orange, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Otimização de bateria ativa", color = Orange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("O Android pode encerrar o serviço. Toque para corrigir.", color = TxtSec, fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                battOptLauncher.launch(
                                    Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                        .apply { data = Uri.parse("package:$packageName") }
                                )
                            }) { Text("Corrigir", color = Orange, fontSize = 13.sp) }
                        }
                    }
                }
            }
            item {
                Text(
                    "Dica: force a parada do app Haylou antes de conectar — o relógio aceita apenas uma conexão por vez.",
                    color = TxtSec, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    // ── Faces tab ───────────────────────────────────────────────────────
    @Composable
    private fun FacesTab() {
        val ctx = this@MainActivity
        val assetNames = remember { assets.list("faces")?.toList().orEmpty() }
        FaceEditorScreen(
            ctx = ctx,
            assetNames = assetNames,
            loadAsset = { name -> assets.open("faces/$name").readBytes() },
            loadUri   = { uri ->
                runCatching {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            },
            loadBitmap = { uri ->
                runCatching {
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            },
            onSend = { bytes ->
                val tmp = File(cacheDir, "face_edit.bin").also { it.writeBytes(bytes) }
                val uri = FileProvider.getUriForFile(ctx, "$packageName.provider", tmp)
                startService(
                    Intent(ctx, WatchService::class.java)
                        .setAction(WatchService.ACTION_UPLOAD_URI)
                        .putExtra("uri", uri.toString())
                )
            }
        )
    }

    // ── Bambu tab ───────────────────────────────────────────────────────
    @Composable
    private fun BambuTab() {
        val printSnap by WatchService.printFlow.collectAsState()
        val s = remember { Settings(this) }
        var email    by remember { mutableStateOf("") }
        var pass     by remember { mutableStateOf("") }
        var code     by remember { mutableStateOf("") }
        var serial   by remember { mutableStateOf("") }
        var msg      by remember { mutableStateOf("") }
        var needCode by remember { mutableStateOf(false) }
        var showForm by remember { mutableStateOf(printSnap == null) }
        val cloud = remember { BambuCloud() }

        LaunchedEffect(Unit) {
            serial = s.get(Settings.BAMBU_SERIAL) ?: ""
        }

        suspend fun finish(token: String) {
            val uid = cloud.uid(token)
            s.set(Settings.BAMBU_TOKEN, token); s.set(Settings.BAMBU_UID, uid)
            s.set(Settings.BAMBU_SERIAL, serial.trim()); s.setBool(Settings.BAMBU_ENABLED, true)
            msg = "Conectado! Reinicie o serviço."
        }

        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Painel ao vivo
            if (printSnap != null) {
                item { PrintStatusCard(printSnap!!) }
            }
            // Formulário de conta
            item {
                HudCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { showForm = !showForm },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Print, null, tint = Cyan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("CONTA BAMBU LAB", color = TxtSec, fontSize = 10.sp, letterSpacing = 2.sp)
                            }
                            Icon(
                                if (showForm) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                null, tint = TxtSec, modifier = Modifier.size(20.dp)
                            )
                        }
                        AnimatedVisibility(showForm) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                HudTextField(serial,  { serial = it },  "Serial da impressora")
                                HudTextField(email,   { email = it },   "E-mail da conta Bambu")
                                HudTextField(pass,    { pass = it },    "Senha", isPassword = true)
                                if (needCode) HudTextField(code, { code = it }, "Código do e-mail")
                                Spacer(Modifier.height(2.dp))
                                CyanButton(Icons.AutoMirrored.Filled.Login, if (needCode) "Confirmar código" else "Entrar") {
                                    lifecycleScope.launch {
                                        msg = "Entrando..."
                                        val r = runCatching {
                                            if (needCode) cloud.loginWithCode(email.trim(), code.trim())
                                            else cloud.login(email.trim(), pass)
                                        }.getOrElse { BambuCloud.LoginResult.Error(it.message ?: "erro") }
                                        when (r) {
                                            is BambuCloud.LoginResult.Token ->
                                                runCatching { finish(r.accessToken) }.onFailure { msg = "uid: ${it.message}" }
                                            BambuCloud.LoginResult.NeedsEmailCode -> { needCode = true; msg = "Digite o código enviado ao seu e-mail" }
                                            is BambuCloud.LoginResult.Error -> msg = "Erro: ${r.msg}"
                                        }
                                    }
                                }
                                if (msg.isNotEmpty())
                                    Text(msg, color = if (msg.startsWith("Erro")) RedBad else Cyan, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PrintStatusCard(s: PrintSnapshot) {
        val pct = (s.percent / 100f).coerceIn(0f, 1f)
        val stateColor = when (s.gcodeState) {
            "RUNNING" -> GreenOk; "PAUSE" -> Orange; "FAILED" -> RedBad; else -> TxtSec
        }
        HudCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("BAMBU LAB", color = TxtSec, fontSize = 10.sp, letterSpacing = 2.sp)
                    Text(s.statePt.uppercase(), color = stateColor, fontSize = 10.sp,
                        letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                }
                if (s.jobName.isNotEmpty())
                    Text(s.jobName, color = TxtPri, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                // Progresso
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom) {
                        Text("${s.percent}%", color = Cyan, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        if (s.remainH > 0 || s.remainM > 0)
                            Text("${s.remainH}h ${s.remainM.toString().padStart(2,'0')}min restantes",
                                color = TxtSec, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(CyanDark)) {
                        Box(Modifier.fillMaxWidth(pct).fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(CyanMid, Cyan))))
                    }
                    if (s.totalLayers > 0)
                        Text("Camada ${s.layer} / ${s.totalLayers}", color = TxtSec, fontSize = 11.sp)
                }
                // Temperaturas
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TempChip("Bico", s.nozzle, s.nozzleTarget, Modifier.weight(1f))
                    TempChip("Mesa", s.bed,    s.bedTarget,    Modifier.weight(1f))
                }
            }
        }
    }

    @Composable
    private fun TempChip(label: String, current: Int, target: Int, modifier: Modifier = Modifier) {
        Box(modifier.background(BgField, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Column {
                Text(label.uppercase(), color = TxtSec, fontSize = 10.sp, letterSpacing = 1.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${current}°", color = TxtPri, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (target > 0)
                        Text(" / ${target}°", color = TxtSec, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
    }

    // ── Logs tab ────────────────────────────────────────────────────────
    @Composable
    private fun LogsTab() {
        var lines by remember { mutableStateOf(AppLog.snapshot()) }
        val listState = rememberLazyListState()
        DisposableEffect(Unit) {
            val cb: (List<String>) -> Unit = { runOnUiThread { lines = it } }
            AppLog.listeners += cb
            onDispose { AppLog.listeners -= cb }
        }
        LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(0) }

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("LOGS", color = TxtSec, fontSize = 10.sp, letterSpacing = 2.sp)
                Text("${lines.size} entradas", color = TxtSec, fontSize = 11.sp)
            }
            HorizontalDivider(color = CyanDark, thickness = 1.dp)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(lines) { line ->
                    val color = when {
                        line.contains("falhou", ignoreCase = true) || line.contains("erro", ignoreCase = true)
                            || line.contains("FALHOU") || line.contains("inválido") -> RedBad.copy(alpha = 0.9f)
                        line.contains("conectado") || line.contains("instalado") || line.contains("concluída") -> GreenOk.copy(alpha = 0.8f)
                        line.contains("Bambu") -> Cyan.copy(alpha = 0.8f)
                        else -> TxtSec
                    }
                    Text(line, color = color, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        lineHeight = 16.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }

}
