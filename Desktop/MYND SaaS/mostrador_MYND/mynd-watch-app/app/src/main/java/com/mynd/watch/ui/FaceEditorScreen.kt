package com.mynd.watch.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mynd.watch.data.AppLog
import com.mynd.watch.face.TextElem
import com.mynd.watch.face.WatchFace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Color palette for element editing
private val COLOR_PALETTE = listOf(
    0xFFD0E8FF, 0xFF00C8FF, 0xFF00FF99, 0xFFFFFF00,
    0xFFFF9900, 0xFFFF3355, 0xFFFF66FF, 0xFFFFFFFF
).map { Color(it) }

@Composable
fun FaceEditorScreen(
    ctx: Context,
    assetNames: List<String>,
    loadAsset: (String) -> ByteArray,
    loadUri: (Uri) -> ByteArray?,
    loadBitmap: (Uri) -> Bitmap?,
    onSend: (ByteArray) -> Unit,
) {
    var face by remember { mutableStateOf<WatchFace?>(null) }
    if (face == null) {
        TemplateGallery(assetNames, loadAsset, loadUri) { bytes, name ->
            face = WatchFace(bytes, name)
        }
    } else {
        FaceEditor(face!!, ctx, loadBitmap, onSend) { face = null }
    }
}

// ── Template gallery ──────────────────────────────────────────────────

@Composable
private fun TemplateGallery(
    assetNames: List<String>,
    loadAsset: (String) -> ByteArray,
    loadUri: (Uri) -> ByteArray?,
    onSelect: (ByteArray, String) -> Unit,
) {
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = loadUri(uri) ?: return@rememberLauncherForActivityResult
        onSelect(bytes, uri.lastPathSegment?.substringAfterLast('/') ?: "mostrador")
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("MODELOS", color = TxtSec, fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(4.dp))
            Text("Selecione um modelo para editar", color = TxtPri, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text("Estilo analógico não é suportado pelo firmware JieLi — apenas digital.", color = TxtSec, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
        }
        items(assetNames) { name ->
            val bytes = remember(name) { loadAsset(name) }
            val wf    = remember(name) { WatchFace(bytes, name.removeSuffix(".bin")) }
            val prev  = remember(name) { wf.decodePreview().asImageBitmap() }
            HudCard {
                Row(
                    Modifier.clickable { onSelect(bytes, name.removeSuffix(".bin")) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(prev, null, Modifier.size(52.dp, 71.dp).clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.FillBounds)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(wf.name, color = TxtPri, fontWeight = FontWeight.SemiBold)
                        Text("${wf.textElems.size} campos de texto", color = TxtSec, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.Edit, null, tint = CyanMid, modifier = Modifier.size(18.dp))
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, CyanDark),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)
            ) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Importar arquivo .bin")
            }
        }
    }
}

// ── Face editor ───────────────────────────────────────────────────────

