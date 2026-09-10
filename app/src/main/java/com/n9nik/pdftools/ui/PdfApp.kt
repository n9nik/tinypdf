package com.n9nik.pdftools.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.n9nik.pdftools.ads.BannerAd
import com.n9nik.pdftools.pdf.FileUtils
import com.n9nik.pdftools.pdf.PdfOps
import com.n9nik.pdftools.pdf.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

private val Indigo = Color(0xFF4F46E5)

@Composable
fun TinyPdfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Indigo,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE0E7FF),
            onPrimaryContainer = Color(0xFF312E81),
            secondary = Color(0xFF0EA5E9),
            background = Color(0xFFF8FAFC),
            surface = Color.White,
            surfaceVariant = Color(0xFFF1F5F9),
        ),
        content = content,
    )
}

enum class Tool(
    val title: String,
    val blurb: String,
    val icon: ImageVector,
) {
    MERGE("Merge", "Combine PDFs into one", Icons.Filled.MergeType),
    SPLIT("Split", "Extract page ranges", Icons.Filled.CallSplit),
    COMPRESS("Compress", "Shrink file size", Icons.Filled.Compress),
    WATERMARK("Watermark", "Stamp text diagonally", Icons.Filled.BrandingWatermark),
}

sealed interface JobState {
    data object Idle : JobState
    data class Working(val message: String) : JobState
    data class Failed(val message: String) : JobState
}

data class SavedFile(val name: String, val uri: Uri, val sizeText: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfApp(adsEnabled: Boolean, onPrivacyOptions: () -> Unit) {
    var tool by remember { mutableStateOf<Tool?>(null) }
    TinyPdfTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("TinyPDF", fontWeight = FontWeight.Bold)
                            Text(
                                "Merge · Split · Compress · Watermark",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        if (tool != null) {
                            IconButton(onClick = { tool = null }) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            bottomBar = {
                Column {
                    if (adsEnabled) BannerAd()
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val current = tool
                if (current == null) {
                    HomeScreen(onPickTool = { tool = it }, onPrivacyOptions = onPrivacyOptions)
                } else {
                    when (current) {
                        Tool.MERGE -> MergeScreen()
                        Tool.SPLIT -> SplitScreen()
                        Tool.COMPRESS -> CompressScreen()
                        Tool.WATERMARK -> WatermarkScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onPickTool: (Tool) -> Unit, onPrivacyOptions: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "What do you want to do?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        for (row in Tool.entries.chunked(2)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (t in row) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp),
                        onClick = { onPickTool(t) },
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                t.icon,
                                contentDescription = null,
                                tint = Indigo,
                                modifier = Modifier.size(32.dp),
                            )
                            Text(t.title, fontWeight = FontWeight.Bold)
                            Text(
                                t.blurb,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("100% offline", fontWeight = FontWeight.Bold)
                Text(
                    "Your documents never leave this phone. No accounts, no daily limits, no paywalls. Files save to Downloads/TinyPDF.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onPrivacyOptions) { Text("Ad privacy options") }
    }
}

// ---------------------------------------------------------------------------
// Shared components
// ---------------------------------------------------------------------------

@Composable
fun PasswordField(password: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = password,
        onValueChange = onChange,
        label = { Text("Password (only if the PDF is encrypted)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun JobStatus(state: JobState) {
    when (state) {
        is JobState.Working -> {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(state.message, style = MaterialTheme.typography.bodySmall)
        }
        is JobState.Failed -> {
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2))) {
                Text(
                    state.message,
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFF991B1B),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        JobState.Idle -> {}
    }
}

@Composable
fun SavedFileCard(file: SavedFile) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF15803D))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${file.sizeText} · Saved to Downloads/TinyPDF",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { FileUtils.sharePdf(context, file.uri) }) {
                Icon(Icons.Filled.Share, contentDescription = "Share")
            }
        }
    }
}

@Composable
fun PdfRow(
    name: String,
    subtitle: String,
    onRemove: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = Indigo)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onMoveUp != null) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                }
            }
            if (onMoveDown != null) {
                IconButton(onClick = onMoveDown, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                }
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove")
                }
            }
        }
    }
}

