package com.zor.monitor.ui

import android.app.Application
import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.zor.monitor.R
import com.zor.monitor.models.Record
import com.zor.monitor.utils.ReportGenerator
import com.zor.monitor.utils.StorageManager
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DateMask : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(8)
        val formatted = buildString {
            for (i in digits.indices) {
                append(digits[i])
                if (i == 1 || i == 3) append('.')
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 1) return offset
                if (offset <= 3) return offset + 1
                if (offset <= 5) return offset + 2
                return minOf(offset + 2, formatted.length)
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 2) return offset
                if (offset <= 5) return offset - 1
                return maxOf(offset - 2, 0)
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

class TimeMask : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(4)
        val formatted = when {
            digits.length >= 3 -> "${digits.substring(0, 2)}:${digits.substring(2)}"
            digits.length == 2 -> "${digits}:"
            else -> digits
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 1) return offset
                if (offset == 2) return 3
                if (offset >= 3) return offset + 1
                return offset
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 2) return offset
                return offset - 1
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf(StorageManager.loadRecords(ctx)) }
    var settings by remember { mutableStateOf(StorageManager.loadSettings(ctx)) }
    var customLists by remember { mutableStateOf(StorageManager.loadCustomLists(ctx)) }
    var type by remember { mutableStateOf("") }
    var fv by remember { mutableStateOf("") }
    var fc by remember { mutableStateOf("") }
    var suppressed by remember { mutableStateOf(false) }
    val df = remember { SimpleDateFormat("dd.MM.yy", Locale.getDefault()) }
    val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var cd by remember { mutableStateOf(df.format(Date())) }
    var ct by remember { mutableStateOf(tf.format(Date())) }
    var expandedType by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var lastReportTime by remember { mutableStateOf(settings["last_report_time"] ?: "") }
    var isGeneratingReport by remember { mutableStateOf(false) }
    var validationErrors by remember { mutableStateOf(mapOf<String, Boolean>()) }

    val directions = customLists["directions"] ?: emptyList()
    val points = customLists["points"] ?: emptyList()
    val types = customLists["types"] ?: emptyList()
    val exportFormat = settings["export_format"] ?: "xlsx"
    val reportPeriod = settings["report_period"] ?: "all"
    val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val todayRecords = records.filter { it.isoDate == todayIso }

    var selectedTab by remember { mutableIntStateOf(0) }

    fun refresh() { records = StorageManager.loadRecords(ctx) }

    fun playSound(resId: Int) {
        try { val mp = MediaPlayer.create(ctx, resId); mp?.start(); mp?.setOnCompletionListener { it.release() } } catch (_: Exception) {}
    }

    fun shareFile(path: String) {
        try {
            val file = File(path)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val mime = when { path.endsWith(".csv") -> "text/csv"; path.endsWith(".json") -> "application/json"; else -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" }
            val intent = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uri); setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            ctx.startActivity(Intent.createChooser(intent, "Поделиться отчётом"))
        } catch (e: Exception) { Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    fun cleanFreqInput(raw: String, min: Int, max: Int): Pair<String, String?> {
        val digits = raw.filter { it.isDigit() }
        val num = digits.toIntOrNull()
        val error = if (digits.isNotEmpty() && (num == null || num < min || num > max)) "Диапазон $min–$max" else null
        return digits to error
    }

    fun validate(): Boolean {
        val errors = mutableMapOf<String, Boolean>()
        if (type.isEmpty()) errors["type"] = true
        if (fv.isEmpty()) errors["fv"] = true
        else { val num = fv.toIntOrNull(); if (num == null || num !in 100..12000) errors["fv_range"] = true }
        if (fc.isNotEmpty()) { val num = fc.toIntOrNull(); if (num == null || num !in 100..5000) errors["fc_range"] = true }
        if (settings["direction"].isNullOrBlank()) errors["direction"] = true
        if (settings["point"].isNullOrBlank()) errors["point"] = true
        validationErrors = errors
        return errors.isEmpty()
    }

    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Удалить запись?") },
            text = { Text("Нельзя отменить") },
            confirmButton = { TextButton(onClick = { deleteId?.let { StorageManager.deleteRecord(ctx, it) }; deleteId = null; refresh() }) { Text("Удалить", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Отмена") } }
        )
    }

    if (showSettings) {
        SettingsScreen(
            settings = settings, directions = directions, points = points, types = types,
            onSave = { s, l -> settings = s; customLists = l; StorageManager.saveSettings(ctx, s); StorageManager.saveCustomLists(ctx, l); showSettings = false },
            onBack = { showSettings = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painter = painterResource(id = R.drawable.ic_logo), contentDescription = "Logo", modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("VZOR", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
                actions = { IconButton(onClick = { showSettings = true }) { Text("☰", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab, indicator = {}, divider = {}, containerColor = MaterialTheme.colorScheme.surface) {
                listOf("ОБНАРУЖЕНИЕ", "ОТЧЕТ").forEachIndexed { index, title ->
                    val selected = selectedTab == index
                    Tab(
                        selected = selected,
                        onClick = { selectedTab = index },
                        modifier = if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        else Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    ) {
                        Text(text = title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
            }
            when (selectedTab) {
                0 -> DetectionTab(
                    type = type, onTypeChange = { type = it; validationErrors = validationErrors - "type" },
                    types = types,
                    expandedType = expandedType, onExpandedTypeChange = { expandedType = it },
                    fv = fv, fc = fc,
                    onFvChange = { raw ->
                        val (digits, err) = cleanFreqInput(raw, 100, 12000)
                        fv = digits
                        if (err != null) validationErrors = validationErrors + ("fv_range" to true)
                        else validationErrors = validationErrors - "fv_range"
                    },
                    onFcChange = { raw ->
                        val (digits, err) = cleanFreqInput(raw, 100, 5000)
                        fc = digits
                        if (err != null) validationErrors = validationErrors + ("fc_range" to true)
                        else validationErrors = validationErrors - "fc_range"
                    },
                    cd = cd, onCdChange = { cd = it },
                    ct = ct, onCtChange = { ct = it },
                    onSetNow = {
                        cd = df.format(Date())
                        ct = tf.format(Date())
                    },
                    suppressed = suppressed, onSuppressedChange = { suppressed = it },
                    settings = settings,
                    validationErrors = validationErrors,
                    onSave = {
                        if (validate()) {
                            val isoDate = try {
                                val parsed = df.parse(cd)
                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(parsed!!)
                            } catch (_: Exception) { "" }
                            StorageManager.addRecord(ctx, Record(
                                date = cd, time = ct,
                                direction = settings["direction"]!!, point = settings["point"]!!,
                                type = type, freqVideo = fv, freqControl = fc,
                                suppressed = if (suppressed) "ДА" else "НЕТ",
                                voiceText = "",
                                isoDate = isoDate
                            ))
                            refresh()
                            fv = ""; fc = ""; suppressed = false
                            cd = df.format(Date()); ct = tf.format(Date())
                            validationErrors = emptyMap()
                            Toast.makeText(ctx, "Сохранено!", Toast.LENGTH_SHORT).show()
                            playSound(R.raw.save_sound)
                        }
                    },
                    lastRecord = records.lastOrNull(),
                    onDelete = { deleteId = it }
                )
                1 -> ReportTab(
                    todayRecords = todayRecords,
                    lastReportTime = lastReportTime,
                    isGenerating = isGeneratingReport,
                    onSendReport = {
                        scope.launch {
                            isGeneratingReport = true
                            val p = ReportGenerator.generateReport(ctx, exportFormat, reportPeriod)
                            isGeneratingReport = false
                            if (p != null) {
                                Toast.makeText(ctx, "Отчёт: $p", Toast.LENGTH_LONG).show()
                                val now = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                                val updatedSettings = settings.toMutableMap()
                                updatedSettings["last_report_time"] = now
                                settings = updatedSettings
                                StorageManager.saveSettings(ctx, updatedSettings)
                                lastReportTime = now
                                refresh()
                                shareFile(p)
                                playSound(R.raw.report_sound)
                            } else Toast.makeText(ctx, "Нет новых данных", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDeleteRecord = { deleteId = it }
                )
            }
        }
    }
}

// ... (DetectionTab, RecordCard, ReportTab без изменений из предыдущей полной версии)
