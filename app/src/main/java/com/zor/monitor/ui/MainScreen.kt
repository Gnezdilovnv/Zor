package com.zor.monitor.ui

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

// Маски даты и времени
class DateMask : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val raw = text.text.filter { it.isDigit() }.take(8)
        val formatted = buildString {
            for (i in raw.indices) {
                append(raw[i])
                if (i == 1 || i == 3) append('.')
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 1) return offset
                if (offset <= 3) return offset + 1
                if (offset <= 5) return offset + 2
                return 8
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 2) return offset
                if (offset <= 5) return offset - 1
                return offset - 2
            }
        }
        return TransformedText(androidx.compose.ui.text.AnnotatedString(formatted), offsetMapping)
    }
}

class TimeMask : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val raw = text.text.filter { it.isDigit() }.take(4)
        val formatted = when {
            raw.length >= 3 -> "${raw.substring(0, 2)}:${raw.substring(2)}"
            raw.length == 2 -> "${raw}:"
            else -> raw
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 1) return offset
                if (offset == 2) return 3
                return offset + 1
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 2) return offset
                return offset - 1
            }
        }
        return TransformedText(androidx.compose.ui.text.AnnotatedString(formatted), offsetMapping)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(context: Context) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
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
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val todayRecords = records.filter { it.date == today }

    var selectedTab by remember { mutableIntStateOf(0) }

    fun refresh() { records = StorageManager.loadRecords(ctx) }

    fun playSound(resId: Int) {
        try {
            val mp = MediaPlayer.create(ctx, resId)
            mp?.start()
            mp?.setOnCompletionListener { it.release() }
        } catch (_: Exception) {}
    }

    fun shareFile(path: String) {
        try {
            val file = File(path)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val mimeType = if (path.endsWith(".csv")) "text/csv" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, "Поделиться отчётом"))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun validate(): Boolean {
        val errors = mutableMapOf<String, Boolean>()
        if (type.isEmpty()) errors["type"] = true
        if (fv.isEmpty()) errors["fv"] = true
        validationErrors = errors
        return errors.isEmpty()
    }

    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Удалить запись?") },
            text = { Text("Нельзя отменить") },
            confirmButton = {
                TextButton(onClick = {
                    deleteId?.let { StorageManager.deleteRecord(ctx, it) }
                    deleteId = null
                    refresh()
                }) { Text("Удалить", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Отмена") } }
        )
    }

    if (showSettings) {
        SettingsScreen(
            settings = settings,
            directions = directions,
            points = points,
            types = types,
            onSave = { s, l ->
                settings = s; customLists = l
                StorageManager.saveSettings(ctx, s)
                StorageManager.saveCustomLists(ctx, l)
                showSettings = false
            },
            onBack = { showSettings = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VZOR", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Text("☰", fontSize = 22.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                indicator = {},
                divider = {},
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                listOf("ОБНАРУЖЕНИЕ", "ОТЧЕТ").forEachIndexed { index, title ->
                    val selected = selectedTab == index
                    Tab(
                        selected = selected,
                        onClick = { selectedTab = index },
                        modifier = if (selected) {
                            Modifier
                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        }
                    ) {
                        Text(
                            text = title,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
            when (selectedTab) {
                0 -> DetectionTab(
                    type = type, onTypeChange = { type = it; validationErrors = validationErrors - "type" },
                    types = types,
                    expandedType = expandedType, onExpandedTypeChange = { expandedType = it },
                    fv = fv, onFvChange = { fv = it; validationErrors = validationErrors - "fv" },
                    fc = fc, onFcChange = { fc = it },
                    cd = cd, onCdChange = { cd = it },
                    ct = ct, onCtChange = { ct = it },
                    suppressed = suppressed, onSuppressedChange = { suppressed = it },
                    settings = settings,
                    validationErrors = validationErrors,
                    onSave = {
                        if (validate()) {
                            if (settings["direction"] == null || settings["point"] == null) {
                                Toast.makeText(ctx, "Выберите направление и точку в настройках!", Toast.LENGTH_SHORT).show()
                                return@DetectionTab
                            }
                            StorageManager.addRecord(ctx, Record(
                                date = today, time = ct,
                                direction = settings["direction"]!!, point = settings["point"]!!,
                                type = type, freqVideo = fv, freqControl = fc,
                                suppressed = if (suppressed) "ДА" else "НЕТ",
                                voiceText = ""
                            ))
                            refresh()
                            fv = ""; fc = ""; suppressed = false
                            cd = df.format(Date()); ct = tf.format(Date())
                            validationErrors = emptyMap()
                            Toast.makeText(ctx, "Сохранено!", Toast.LENGTH_SHORT).show()
                            playSound(R.raw.save_sound)
                        }
                    },
                    onSetNow = {
                        cd = df.format(Date())
                        ct = tf.format(Date())
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionTab(
    type: String, onTypeChange: (String) -> Unit, types: List<String>,
    expandedType: Boolean, onExpandedTypeChange: (Boolean) -> Unit,
    fv: String, onFvChange: (String) -> Unit,
    fc: String, onFcChange: (String) -> Unit,
    cd: String, onCdChange: (String) -> Unit,
    ct: String, onCtChange: (String) -> Unit,
    suppressed: Boolean, onSuppressedChange: (Boolean) -> Unit,
    settings: Map<String, String>,
    validationErrors: Map<String, Boolean>,
    onSave: () -> Unit,
    onSetNow: () -> Unit,
    lastRecord: Record?,
    onDelete: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Новое обнаружение", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        ExposedDropdownMenuBox(expanded = expandedType, onExpandedChange = onExpandedTypeChange) {
            OutlinedTextField(
                value = type, onValueChange = {}, readOnly = true,
                label = { Text("Тип") }, isError = validationErrors["type"] == true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedType) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (validationErrors["type"] == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = if (validationErrors["type"] == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expandedType, { onExpandedTypeChange(false) }) {
                types.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { onTypeChange(t); onExpandedTypeChange(false) }) }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(fv, onFvChange, label = { Text("Частота видео (МГц)") }, isError = validationErrors["fv"] == true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(fc, onFcChange, label = { Text("Частота управления (МГц)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(cd, onCdChange, label = { Text("Дата") }, visualTransformation = DateMask(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onSetNow) { Text("🕒") }
            OutlinedTextField(ct, onCtChange, label = { Text("Время") }, visualTransformation = TimeMask(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Подавлен")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (suppressed) "ДА" else "НЕТ",
                fontWeight = FontWeight.Bold,
                color = if (suppressed) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
            Spacer(modifier = Modifier.weight(1f))
            Switch(checked = suppressed, onCheckedChange = onSuppressedChange)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("СОХРАНИТЬ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (lastRecord != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Text("Последняя запись", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            RecordCard(
                record = lastRecord,
                onDelete = { onDelete(lastRecord.id) },
                showDelete = !lastRecord.exported
            )
        }
    }
}

@Composable
fun RecordCard(record: Record, onDelete: () -> Unit, showDelete: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = when { record.suppressed == "ДА" -> Color(0xFFFFCDD2) else -> Color(0xFFECEFF1) })
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        append(record.type)
                        append(" | В:${record.freqVideo}")
                        if (record.freqControl.isNotEmpty()) append(" У:${record.freqControl}")
                        append(" | ${record.date} | ${record.time}")
                        append(" | ")
                        append(if (record.suppressed == "ДА") "🟢 Подавлен" else "🔴 Активен")
                        if (record.exported) append(" ✅")
                    },
                    fontSize = 14.sp
                )
            }
            if (showDelete) {
                TextButton(onClick = onDelete) { Text("🗑", fontSize = 20.sp) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportTab(
    todayRecords: List<Record>,
    lastReportTime: String,
    isGenerating: Boolean,
    onSendReport: () -> Unit,
    onDeleteRecord: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Статистика за сегодня", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Всего записей: ${todayRecords.size}", fontSize = 16.sp)
                    Text("🟢 Подавлено: ${todayRecords.count { it.suppressed == "ДА" }}", fontSize = 16.sp)
                    Text("🔴 Активных: ${todayRecords.count { it.suppressed == "НЕТ" }}", fontSize = 16.sp)
                }
            }
            if (lastReportTime.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Последний отчёт отправлен: $lastReportTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (isGenerating) {
                CircularProgressIndicator()
            } else {
                Button(onClick = onSendReport, modifier = Modifier.fillMaxWidth()) {
                    Text("ОТПРАВИТЬ ОТЧЕТ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (todayRecords.isNotEmpty()) {
            Text(
                "Обнаружения за сегодня",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            ) {
                items(todayRecords) { record ->
                    RecordCard(
                        record = record,
                        onDelete = { onDeleteRecord(record.id) },
                        showDelete = !record.exported
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