/** Horizontal strip of page thumbnails (first pages only, rendered lazily). */
@Composable
fun ThumbStrip(uri: Uri, pageCount: Int) {
    val context = LocalContext.current
    val cache = remember(uri) { mutableMapOf<Int, Bitmap>() }
    val shown = minOf(pageCount, 12)
    if (shown <= 0) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "$pageCount page${if (pageCount == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            itemsIndexed((0 until shown).toList()) { _, pageIndex ->
                var bmp by remember(uri, pageIndex) { mutableStateOf(cache[pageIndex]) }
                LaunchedEffect(uri, pageIndex) {
                    if (bmp == null) {
                        bmp = withContext(Dispatchers.IO) {
                            FileUtils.renderThumbnail(context, uri, pageIndex, 160)
                        }?.also { cache[pageIndex] = it }
                    }
                }
                val b = bmp
                if (b != null) {
                    Image(
                        bitmap = b.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        modifier = Modifier
                            .size(width = 80.dp, height = 110.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White),
                    )
                } else {
                    Box(
                        Modifier.size(width = 80.dp, height = 110.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${pageIndex + 1}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (pageCount > shown) {
            Text(
                "+ ${pageCount - shown} more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun persistPermission(context: android.content.Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

// ---------------------------------------------------------------------------
// Tool screens
// ---------------------------------------------------------------------------

data class PickedDoc(val uri: Uri, val name: String, val sizeText: String)

@Composable
private fun rememberPickedDoc(uri: Uri): PickedDoc {
    val context = LocalContext.current
    return remember(uri) {
        PickedDoc(
            uri,
            FileUtils.displayName(context, uri),
            formatBytes(FileUtils.sizeOf(context, uri)),
        )
    }
}

@Composable
private fun docPages(uri: Uri, password: String): Int? {
    val context = LocalContext.current
    var pages by remember(uri, password) { mutableStateOf<Int?>(null) }
    LaunchedEffect(uri, password) {
        pages = withContext(Dispatchers.IO) {
            runCatching { PdfOps.pageCount(context, uri, password.ifEmpty { null }) }.getOrNull()
        }
    }
    return pages
}

@Composable
private fun MergeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val docs = remember { mutableStateListOf<Uri>() }
    var password by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<JobState>(JobState.Idle) }
    var saved by remember { mutableStateOf<SavedFile?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        for (uri in uris) {
            persistPermission(context, uri)
            if (docs.none { it == uri }) docs.add(uri)
        }
    }

    ToolColumn {
        Text("Pick two or more PDFs. They merge in the order shown — use the arrows to reorder.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add PDFs (${docs.size})")
        }
        for ((index, uri) in docs.withIndex()) {
            val doc = rememberPickedDoc(uri)
            val pages = docPages(uri, password)
            PdfRow(
                name = doc.name,
                subtitle = "${doc.sizeText}" + (pages?.let { " · $it pages" } ?: ""),
                onRemove = { docs.remove(uri); saved = null },
                onMoveUp = { if (index > 0) Collections.swap(docs, index, index - 1) },
                onMoveDown = { if (index < docs.lastIndex) Collections.swap(docs, index, index + 1) },
            )
        }
        PasswordField(password) { password = it; saved = null }
        Button(
            onClick = {
                saved = null
                job = JobState.Working("Merging ${docs.size} PDFs…")
                scope.launch(Dispatchers.IO) {
                    try {
                        val first = FileUtils.baseName(FileUtils.displayName(context, docs.first()))
                        val outName = "${first}_merged.pdf"
                        val uri = FileUtils.saveToDownloads(context, outName) { out ->
                            PdfOps.merge(context, docs.toList(), password.ifEmpty { null }, out)
                        }
                        val size = formatBytes(FileUtils.sizeOf(context, uri))
                        withContext(Dispatchers.Main) {
                            saved = SavedFile(outName, uri, size)
                            job = JobState.Idle
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            job = JobState.Failed(e.message ?: "Merge failed.")
                        }
                    }
                }
            },
            enabled = docs.size >= 2 && job is JobState.Idle,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.MergeType, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Merge into one PDF")
        }
        JobStatus(job)
        saved?.let { SavedFileCard(it) }
    }
}

@Composable
private fun SplitScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var rangesText by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<JobState>(JobState.Idle) }
    val saved = remember { mutableStateListOf<SavedFile>() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked ->
        if (picked != null) {
            persistPermission(context, picked)
            uri = picked
            saved.clear()
            job = JobState.Idle
        }
    }

    val current = uri
    val pages = if (current != null) docPages(current, password) else null

    ToolColumn {
        Text("Extract pages into separate PDFs. Ranges like 1-3, 5, 7-9.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (current == null) "Pick a PDF" else "Pick a different PDF")
        }
        if (current != null) {
            val doc = rememberPickedDoc(current)
            PdfRow(name = doc.name, subtitle = doc.sizeText + (pages?.let { " · $it pages" } ?: ""))
            if (pages != null && pages > 0) ThumbStrip(current, pages)
            PasswordField(password) { password = it; saved.clear() }
            OutlinedTextField(
                value = rangesText,
                onValueChange = { rangesText = it; saved.clear() },
                label = { Text("Page ranges, e.g. 1-3, 5") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    saved.clear()
                    scope.launch(Dispatchers.IO) {
                        try {
                            val ranges = PdfOps.parseRanges(rangesText, pages ?: 0)
                            withContext(Dispatchers.Main) {
                                job = JobState.Working("Splitting into ${ranges.size} files…")
                            }
                            val base = FileUtils.baseName(doc.name)
                            val results = mutableListOf<SavedFile>()
                            PdfOps.split(
                                context, current, password.ifEmpty { null }, ranges,
                            ) { partIndex, _ ->
                                val range = ranges[partIndex]
                                val outName = "${base}_p${range.first + 1}-${range.last + 1}.pdf"
                                val savedUri = FileUtils.createDownloadEntry(context, outName)
                                results.add(SavedFile(outName, savedUri, ""))
                                context.contentResolver.openOutputStream(savedUri)
                                    ?: throw java.io.IOException("Could not write $outName")
                            }
                            withContext(Dispatchers.Main) {
                                saved.addAll(results.map {
                                    it.copy(sizeText = formatBytes(FileUtils.sizeOf(context, it.uri)))
                                })
                                job = JobState.Idle
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                job = JobState.Failed(e.message ?: "Split failed.")
                            }
                        }
                    }
                },
                enabled = current != null && pages != null && rangesText.isNotBlank() && job is JobState.Idle,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.CallSplit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Split PDF")
            }
        }
        JobStatus(job)
        for (f in saved) SavedFileCard(f)
    }
}

private enum class CompressPreset(val label: String, val maxDim: Int, val quality: Float, val blurb: String) {
    SMALLER("Smaller file", 1100, 0.45f, "Best for sharing"),
    BALANCED("Balanced", 1600, 0.65f, "Good default"),
    BEST("Best quality", 2200, 0.85f, "Gentle shrink"),
}

@Composable
private fun CompressScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf(CompressPreset.BALANCED) }
    var job by remember { mutableStateOf<JobState>(JobState.Idle) }
    var saved by remember { mutableStateOf<SavedFile?>(null) }
    var statsLine by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked ->
        if (picked != null) {
            persistPermission(context, picked)
            uri = picked
            saved = null
            statsLine = null
            job = JobState.Idle
        }
    }

    val current = uri
    val pages = if (current != null) docPages(current, password) else null

    ToolColumn {
        Text("Downscale and re-encode embedded images. Text stays razor sharp.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (current == null) "Pick a PDF" else "Pick a different PDF")
        }
        if (current != null) {
            val doc = rememberPickedDoc(current)
            PdfRow(name = doc.name, subtitle = doc.sizeText + (pages?.let { " · $it pages" } ?: ""))
            if (pages != null && pages > 0) ThumbStrip(current, pages)
            PasswordField(password) { password = it; saved = null }
            Text("Quality", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in CompressPreset.entries) {
                    FilterChip(
                        selected = preset == p,
                        onClick = { preset = p; saved = null },
                        label = { Text(p.label) },
                    )
                }
            }
            Text(preset.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = {
                    saved = null
                    statsLine = null
                    job = JobState.Working("Compressing images…")
                    scope.launch(Dispatchers.IO) {
                        try {
                            val before = FileUtils.sizeOf(context, current)
                            val base = FileUtils.baseName(doc.name)
                            val outName = "${base}_compressed.pdf"
                            var stats: PdfOps.CompressStats? = null
                            val outUri = FileUtils.saveToDownloads(context, outName) { out ->
                                stats = PdfOps.compress(
                                    context, current, password.ifEmpty { null },
                                    preset.maxDim, preset.quality, out,
                                )
                            }
                            val after = FileUtils.sizeOf(context, outUri)
                            val line = buildString {
                                append("${stats?.imagesRecompressed ?: 0} images recompressed · ")
                                append("${formatBytes(before)} → ${formatBytes(after)}")
                                if (before > 0 && after >= 0) {
                                    val pct = (100 * (before - after) / before).toInt()
                                    if (pct > 0) append(" ($pct% smaller)")
                                }
                            }
                            withContext(Dispatchers.Main) {
                                saved = SavedFile(outName, outUri, formatBytes(after))
                                statsLine = line
                                job = JobState.Idle
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                job = JobState.Failed(e.message ?: "Compress failed.")
                            }
                        }
                    }
                },
                enabled = job is JobState.Idle,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Compress, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Compress PDF")
            }
        }
        JobStatus(job)
        statsLine?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        saved?.let { SavedFileCard(it) }
    }
}

