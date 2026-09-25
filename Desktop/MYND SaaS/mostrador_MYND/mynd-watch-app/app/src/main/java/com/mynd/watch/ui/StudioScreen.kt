package com.mynd.watch.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mynd.watch.data.Library
import com.mynd.watch.studio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StudioScreen(ref: FaceLibrary.Ref, onBack: () -> Unit) {
    val ctx      = LocalContext.current
    val scope    = rememberCoroutineScope()
    val entry    = remember(ref.id) { Library.lib.get(ref.id) }
    val original = remember(ref) { runCatching { FaceFile.parse(Library.lib.bytes(ref)) }.getOrNull() }
    val contract = entry?.contract ?: DataContract.REAL_WEATHER

    if (original == null) { onBack(); return }

    var face    by remember { mutableStateOf(original) }
    var isDirty by remember { mutableStateOf(false) }
    var selTab  by remember { mutableIntStateOf(0) }
    var selIdx  by remember { mutableIntStateOf(-1) }

    // dialogs
    var discardDlg  by remember { mutableStateOf(false) }
    var saveDlg     by remember { mutableStateOf(false) }
    var saveNote    by remember { mutableStateOf("") }
    var removeDlg   by remember { mutableStateOf(false) }
    var addFieldDlg by remember { mutableStateOf(false) }

    // fundo
    var wallArgb  by remember { mutableStateOf<IntArray?>(null) }
    var keepFrame by remember { mutableStateOf(true) }
    var darken    by remember { mutableIntStateOf(45) }
    var tint      by remember { mutableIntStateOf(0) }

    val frameArgb = remember(contract.usesBambu) {
        runCatching {
            val name = if (contract.usesBambu) "studio/frames/hud_bambu_frame.png"
                       else "studio/frames/hud_frame.png"
            val bmp = BitmapFactory.decodeStream(ctx.assets.open(name))
            IntArray(JL.W * JL.H).also { bmp.getPixels(it, 0, JL.W, 0, 0, JL.W, JL.H) }
        }.getOrNull()
    }

    LaunchedEffect(wallArgb, keepFrame, darken, tint) {
        val wall = wallArgb ?: return@LaunchedEffect
        val base = face
        val newFace = withContext(Dispatchers.Default) {
            val bg = FaceComposer.compose(wall, if (keepFrame) frameArgb else null, FaceComposer.WallpaperFx(darken, tint))
            FaceComposer.withBackground(base, bg)
        }
        face = newFace; isDirty = true
    }

    val wallLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val px = runCatching {
                val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: return@runCatching null
                val sw = bmp.width; val sh = bmp.height
                val tr = JL.W.toFloat() / JL.H; val sr = sw.toFloat() / sh
                val (cw, ch) = if (sr > tr) ((sh * tr).toInt() to sh) else (sw to (sw / tr).toInt())
                val scaled = Bitmap.createScaledBitmap(Bitmap.createBitmap(bmp, (sw - cw) / 2, (sh - ch) / 2, cw, ch), JL.W, JL.H, true)
                IntArray(JL.W * JL.H).also { scaled.getPixels(it, 0, JL.W, 0, 0, JL.W, JL.H) }
            }.getOrNull()
            withContext(Dispatchers.Main) { if (px != null) wallArgb = px }
        }
    }

    // dígitos
    val FONTS = remember {
        listOf("Oxanium" to "studio/fonts/oxanium-800.ttf",
               "Michroma" to "studio/fonts/michroma-400.ttf",
               "Orbitron" to "studio/fonts/orbitron-700.ttf",
               "Russo One" to "studio/fonts/russo-one.ttf",
               "Space Mono" to "studio/fonts/space-mono-400.ttf")
    }
    var selFont by remember { mutableIntStateOf(0) }
    var cHora   by remember { mutableIntStateOf(0xFFFFFFFF.toInt()) }
    var cMin    by remember { mutableIntStateOf(0xFF00C8FF.toInt()) }
    var cDia    by remember { mutableIntStateOf(0xFF00C8FF.toInt()) }
    var cDow    by remember { mutableIntStateOf(0xFFFFFFFF.toInt()) }

    // verificar
    val issues = remember(face, contract) { FaceValidator.check(face, contract) }
    val hasErr = FaceValidator.hasErrors(issues)
    val sim    = remember { SimValues() }

    // ── dialogs ──────────────────────────────────────────────────────────
    if (discardDlg) AlertDialog(onDismissRequest = { discardDlg = false }, containerColor = BgCard,
        title = { Text("Descartar alterações?", color = TxtPri) },
        text  = { Text("Todas as modificações serão perdidas.", color = TxtSec) },
        confirmButton = { TextButton(onClick = { discardDlg = false; onBack() }) { Text("Descartar", color = RedBad) } },
        dismissButton = { TextButton(onClick = { discardDlg = false }) { Text("Continuar editando", color = Cyan) } })

    if (saveDlg) AlertDialog(onDismissRequest = { saveDlg = false }, containerColor = BgCard,
        title = { Text("Salvar nova versão", color = TxtPri) },
        text  = { HudTextField(saveNote, { saveNote = it }, "Nota curta") },
        confirmButton = {
            TextButton(onClick = {
                val note = saveNote.trim().ifEmpty { "editado no estúdio" }
                scope.launch(Dispatchers.Default) {
                    val refreshed = FaceComposer.refreshPreview(face)
                    val newRef = withContext(Dispatchers.IO) {
                        Library.lib.save(entry?.name ?: ref.id, refreshed.build(), note, contract)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Salvo como v${newRef.v}", Toast.LENGTH_SHORT).show()
                        saveDlg = false; onBack()
                    }
                }
            }) { Text("Salvar", color = Cyan) }
        },
        dismissButton = { TextButton(onClick = { saveDlg = false }) { Text("Cancelar", color = TxtSec) } })

    if (removeDlg) AlertDialog(onDismissRequest = { removeDlg = false }, containerColor = BgCard,
        title = { Text("Remover campo?", color = TxtPri) },
        text  = { Text("Esta operação não pode ser desfeita.", color = TxtSec) },
        confirmButton = {
            TextButton(onClick = {
                val ts = face.texts.toMutableList()
                if (selIdx in ts.indices) ts.removeAt(selIdx)
                face = face.copy(texts = ts); isDirty = true; selIdx = -1; removeDlg = false
            }) { Text("Remover", color = RedBad) }
        },
        dismissButton = { TextButton(onClick = { removeDlg = false }) { Text("Cancelar", color = TxtSec) } })

    if (addFieldDlg) {
        var addType by remember { mutableIntStateOf(Catalog.TEXT_TYPES.first().id) }
        AlertDialog(onDismissRequest = { addFieldDlg = false }, containerColor = BgCard,
            title = { Text("Adicionar campo", color = TxtPri) },
            text  = { HudDropdown("Tipo", addType,
                Catalog.TEXT_TYPES.map { it.id to "${it.id} · ${it.nome} · ex.: ${it.exemplo}" }) { addType = it } },
            confirmButton = {
                TextButton(onClick = {
                    if (face.tableBytes + JL.TXT_REC > JL.TABLE_BYTES || face.images.size + face.texts.size >= JL.MAX_RECORDS)
                        Toast.makeText(ctx, "Sem espaço na tabela do mostrador", Toast.LENGTH_SHORT).show()
                    else {
                        face = face.copy(texts = face.texts + TextElem(addType, 90, 140, 60, 255, 255, 255, 2))
                        isDirty = true
                    }
                    addFieldDlg = false
                }) { Text("Adicionar", color = Cyan) }
            },
            dismissButton = { TextButton(onClick = { addFieldDlg = false }) { Text("Cancelar", color = TxtSec) } })
    }

    Column(Modifier.fillMaxSize()) {
        // AppBar
        Surface(color = BgCard, shadowElevation = 0.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (isDirty) discardDlg = true else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Cyan)
                }
                Column(Modifier.weight(1f)) {
                    Text("ESTÚDIO", color = TxtSec, fontSize = 10.sp, letterSpacing = 2.sp)
                    Text(entry?.name ?: ref.id, color = TxtPri, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text("v${ref.v}", color = TxtSec, fontSize = 12.sp)
            }
        }

        // Preview (144dp = ~0.6x scale)
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
            Box(Modifier.width(144.dp)) {
                FacePreview(face, sim, showBoxes = true, modifier = Modifier.fillMaxWidth())
                if (selTab == 1 && selIdx >= 0) {
                    Box(Modifier.fillMaxSize().pointerInput(selIdx) {
                        detectDragGestures { _, d ->
                            val sx = JL.W.toFloat() / size.width
                            val sy = JL.H.toFloat() / size.height
                            val dx = (d.x * sx).toInt(); val dy = (d.y * sy).toInt()
                            if ((dx != 0 || dy != 0) && selIdx in face.texts.indices) {
                                val ts = face.texts.toMutableList()
                                val old = ts[selIdx]
                                ts[selIdx] = old.copy(x = (old.x + dx).coerceIn(0, JL.W - 1),
                                                      y = (old.y + dy).coerceIn(0, JL.H - 1))
                                face = face.copy(texts = ts); isDirty = true
                            }
                        }
                    })
                }
            }
        }

        // TabRow
        TabRow(selectedTabIndex = selTab, containerColor = BgCard, contentColor = Cyan) {
            listOf("Fundo", "Campos", "Dígitos", "Verificar").forEachIndexed { i, t ->
                Tab(selected = selTab == i, onClick = { selTab = i },
                    text = { Text(t, fontSize = 12.sp) },
                    selectedContentColor = Cyan, unselectedContentColor = TxtSec)
            }
        }

        // Content
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (selTab) {
                0 -> FundoTab(wallArgb, keepFrame, darken, tint,
                    onPickWall = { wallLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onKeepFrame = { keepFrame = it }, onDarken = { darken = it }, onTint = { tint = it },
                    onRestore = { face = face.copy(bg = original.bg, preview = original.preview); wallArgb = null; darken = 45; tint = 0; keepFrame = true; isDirty = true })
                1 -> CamposTab(face, selIdx, onSelect = { selIdx = it },
                    onChange = { face = it; isDirty = true },
                    onAdd = { addFieldDlg = true },
                    onRemove = { if (selIdx >= 0) removeDlg = true })
                2 -> DigitosTab(ctx, FONTS, selFont, cHora, cMin, cDia, cDow,
                    onFont = { selFont = it }, onHora = { cHora = it }, onMin = { cMin = it },
                    onDia = { cDia = it }, onDow = { cDow = it },
                    onApply = {
                        scope.launch(Dispatchers.Default) {
                            val tf = Typeface.createFromAsset(ctx.assets, FONTS[selFont].second)
                            val newFace = FaceComposer.refreshPreview(applyDigits(face, tf, cHora, cMin, cDia, cDow))
                            withContext(Dispatchers.Main) { face = newFace; isDirty = true }
                        }
                    })
                3 -> VerificarTab(issues, face)
            }
        }

        // Bottom bar
        Surface(color = BgCard) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (isDirty) discardDlg = true else onBack() },
                    modifier = Modifier.weight(1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanDark),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TxtSec)) {
                    Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp)); Text("Descartar", fontSize = 13.sp)
                }
                Button(onClick = { saveNote = ""; saveDlg = true }, enabled = !hasErr,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanDark, contentColor = Cyan,
                        disabledContainerColor = CyanDark.copy(alpha = 0.3f), disabledContentColor = TxtSec)) {
                    Text(if (hasErr) "Corrija os erros" else "Salvar versão", fontSize = 13.sp)
                }
            }
        }
    }
}

