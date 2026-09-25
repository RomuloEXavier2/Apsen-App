package com.mynd.watch.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mynd.watch.data.Library
import com.mynd.watch.service.WatchService
import com.mynd.watch.studio.CurrentSrc
import com.mynd.watch.studio.DataContract
import com.mynd.watch.studio.FaceFile
import com.mynd.watch.studio.FaceLibrary
import com.mynd.watch.studio.FaceValidator
import com.mynd.watch.studio.IconSrc
import com.mynd.watch.studio.PairSrc
import com.mynd.watch.studio.Rgb565 as StudioRgb565
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(sendToWatch: (FaceLibrary.Ref) -> Unit) {
    var previewRef by remember { mutableStateOf<FaceLibrary.Ref?>(null) }
    previewRef?.let { ref ->
        PreviewScreen(ref = ref, sendToWatch = sendToWatch, onBack = { previewRef = null })
        return
    }
    LibraryContent(sendToWatch = sendToWatch, onPreview = { previewRef = it })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryContent(sendToWatch: (FaceLibrary.Ref) -> Unit, onPreview: (FaceLibrary.Ref) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed by WatchService.installedFlow.collectAsState()
    val statusSvc by WatchService.statusFlow.collectAsState()
    val isSending  = statusSvc.startsWith("Enviando")
    var entries by remember { mutableStateOf(Library.lib.list()) }
    var selectedEntry by remember { mutableStateOf<FaceLibrary.Entry?>(null) }
    var showVersionsFor by remember { mutableStateOf<FaceLibrary.Entry?>(null) }
    var showNameDialog by remember { mutableStateOf<ByteArray?>(null) }
    var nameInput by remember { mutableStateOf("") }

    fun reload() { entries = Library.lib.list() }

    // Importar .bin
    val importBinLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val bytes = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null) { withContext(Dispatchers.Main) { Toast.makeText(ctx, "Erro ao ler arquivo", Toast.LENGTH_SHORT).show() }; return@launch }
            val ok = runCatching { FaceFile.parse(bytes) }.isSuccess
            withContext(Dispatchers.Main) {
                if (!ok) { Toast.makeText(ctx, "Arquivo não é um mostrador JL", Toast.LENGTH_SHORT).show(); return@withContext }
                val suggestion = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')?.removeSuffix(".bin") ?: "mostrador"
                nameInput = suggestion
                showNameDialog = bytes
            }
        }
    }

    // Backup (exportar zip)
    val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { Library.lib.exportZip(it) } }
            withContext(Dispatchers.Main) { Toast.makeText(ctx, "Backup salvo", Toast.LENGTH_SHORT).show() }
        }
    }

    // Restaurar (importar zip)
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching { ctx.contentResolver.openInputStream(uri)?.use { Library.lib.importZip(it) } }
            withContext(Dispatchers.Main) { Toast.makeText(ctx, "Backup restaurado (nada foi apagado)", Toast.LENGTH_SHORT).show(); reload() }
        }
    }

    // Exportar .bin
    var exportRef by remember { mutableStateOf<FaceLibrary.Ref?>(null) }
    val exportBinLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val ref = exportRef ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(Library.lib.bytes(ref)) } }
            withContext(Dispatchers.Main) { Toast.makeText(ctx, "Exportado", Toast.LENGTH_SHORT).show() }
        }
    }

    // Duplicar
    var dupPending by remember { mutableStateOf<FaceLibrary.Ref?>(null) }
    var dupName by remember { mutableStateOf("") }

    // Contrato de dados
    var showContractFor by remember { mutableStateOf<FaceLibrary.Entry?>(null) }
    var ccurrent by remember { mutableStateOf(CurrentSrc.REAL_WEATHER) }
    var cpair    by remember { mutableStateOf(PairSrc.REAL_WEATHER) }
    var cicon    by remember { mutableStateOf(IconSrc.REAL_WEATHER) }
    val contractIssues = remember(ccurrent, cpair, cicon, showContractFor?.id) {
        val ent = showContractFor ?: return@remember emptyList<FaceValidator.Issue>()
        runCatching {
            val bytes = Library.lib.bytes(FaceLibrary.Ref(ent.id, ent.latest!!.n))
            FaceValidator.check(FaceFile.parse(bytes), DataContract(ccurrent, cpair, cicon))
        }.getOrElse { emptyList() }
    }

    Column(Modifier.fillMaxSize()) {
        // Topo com título e ações
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("BIBLIOTECA", color = TxtSec, fontSize = 10.sp, letterSpacing = 2.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlineAction(Icons.Default.FileDownload, "Importar .bin") { importBinLauncher.launch(arrayOf("*/*")) }
                OutlineAction(Icons.Default.Backup, "Backup") { backupLauncher.launch("mynd_backup_${sdf.format(Date())}.zip") }
                OutlineAction(Icons.Default.Restore, "Restaurar") { restoreLauncher.launch(arrayOf("application/zip")) }
            }
        }
        HorizontalDivider(color = CyanDark, thickness = 1.dp)

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries) { entry ->
                FaceEntryCard(entry, installed, onClick = { showVersionsFor = entry })
            }
        }
    }

    // Diálogo: nome para .bin importado
    showNameDialog?.let { bytes ->
        AlertDialog(
            onDismissRequest = { showNameDialog = null },
            containerColor = BgCard,
            title = { Text("Nome do mostrador", color = TxtPri) },
            text = { HudTextField(nameInput, { nameInput = it }, "Nome") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        runCatching { Library.lib.save(nameInput.trim().ifEmpty { "mostrador" }, bytes, "importado") }
                        withContext(Dispatchers.Main) { reload(); showNameDialog = null }
                    }
                }) { Text("Salvar", color = Cyan) }
            },
            dismissButton = { TextButton(onClick = { showNameDialog = null }) { Text("Cancelar", color = TxtSec) } }
        )
    }

    // Diálogo: nome para duplicar
    dupPending?.let { ref ->
        AlertDialog(
            onDismissRequest = { dupPending = null },
            containerColor = BgCard,
            title = { Text("Duplicar como...", color = TxtPri) },
            text = { HudTextField(dupName, { dupName = it }, "Nome do novo mostrador") },
            confirmButton = {
                TextButton(onClick = {
                    val entry = entries.firstOrNull { it.id == ref.id }
                    scope.launch(Dispatchers.IO) {
                        runCatching { Library.lib.save(dupName.trim().ifEmpty { "cópia" }, Library.lib.bytes(ref), "duplicado de ${ref.id} v${ref.v}", entry?.contract) }
                        withContext(Dispatchers.Main) { reload(); dupPending = null }
                    }
                }) { Text("Duplicar", color = Cyan) }
            },
            dismissButton = { TextButton(onClick = { dupPending = null }) { Text("Cancelar", color = TxtSec) } }
        )
    }

    // Diálogo: contrato de dados
    showContractFor?.let { entry ->
        AlertDialog(
            onDismissRequest = { showContractFor = null },
            containerColor = BgCard,
            title = { Text("Dados do mostrador", color = TxtPri) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HudDropdown("Número (campos 13/24)", ccurrent,
                        listOf(CurrentSrc.REAL_WEATHER to "Clima real", CurrentSrc.BAMBU_PERCENT to "Impressão %",
                               CurrentSrc.BAMBU_NOZZLE to "Bico °C", CurrentSrc.BAMBU_BED to "Mesa °C",
                               CurrentSrc.BAMBU_LAYER to "Camada atual")
                    ) { ccurrent = it }
                    HudDropdown("Par (campo 33)", cpair,
                        listOf(PairSrc.REAL_WEATHER to "Clima real (mín/máx)", PairSrc.BAMBU_REMAINING_HM to "Tempo restante h/min",
                               PairSrc.BAMBU_PERCENT_REMAINING_MIN to "% / minutos", PairSrc.BAMBU_NOZZLE_TARGET to "Bico atual/alvo",
                               PairSrc.BAMBU_BED_TARGET to "Mesa atual/alvo", PairSrc.BAMBU_LAYERS to "Camada/total")
                    ) { cpair = it }
                    HudDropdown("Ícone (imagem 6)", cicon,
                        listOf(IconSrc.REAL_WEATHER to "Clima real", IconSrc.BAMBU_PROGRESS_16 to "Barra de progresso (16)",
                               IconSrc.BAMBU_NOZZLE_LEVEL to "Bico (níveis desenhados)")
                    ) { cicon = it }
                    if (contractIssues.isNotEmpty()) {
                        HorizontalDivider(color = CyanDark.copy(alpha = 0.5f))
                        contractIssues.forEach { issue ->
                            val color = if (issue.level == FaceValidator.Level.ERRO) RedBad else Orange
                            Text("• ${issue.msg}", color = color, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Library.lib.setContract(entry.id, DataContract(ccurrent, cpair, cicon))
                    reload(); showContractFor = null
                }) { Text("Salvar", color = Cyan) }
            },
            dismissButton = { TextButton(onClick = { showContractFor = null }) { Text("Cancelar", color = TxtSec) } }
        )
    }

    // BottomSheet: versões
    showVersionsFor?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { showVersionsFor = null; reload() },
            containerColor = BgCard
        ) {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.name, color = TxtPri, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        ccurrent = entry.contract.current
                        cpair    = entry.contract.pair
                        cicon    = entry.contract.icon
                        showContractFor = entry
                        showVersionsFor = null
                    }) { Text("Dados do mostrador...", color = TxtSec, fontSize = 12.sp) }
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = CyanDark.copy(alpha = 0.4f))
                Spacer(Modifier.height(4.dp))
                entry.versions.sortedByDescending { it.n }.forEach { ver ->
                    val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                    val dateStr = fmt.format(Date(ver.created))
                    val isInstalled = installed?.id == entry.id && installed?.v == ver.n
                    val isApproved = ver.approved
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("v${ver.n}", color = TxtPri, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("$dateStr  ${ver.note}", color = TxtSec, fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (isInstalled) VersionBadge("INSTALADO", GreenOk)
                                if (isApproved)  VersionBadge("APROVADO",  Cyan)
                            }
                        }
                        // Ações da versão
                        val ref = FaceLibrary.Ref(entry.id, ver.n)
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onPreview(ref); showVersionsFor = null }) { Text("Pré-visualizar", color = Cyan, fontSize = 12.sp) }
                            TextButton(onClick = { sendToWatch(ref); showVersionsFor = null }, enabled = !isSending) {
                                Text("Enviar ao relógio", color = if (isSending) TxtSec else Cyan, fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                Toast.makeText(ctx, "Disponível na 0.2.5", Toast.LENGTH_SHORT).show()
                            }) { Text("Editar no Estúdio", color = TxtSec, fontSize = 12.sp) }
                            TextButton(onClick = { Library.lib.markApproved(ref); reload(); showVersionsFor = Library.lib.get(entry.id) }) {
                                Text("Marcar aprovado", color = TxtSec, fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                exportRef = ref
                                exportBinLauncher.launch("${entry.id}_v${ver.n}.bin")
                            }) { Text("Exportar .bin", color = TxtSec, fontSize = 12.sp) }
                            TextButton(onClick = {
                                dupName = "${entry.name} cópia"
                                dupPending = ref
                                showVersionsFor = null
                            }) { Text("Duplicar como novo", color = TxtSec, fontSize = 12.sp) }
                        }
                    }
                    HorizontalDivider(color = CyanDark.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun FaceEntryCard(entry: FaceLibrary.Entry, installed: FaceLibrary.Ref?, onClick: () -> Unit) {
    var preview by remember(entry.id) { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(entry.id, entry.latest?.n) {
        scope.launch(Dispatchers.IO) {
            val bmp = runCatching {
                val bytes = Library.lib.bytes(FaceLibrary.Ref(entry.id, entry.latest!!.n))
                val face = FaceFile.parse(bytes)
                val px = StudioRgb565.decode(face.preview, 156, 214)
                Bitmap.createBitmap(px, 156, 214, Bitmap.Config.ARGB_8888)
            }.getOrNull()
            withContext(Dispatchers.Main) { preview = bmp }
        }
    }

    HudCard {
        Row(Modifier.clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Miniatura 52x71dp
            Box(Modifier.size(52.dp, 71.dp)) {
                preview?.let {
                    Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } ?: Surface(Modifier.fillMaxSize(), color = BgField, shape = RoundedCornerShape(4.dp)) {}
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, color = TxtPri, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                val latest = entry.latest
                if (latest != null) Text("v${latest.n} · ${entry.versions.size} versões", color = TxtSec, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (installed?.id == entry.id)
                        VersionBadge("INSTALADO", GreenOk)
                    entry.approved?.let { ap ->
                        VersionBadge("APROVADO v${ap.n}", Cyan)
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = TxtSec, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun VersionBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), letterSpacing = 1.sp)
    }
}