@Composable
private fun FaceEditor(
    face: WatchFace,
    ctx: Context,
    loadBitmap: (Uri) -> Bitmap?,
    onSend: (ByteArray) -> Unit,
    onBack: () -> Unit,
) {
    val elems      = remember(face) { face.textElems.toMutableStateList() }
    var customBg   by remember { mutableStateOf<Bitmap?>(null) }
    var bgImage    by remember { mutableStateOf<ImageBitmap?>(null) }
    var selectedIdx by remember { mutableIntStateOf(-1) }
    var panelTab   by remember { mutableIntStateOf(0) }

    LaunchedEffect(face, customBg) {
        bgImage = withContext(Dispatchers.Default) {
            (customBg ?: face.decodeBg()).asImageBitmap()
        }
    }

    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        customBg = loadBitmap(uri)
    }
    val savePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = face.build(elems, customBg)
        runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
            .onSuccess { AppLog.add("${face.name}.bin salvo") }
            .onFailure { AppLog.add("Erro ao salvar: ${it.message}") }
    }

    Column(Modifier.fillMaxSize()) {
        // ── Barra superior ──
        Surface(color = BgCard) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Cyan)
                }
                Text(face.name, color = TxtPri, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { savePicker.launch("${face.name}.bin") }) {
                    Text("Salvar .bin", color = CyanMid, fontSize = 12.sp)
                }
                Button(
                    onClick = { onSend(face.build(elems, customBg)) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanDark, contentColor = Cyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Bluetooth, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Enviar", fontSize = 12.sp)
                }
            }
        }

        // ── Preview do mostrador ──
        bgImage?.let { bg ->
            WatchCanvas(
                bgImage = bg,
                elems = elems,
                selectedIdx = selectedIdx,
                onSelectElem = { idx -> selectedIdx = idx; if (idx >= 0) panelTab = 1 },
                onMoveElem = { idx, dx, dy ->
                    elems[idx] = elems[idx].copy(
                        x = (elems[idx].x + dx).coerceIn(0, 220),
                        y = (elems[idx].y + dy).coerceIn(0, 270)
                    )
                }
            )
        }

        HorizontalDivider(color = CyanDark)

        // ── Abas do painel ──
        TabRow(
            selectedTabIndex = panelTab,
            containerColor = BgCard,
            contentColor = Cyan,
            indicator = { tabs ->
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabs[panelTab]), color = Cyan)
            }
        ) {
            listOf("Fundo", "Elementos", "Cor global").forEachIndexed { i, title ->
                Tab(selected = panelTab == i, onClick = { panelTab = i; if (i != 1) selectedIdx = -1 },
                    text = { Text(title, fontSize = 11.sp) })
            }
        }

        // ── Conteúdo do painel ──
        Box(Modifier.weight(1f)) {
            when (panelTab) {
                0 -> BackgroundPanel(onPickGallery = {
                    bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                1 -> ElementsPanel(
                    elems = elems,
                    selectedIdx = selectedIdx,
                    onSelect = { selectedIdx = it },
                    onColor = { idx, r, g, b -> elems[idx] = elems[idx].copy(r = r, g = g, b = b) },
                    onAlign = { idx, al ->
                        val el = elems[idx]
                        val newX = when (al) {
                            2 -> (120 - el.largura / 2).coerceIn(0, 220)
                            3 -> (240 - el.largura - 8).coerceIn(0, 220)
                            else -> 8
                        }
                        elems[idx] = el.copy(alignment = al, x = newX)
                    },
                    onPositionY = { idx, y -> elems[idx] = elems[idx].copy(y = y) }
                )
                2 -> GlobalColorPanel { r, g, b ->
                    for (i in elems.indices) elems[i] = elems[i].copy(r = r, g = g, b = b)
                }
            }
        }
    }
}

// ── Watch canvas ──────────────────────────────────────────────────────

@Composable
private fun WatchCanvas(
    bgImage: ImageBitmap,
    elems: List<TextElem>,
    selectedIdx: Int,
    onSelectElem: (Int) -> Unit,
    onMoveElem: (Int, Int, Int) -> Unit,
) {
    val currentIdx   = rememberUpdatedState(selectedIdx)
    val currentElems = rememberUpdatedState(elems.toList())

    Box(
        Modifier.fillMaxWidth().height(260.dp).background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.aspectRatio(240f / 296f).fillMaxHeight()
        ) {
            Image(bgImage, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val scaleX = 240f / size.width
                            val scaleY = 296f / size.height
                            val fx = offset.x * scaleX
                            val fy = offset.y * scaleY
                            val hit = currentElems.value.indexOfFirst { el ->
                                val w = maxOf(el.largura, 40).toFloat()
                                fx in el.x.toFloat()..(el.x + w) && fy in el.y.toFloat()..(el.y + 22f)
                            }
                            onSelectElem(hit)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { _, amount ->
                            val idx = currentIdx.value
                            if (idx >= 0) {
                                onMoveElem(
                                    idx,
                                    (amount.x * (240f / size.width)).toInt(),
                                    (amount.y * (296f / size.height)).toInt()
                                )
                            }
                        }
                    }
            ) {
                elems.forEachIndexed { idx, el ->
                    val sel = idx == currentIdx.value
                    val sx = 240f / size.width
                    val sy = 296f / size.height
                    val px = el.x / sx
                    val py = el.y / sy
                    val pw = maxOf(el.largura.toFloat(), 40f) / sx
                    val ph = 20f / sy
                    val col = Color(el.r, el.g, el.b)
                    drawRoundRect(col.copy(alpha = if (sel) 0.22f else 0.07f),
                        Offset(px, py), Size(pw, ph), CornerRadius(3f))
                    drawRoundRect(col.copy(alpha = if (sel) 1f else 0.45f),
                        Offset(px, py), Size(pw, ph), CornerRadius(3f),
                        style = Stroke(if (sel) 2.5f else 0.8f))
                }
            }
        }
        if (elems.isEmpty()) {
            Text("Sem campos de texto editáveis", color = TxtSec, fontSize = 12.sp)
        }
    }
}

// ── Panels ────────────────────────────────────────────────────────────