// ── Aba Fundo ────────────────────────────────────────────────────────────────

@Composable
private fun FundoTab(wallArgb: IntArray?, keepFrame: Boolean, darken: Int, tint: Int,
    onPickWall: () -> Unit, onKeepFrame: (Boolean) -> Unit, onDarken: (Int) -> Unit,
    onTint: (Int) -> Unit, onRestore: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickWall, modifier = Modifier.weight(1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanDark),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)) {
                    Icon(Icons.Default.Image, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp)); Text("Escolher imagem", fontSize = 12.sp)
                }
                if (wallArgb != null)
                    OutlinedButton(onClick = onRestore,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanDark),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TxtSec)) {
                        Icon(Icons.Default.Restore, null, Modifier.size(16.dp))
                    }
            }
        }
        if (wallArgb != null) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Manter moldura MYND", color = TxtSec, fontSize = 12.sp)
                    Switch(checked = keepFrame, onCheckedChange = onKeepFrame,
                        colors = SwitchDefaults.colors(checkedThumbColor = Cyan, checkedTrackColor = CyanDark))
                }
            }
            item { StudioSlider("Escurecer", darken, 0, 90) { onDarken(it) } }
            item { StudioSlider("Tom ciano", tint, 0, 100) { onTint(it) } }
        }
        item {
            Text("As molduras só combinam com os mostradores MYND HUD (mesma posição dos painéis).",
                color = TxtSec, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

// ── Aba Campos ───────────────────────────────────────────────────────────────

@Composable
private fun CamposTab(face: FaceFile, selIdx: Int, onSelect: (Int) -> Unit,
    onChange: (FaceFile) -> Unit, onAdd: () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(face.texts) { i, txt ->
                val typeName = Catalog.TEXT_BY_ID[txt.type]?.nome ?: "Tipo inválido ${txt.type}"
                val isInvalid = Catalog.TEXT_BY_ID[txt.type] == null
                Surface(modifier = Modifier.fillMaxWidth().clickable { onSelect(i) },
                    color = if (i == selIdx) CyanDark.copy(alpha = 0.3f) else BgField,
                    shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(typeName, color = if (isInvalid) RedBad else TxtPri,
                                fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("x=${txt.x}  y=${txt.y}  w=${txt.width}", color = TxtSec, fontSize = 11.sp)
                        }
                        Box(Modifier.size(16.dp).background(
                            Color(txt.r shl 16 or (txt.g shl 8) or txt.b or 0xFF000000.toInt()), CircleShape))
                    }
                }
            }
        }

        if (selIdx in face.texts.indices) {
            val txt = face.texts[selIdx]
            Surface(color = BgCard, shadowElevation = 0.dp) {
                LazyColumn(Modifier.heightIn(max = 280.dp).fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { HudDropdown("Tipo", txt.type,
                        Catalog.TEXT_TYPES.map { it.id to "${it.id} · ${it.nome}" }) { t ->
                        val ts = face.texts.toMutableList(); ts[selIdx] = ts[selIdx].copy(type = t)
                        onChange(face.copy(texts = ts))
                    } }
                    item { StepControl("X", txt.x, 0, JL.W - 1) { v ->
                        val ts = face.texts.toMutableList(); ts[selIdx] = ts[selIdx].copy(x = v)
                        onChange(face.copy(texts = ts))
                    } }
                    item { StepControl("Y", txt.y, 0, JL.H - 1) { v ->
                        val ts = face.texts.toMutableList(); ts[selIdx] = ts[selIdx].copy(y = v)
                        onChange(face.copy(texts = ts))
                    } }
                    item { StepControl("Largura", txt.width, 8, 240) { v ->
                        val ts = face.texts.toMutableList(); ts[selIdx] = ts[selIdx].copy(width = v)
                        onChange(face.copy(texts = ts))
                    } }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Esq." to 1, "Centro" to 2, "Dir." to 3).forEach { (lbl, code) ->
                                OutlinedButton(onClick = {
                                    val ts = face.texts.toMutableList()
                                    ts[selIdx] = ts[selIdx].copy(align = code)
                                    onChange(face.copy(texts = ts))
                                }, modifier = Modifier.weight(1f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp,
                                        if (txt.align == code) Cyan else CyanDark),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (txt.align == code) Cyan else TxtSec)) {
                                    Text(lbl, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    item {
                        Text("Cor", color = TxtSec, fontSize = 11.sp)
                        FieldColorPicker(txt.r, txt.g, txt.b) { r, g, b ->
                            val ts = face.texts.toMutableList()
                            ts[selIdx] = ts[selIdx].copy(r = r, g = g, b = b)
                            onChange(face.copy(texts = ts))
                        }
                    }
                    if (txt.type == 19) {
                        item { HudTextField(txt.fixedText, { v ->
                            val bytes = ByteArray(20)
                            v.take(19).toByteArray(Charsets.US_ASCII).copyInto(bytes)
                            val ts = face.texts.toMutableList(); ts[selIdx] = ts[selIdx].copy(tail = bytes)
                            onChange(face.copy(texts = ts))
                        }, "Texto fixo (máx 19 chars)") }
                    }
                    item { TextButton(onClick = onRemove) { Text("Remover campo", color = RedBad, fontSize = 12.sp) } }
                }
            }
        }

        OutlineAction(Icons.Default.Add, "Adicionar campo") { onAdd() }
    }
}