@Composable
private fun WatermarkScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("DRAFT") }
    var opacity by remember { mutableStateOf(0.3f) }
    var job by remember { mutableStateOf<JobState>(JobState.Idle) }
    var saved by remember { mutableStateOf<SavedFile?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked ->
        if (picked != null) {
            persistPermission(context, picked)
            uri = picked
            saved = null
            job = JobState.Idle
        }
    }

    val current = uri
    val pages = if (current != null) docPages(current, password) else null

    ToolColumn {
        Text("Stamp text diagonally across every page.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (current == null) "Pick a PDF" else "Pick a different PDF")
        }
        if (current != null) {
            val doc = rememberPickedDoc(current)
            PdfRow(name = doc.name, subtitle = doc.sizeText + (pages?.let { " · $it pages" } ?: ""))
            if (pages != null && pages > 0) ThumbStrip(current, pages)
            PasswordField(password) { password = it; saved = null }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; saved = null },
                label = { Text("Watermark text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Opacity: ${(opacity * 100).toInt()}%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(
                value = opacity,
                onValueChange = { opacity = it; saved = null },
                valueRange = 0.1f..0.7f,
            )
            Button(
                onClick = {
                    saved = null
                    job = JobState.Working("Applying watermark…")
                    scope.launch(Dispatchers.IO) {
                        try {
                            val base = FileUtils.baseName(doc.name)
                            val outName = "${base}_watermarked.pdf"
                            val outUri = FileUtils.saveToDownloads(context, outName) { out ->
                                PdfOps.watermark(context, current, password.ifEmpty { null }, text, opacity, out)
                            }
                            withContext(Dispatchers.Main) {
                                saved = SavedFile(outName, outUri, formatBytes(FileUtils.sizeOf(context, outUri)))
                                job = JobState.Idle
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                job = JobState.Failed(e.message ?: "Watermark failed.")
                            }
                        }
                    }
                },
                enabled = text.isNotBlank() && job is JobState.Idle,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.BrandingWatermark, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Apply watermark")
            }
        }
        JobStatus(job)
        saved?.let { SavedFileCard(it) }
    }
}

@Composable
private fun ToolColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
        Spacer(Modifier.height(8.dp))
    }
}
