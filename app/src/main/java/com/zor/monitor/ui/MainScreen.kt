package com.zor.monitor.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.zor.monitor.models.Record
import com.zor.monitor.utils.ReportGenerator
import com.zor.monitor.utils.StorageManager
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
    val df = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var cd by remember { mutableStateOf(df.format(Date())) }
    var ct by remember { mutableStateOf(tf.format(Date())) }
    var expandedType by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }

    val directions = customLists["directions"] ?: emptyList()
    val points = customLists["points"] ?: emptyList()
    val types = customLists["types"] ?: emptyList()
    val isDark = settings["theme"] == "dark"
    val exportFormat = settings["export_format"] ?: "xlsx"
    val reportPeriod = settings["report_period"] ?: "all"
    val today = df.format(Date())
    val todayRecords = records.filter { it.date == today }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("ОБНАРУЖЕНИЕ", "ОТЧЕТ")

    fun refresh() { records = StorageManager.loadRecords(ctx) }

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
                title = { Text("Zor") },
                actions = { IconButton(onClick = { showSettings = true }) { Text("⚙️", fontSize = 20.sp) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            when (selectedTab) {
                0 -> DetectionTab(
                    type = type, onTypeChange = { type = it }, types = types,
                    expandedType = expandedType, onExpandedTypeChange = { expandedType = it },
                    fv = fv, onFvChange = { fv = it }, fc = fc, onFcChange = { fc = it },
                    cd = cd, onCdChange = { cd = it }, ct = ct, onCtChange = { ct = it },
                    suppressed = suppressed, onSuppressedChange = { suppressed = it },
                    settings = settings,
                    onSave = {
                        if (type.isEmpty() || fv.isEmpty()) {
                            Toast.makeText(ctx, "Заполните тип и частоту видео!", Toast.LENGTH_SHORT).show()
                            return@DetectionTab
                        }
                        if (settings["direction"] == null || settings["point"] == null) {
                            Toast.makeText(ctx, "Выберите направление и точку в настройках!", Toast.LENGTH_SHORT).show()
                            return@DetectionTab
                        }
                        StorageManager.addRecord(ctx, Record(
                            date = cd, time = ct,
                            direction = settings["direction"]!!, point = settings["point"]!!,
                            type = type, freqVideo = fv, freqControl = fc,
                            suppressed = if (suppressed) "ДА" else "НЕТ",
                            voiceText = ""
                        ))
                        refresh()
                        fv = ""; fc = ""; suppressed = false
                        cd = df.format(Date()); ct = tf.format(Date())
                        Toast.makeText(ctx, "Сохранено!", Toast.LENGTH_SHORT).show()
                    },
                    lastRecord = records.lastOrNull(),
                    onDelete = { deleteId = it },
                    isDark = isDark
                )
                1 -> ReportTab(
                    todayRecords = todayRecords,
                    onSendReport = {
                        scope.launch {
                            val p = ReportGenerator.generateReport(ctx, exportFormat, reportPeriod)
                            if (p != null) {
                                Toast.makeText(ctx, "Отчёт: $p", Toast.LENGTH_LONG).show()
                                refresh()
                                shareFile(p)
                            } else Toast.makeText(ctx, "Нет новых данных", Toast.LENGTH_SHORT).show()
                        }
                    },
                    isDark = isDark
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
    onSave: () -> Unit,
    lastRecord: Record?,
    onDelete: (String) -> Unit,
    isDark: Boolean
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
                label = { Text("Тип") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedType) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expandedType, { onExpandedTypeChange(false) }) {
                types.forEach { t ->
                    DropdownMenuItem(text = { Text(t) }, onClick = {
                        onTypeChange(t)
                        onExpandedTypeChange(false)
                    })
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = fv, onValueChange = onFvChange,
            label = { Text("Частота видео (МГц)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = fc, onValueChange = onFcChange,
            label = { Text("Частота управления (МГц, необязательно)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = cd, onValueChange = onCdChange, label = { Text("Дата") }, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = ct, onValueChange = onCtChange,
                label = { Text("Время") },
                visualTransformation = TimeMask(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Подавлен")
            Spacer(modifier = Modifier.weight(1f))
            Switch(checked = suppressed, onCheckedChange = onSuppressedChange)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }

        if (lastRecord != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Text("Последняя запись", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        lastRecord.suppressed == "ДА" -> Color(0xFFFFCDD2)
                        else -> Color(0xFFECEFF1)
                    }
                )
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = buildString {
                                append(if (lastRecord.exported) "✅" else "ℹ️")
                                append(" ${lastRecord.date} ${lastRecord.time}")
                                append(" | ${lastRecord.type}")
                                append(" | В:${lastRecord.freqVideo}")
                                if (lastRecord.freqControl.isNotEmpty()) append(" У:${lastRecord.freqControl}")
                                append(" | ")
                                append(if (lastRecord.suppressed == "ДА") "🟢 Подавлен" else "🔴 Активен")
                            },
                            fontSize = 14.sp
                        )
                    }
                    TextButton(onClick = { onDelete(lastRecord.id) }) {
                        Text("🗑", fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportTab(
    todayRecords: List<Record>,
    onSendReport: () -> Unit,
    isDark: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Статистика за сегодня", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF37474F) else Color(0xFFE3F2FD))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Всего записей: ${todayRecords.size}", fontSize = 16.sp)
                Text("🟢 Подавлено: ${todayRecords.count { it.suppressed == "ДА" }}", fontSize = 16.sp)
                Text("🔴 Активных: ${todayRecords.count { it.suppressed == "НЕТ" }}", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onSendReport, modifier = Modifier.fillMaxWidth()) { Text("Отправить отчет") }
    }
}