// ── Aba Dígitos ───────────────────────────────────────────────────────────────

@Composable
private fun DigitosTab(ctx: Context, fonts: List<Pair<String, String>>, selFont: Int,
    cHora: Int, cMin: Int, cDia: Int, cDow: Int,
    onFont: (Int) -> Unit, onHora: (Int) -> Unit, onMin: (Int) -> Unit,
    onDia: (Int) -> Unit, onDow: (Int) -> Unit, onApply: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HudDropdown("Fonte", selFont, fonts.mapIndexed { i, (n, _) -> i to n }) { onFont(it) } }
        item { Text("CORES", color = TxtSec, fontSize = 10.sp, letterSpacing = 2.sp) }
        item { ColorRow("Horas", cHora, onHora) }
        item { ColorRow("Minutos", cMin, onMin) }
        item { ColorRow("Dia", cDia, onDia) }
        item { ColorRow("Dia da semana", cDow, onDow) }
        item { CyanButton(Icons.Default.AutoAwesome, "Aplicar") { onApply() } }
        item {
            Text("O tamanho dos dígitos é o do mostrador original. Para mudar tamanho/posição, peça um novo .bin.",
                color = TxtSec, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

// ── Aba Verificar ─────────────────────────────────────────────────────────────

@Composable
private fun VerificarTab(issues: List<FaceValidator.Issue>, face: FaceFile) {
    val fileKb = remember(face) { runCatching { face.build().size / 1024 }.getOrElse { 0 } }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Tamanho: $fileKb KB", color = TxtSec, fontSize = 12.sp) }
        if (issues.isEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = GreenOk, modifier = Modifier.size(16.dp))
                    Text("Nenhum problema", color = GreenOk, fontSize = 13.sp)
                }
            }
        } else {
            issues.forEach { issue ->
                item {
                    val color = if (issue.level == FaceValidator.Level.ERRO) RedBad else Orange
                    val ic    = if (issue.level == FaceValidator.Level.ERRO) Icons.Default.Error else Icons.Default.Warning
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(ic, null, tint = color, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                        Text(issue.msg, color = color, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

// ── Componentes auxiliares ────────────────────────────────────────────────────

@Composable
private fun ColorRow(label: String, color: Int, onChange: (Int) -> Unit) {
    val r = color ushr 16 and 255; val g = color ushr 8 and 255; val b = color and 255
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = TxtSec, fontSize = 12.sp)
        FieldColorPicker(r, g, b) { nr, ng, nb ->
            onChange(0xFF000000.toInt() or (nr shl 16) or (ng shl 8) or nb)
        }
    }
}

@Composable
private fun FieldColorPicker(r: Int, g: Int, b: Int, onChange: (Int, Int, Int) -> Unit) {
    val palette = remember {
        listOf(0xFFFFFFFF, 0xFF00C8FF, 0xFF00FF99, 0xFFFFFF00, 0xFFFF9900, 0xFFFF3355, 0xFFFF66FF, 0xFFD0E8FF)
            .map { it.toInt() }
    }
    var hex by remember(r, g, b) { mutableStateOf("%02X%02X%02X".format(r, g, b)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            palette.forEach { c ->
                val cr = c shr 16 and 255; val cg = c shr 8 and 255; val cb = c and 255
                Box(Modifier.size(22.dp)
                    .background(Color(c), CircleShape)
                    .border(if (cr == r && cg == g && cb == b) 2.dp else 0.dp, Cyan, CircleShape)
                    .clickable { onChange(cr, cg, cb); hex = "%02X%02X%02X".format(cr, cg, cb) })
            }
        }
        OutlinedTextField(value = hex, onValueChange = { v ->
            hex = v.uppercase().filter { it.isDigit() || it in 'A'..'F' }.take(6)
            if (hex.length == 6) runCatching {
                val i = hex.toLong(16).toInt()
                onChange(i shr 16 and 255, i shr 8 and 255, i and 255)
            }
        }, label = { Text("RRGGBB", fontSize = 11.sp) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cyan, unfocusedBorderColor = CyanDark,
                focusedTextColor = TxtPri, unfocusedTextColor = TxtPri,
                focusedLabelColor = TxtSec, unfocusedLabelColor = TxtSec))
    }
}

@Composable
private fun StepControl(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TxtSec, fontSize = 12.sp, modifier = Modifier.width(58.dp))
        Text("$value", color = TxtPri, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp))
        Spacer(Modifier.weight(1f))
        listOf(-5, -1, +1, +5).forEach { step ->
            OutlinedButton(onClick = { onChange((value + step).coerceIn(min, max)) },
                modifier = Modifier.size(38.dp).padding(2.dp), contentPadding = PaddingValues(0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanDark),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)) {
                Text(if (step > 0) "+$step" else "$step", fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun StudioSlider(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TxtSec, fontSize = 12.sp)
        Text("$value", color = TxtPri, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) },
        valueRange = min.toFloat()..max.toFloat(),
        colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = CyanMid, inactiveTrackColor = CyanDark))
}