@Composable
private fun BackgroundPanel(onPickGallery: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("PLANO DE FUNDO", color = TxtSec, fontSize = 10.sp, letterSpacing = 2.sp)
        HudCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Escolha uma imagem da galeria.\nSerá redimensionada para 240×296 px.", color = TxtSec, fontSize = 12.sp)
                CyanButton(Icons.Default.Image, "Escolher da galeria") { onPickGallery() }
            }
        }
        HudCard {
            Column(Modifier.padding(12.dp)) {
                Text("INFORMAÇÕES", color = TxtSec, fontSize = 10.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(6.dp))
                Text("• Resolução: 240 × 296 pixels\n• Formato interno: RGB565 big-endian\n• O firmware JieLi não suporta estilo analógico\n• Tipo de fonte é fixo no firmware", color = TxtSec, fontSize = 11.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun ElementsPanel(
    elems: List<TextElem>,
    selectedIdx: Int,
    onSelect: (Int) -> Unit,
    onColor: (Int, Int, Int, Int) -> Unit,
    onAlign: (Int, Int) -> Unit,
    onPositionY: (Int, Int) -> Unit,
) {
    if (elems.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Nenhum campo de texto neste mostrador", color = TxtSec)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(elems) { idx, el ->
            val isSel = idx == selectedIdx
            HudCard(if (isSel) Cyan else CyanDark) {
                Column(
                    Modifier.clickable { onSelect(if (isSel) -1 else idx) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).background(Color(el.r, el.g, el.b), CircleShape)
                            .border(1.dp, TxtSec, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(el.name, color = if (isSel) Cyan else TxtPri, fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f))
                        Text("(${el.x}, ${el.y})", color = TxtSec, fontSize = 10.sp)
                    }
                    if (isSel) {
                        // Color palette
                        Text("COR", color = TxtSec, fontSize = 9.sp, letterSpacing = 2.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            COLOR_PALETTE.forEach { c ->
                                val cr = (c.red * 255).toInt()
                                val cg = (c.green * 255).toInt()
                                val cb = (c.blue * 255).toInt()
                                val isCurrent = el.r == cr && el.g == cg && el.b == cb
                                Box(
                                    Modifier.size(28.dp).background(c, CircleShape)
                                        .then(if (isCurrent) Modifier.border(2.dp, Cyan, CircleShape) else Modifier)
                                        .clickable { onColor(idx, cr, cg, cb) }
                                )
                            }
                        }
                        // Alignment
                        Text("ALINHAMENTO", color = TxtSec, fontSize = 9.sp, letterSpacing = 2.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0 to "◄ Esq", 2 to "■ Ctr", 3 to "► Dir").forEach { (al, label) ->
                                val sel2 = el.alignment == al || (al == 0 && el.alignment == 1)
                                OutlinedButton(
                                    onClick = { onAlign(idx, al) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(4.dp),
                                    border = BorderStroke(1.dp, if (sel2) Cyan else CyanDark),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (sel2) Cyan else TxtSec,
                                        containerColor = if (sel2) CyanDark else Color.Transparent
                                    )
                                ) { Text(label, fontSize = 11.sp) }
                            }
                        }
                        // Vertical position
                        Text("POSIÇÃO VERTICAL", color = TxtSec, fontSize = 9.sp, letterSpacing = 2.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Topo" to 20, "Cima" to 70, "Meio" to 130, "Baixo" to 200, "Base" to 255)
                                .forEach { (label, yv) ->
                                    val sel2 = el.y in (yv - 12)..(yv + 12)
                                    OutlinedButton(
                                        onClick = { onPositionY(idx, yv) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(2.dp),
                                        border = BorderStroke(1.dp, if (sel2) Cyan else CyanDark),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (sel2) Cyan else TxtSec,
                                            containerColor = if (sel2) CyanDark else Color.Transparent
                                        )
                                    ) { Text(label, fontSize = 10.sp) }
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalColorPanel(onApplyAll: (Int, Int, Int) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("COR GLOBAL", color = TxtSec, fontSize = 10.sp, letterSpacing = 2.sp)
        Text("Aplica a mesma cor a todos os campos de texto de uma vez.", color = TxtSec, fontSize = 12.sp)
        HudCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                COLOR_PALETTE.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { c ->
                            val cr = (c.red * 255).toInt()
                            val cg = (c.green * 255).toInt()
                            val cb = (c.blue * 255).toInt()
                            Column(
                                Modifier.weight(1f).clickable { onApplyAll(cr, cg, cb) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(Modifier.size(40.dp).background(c, RoundedCornerShape(8.dp))
                                    .border(1.dp, TxtSec.copy(alpha = 0.3f), RoundedCornerShape(8.dp)))
                                Spacer(Modifier.height(4.dp))
                                Text("#%02X%02X%02X".format(cr, cg, cb), color = TxtSec, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