// ── Geração de dígitos (Dispatchers.Default) ──────────────────────────────────

private fun applyDigits(face: FaceFile, typeface: Typeface, cHora: Int, cMin: Int, cDia: Int, cDow: Int): FaceFile {
    val newImages = face.images.map { elem ->
        when (elem.type) {
            1  -> regenerateDigit(elem, listOf("0","1","2"), cHora, typeface)
            2  -> regenerateDigit(elem, (0..9).map(Int::toString), cHora, typeface)
            3  -> regenerateDigit(elem, listOf("0","1","2","3","4","5"), cMin, typeface)
            4  -> regenerateDigit(elem, (0..9).map(Int::toString), cMin, typeface)
            10 -> {
                val texts = if (elem.sprites.size == 4) listOf("0","1","2","3") else (0..9).map(Int::toString)
                regenerateDigit(elem, texts, cDia, typeface)
            }
            11 -> regenerateDigit(elem, (0..9).map(Int::toString), cDia, typeface)
            31 -> regenerateDigit(elem, listOf("DOM","SEG","TER","QUA","QUI","SEX","SÁB"), cDow, typeface)
            else -> elem
        }
    }
    return face.copy(images = newImages)
}

private fun regenerateDigit(elem: ImageElem, texts: List<String>, color: Int, typeface: Typeface): ImageElem {
    val s0 = elem.sprites.firstOrNull() ?: return elem
    val w = s0.w; val h = s0.h
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textAlign = Paint.Align.CENTER
    }
    val bounds = Rect()
    var sz = 200f
    outer@ while (sz >= 6f) {
        paint.textSize = sz
        for (t in texts) {
            paint.getTextBounds(t, 0, t.length, bounds)
            if (bounds.width() > w - 2 || bounds.height() > h - 2) { sz -= 1f; continue@outer }
        }
        break
    }
    paint.color = color
    val newSprites = texts.map { text ->
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, w / 2f, h / 2f - (bounds.top + bounds.bottom) / 2f, paint)
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        Sprite.fromArgb(w, h, px)
    }
    val final = if (elem.sprites.size > newSprites.size)
        newSprites + elem.sprites.subList(newSprites.size, elem.sprites.size)
    else newSprites
    return elem.copy(sprites = final)
}
