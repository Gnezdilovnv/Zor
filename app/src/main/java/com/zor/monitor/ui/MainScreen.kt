package com.zor.monitor.ui

import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.zor.monitor.R
import com.zor.monitor.models.Record
import com.zor.monitor.ui.theme.LocalIsDarkTheme
import com.zor.monitor.utils.ReportGenerator
import com.zor.monitor.utils.StorageManager
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Маски для даты/времени
class DateMask : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(8)
        val formatted = buildString {
            for (i in digits.indices) {
                append(digits[i])
                if (i == 1 || i == 3) append('.')
            }
        }
        val validated = if (formatted.length >= 2) {
            val day = formatted.substring(0, 2).toIntOrNull()
            if (day != null && day in 1..31) formatted else formatted.substring(0, 1)
        } else formatted
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 1) return offset
                if (offset <= 3) return offset + 1
                if (offset <= 5) return offset + 2
                return minOf(offset + 2, validated.length)
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 2) return offset
                if (offset <= 5) return offset - 1
                return maxOf(offset - 2, 0)
            }
        }
        return TransformedText(AnnotatedString(validated), offsetMapping)
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
        val validated = if (formatted.length >= 2) {
            val hour = formatted.substring(0, 2).toIntOrNull()
            if (hour != null && hour in 0..23) formatted else formatted.substring(0, 1)
        } else formatted
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
        return TransformedText(AnnotatedString(validated), offsetMapping)
    }
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selected: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(2.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .clickable { onOptionSelected(option) }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onThemeChange: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf(StorageManager.loadRecords(ctx)) }
    var settings by remember { mutableStateOf(StorageManager.loadSettings(ctx)) }
    var customLists by remember { mutableStateOf(StorageManager.loadCustomLists(ctx)) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    var type by remember { mutableStateOf("") }
    var fv by remember { mutableStateOf("") }
    var fc by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("АКТИВЕН") }
    val df = remember { SimpleDateFormat("dd.MM.yy", Locale.getDefault()) }
    val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var cd by remember { mutableStateOf(df.format(Date())) }
    var ct by remember { mutableStateOf(tf.format(Date())) }
    var expandedType by remember { mutableStateOf(false) }
    var validationErrors by remember { mutableStateOf(mapOf<String, Boolean>()) }

    val types = customLists["types"] ?: listOf("FPV", "DJI", "Яга", "Крыло", "Радар", "Радар ударный", "Перехватчик")
    val directions = customLists["directions"] ?: listOf("Днепряны", "Тарасовка", "Подокалинозка", "Маячка")
    val points = customLists["points"] ?: listOf("Нарцисс", "Пион", "Ландыш", "Гладиолус", "Хризантема")
    val exportFormat = settings["export_format"] ?: "xlsx"
    val reportPeriod = settings["report_period"] ?: "all"
    val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val todayRecords = records.filter { it.isoDate == todayIso }
    var lastReportTime by remember { mutableStateOf(settings["last_report_time"] ?: "") }
    var isGeneratingReport by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    val isDark = LocalIsDarkTheme.current

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
            val mime = when {
                path.endsWith(".csv") -> "text/csv"
                path.endsWith(".json") -> "application/json"
                else -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, "Поделиться отчётом"))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
        else {
            val num = fv.toIntOrNull()
            if (num == null || num !in 100..12000) errors["fv_range"] = true
        }
        if (fc.isNotEmpty()) {
            val num = fc.toIntOrNull()
            if (num == null || num !in 100..5000) errors["fc_range"] = true
        }
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
            confirmButton = {
                TextButton(onClick = {
                    deleteId?.let { StorageManager.deleteRecord(ctx, it) }
                    deleteId = null
                    refresh()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Отмена") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_logo), "Logo", Modifier.size(32.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("VZOR", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 24.sp, letterSpacing = (-0.5).sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton({ showSettings = true }) { Icon(Icons.Filled.Settings, "Настройки", tint = MaterialTheme.colorScheme.onSurface) }
                    IconButton({ onThemeChange(!isDark) }) {
                        Icon(if (isDark) Icons.Filled.DarkMode else Icons.Filled.LightMode, "Тема")
                    }
                }
            )
        }
    ) { padding ->
        if (showSettings) {
            SettingsScreen(
                settings, directions, points, types,
                onSave = { s, l ->
                    settings = s; customLists = l
                    StorageManager.saveSettings(ctx, s)
                    StorageManager.saveCustomLists(ctx, l)
                    showSettings = false
                },
                onBack = { showSettings = false }
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    indicator = {},
                    divider = {},
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    listOf("ОБНАРУЖЕНИЕ", "ОТЧЕТ").forEachIndexed { index, title ->
                        val selected = selectedTab == index
                        Tab(
                            selected = selected,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                when (selectedTab) {
                    0 -> DetectionContent(
                        type, onTypeChange = { type = it; validationErrors = validationErrors - "type" },
                        types, expandedType, onExpandedTypeChange = { expandedType = it },
                        fv, fc,
                        onFvChange = { raw ->
                            val (digits, err) = cleanFreqInput(raw, 100, 12000)
                            fv = digits
                            validationErrors = if (err != null) validationErrors + ("fv_range" to true) else validationErrors - "fv_range"
                        },
                        onFcChange = { raw ->
                            val (digits, err) = cleanFreqInput(raw, 100, 5000)
                            fc = digits
                            validationErrors = if (err != null) validationErrors + ("fc_range" to true) else validationErrors - "fc_range"
                        },
                        cd, onCdChange = { cd = it },
                        ct, onCtChange = { ct = it },
                        onSetNow = { cd = df.format(Date()); ct = tf.format(Date()) },
                        selectedStatus, onStatusChange = { selectedStatus = it },
                        validationErrors,
                        onSave = {
                            if (validate()) {
                                val isoDate = try {
                                    val parsed = df.parse(cd)
                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(parsed!!)
                                } catch (_: Exception) { "" }
                                StorageManager.addRecord(ctx, Record(
                                    date = cd, time = ct,
                                    direction = settings["direction"] ?: "",
                                    point = settings["point"] ?: "",
                                    type = type,
                                    freqVideo = fv,
                                    freqControl = fc,
                                    status = selectedStatus,
                                    voiceText = "",
                                    isoDate = isoDate
                                ))
                                refresh()
                                fv = ""; fc = ""; selectedStatus = "АКТИВЕН"
                                cd = df.format(Date()); ct = tf.format(Date())
                                validationErrors = emptyMap()
                                Toast.makeText(ctx, "Сохранено!", Toast.LENGTH_SHORT).show()
                                playSound(R.raw.save_sound)
                            }
                        },
                        records.lastOrNull(),
                        onDelete = { deleteId = it }
                    )
                    1 -> ReportContent(
                        todayRecords,
                        lastReportTime,
                        isGeneratingReport,
                        onSendReport = {
                            scope.launch {
                                isGeneratingReport = true
                                val path = ReportGenerator.generateReport(ctx, exportFormat, reportPeriod)
                                isGeneratingReport = false
                                if (path != null) {
                                    Toast.makeText(ctx, "Отчёт создан", Toast.LENGTH_LONG).show()
                                    val now = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                                    val updatedSettings = settings.toMutableMap()
                                    updatedSettings["last_report_time"] = now
                                    settings = updatedSettings
                                    StorageManager.saveSettings(ctx, updatedSettings)
                                    lastReportTime = now
                                    refresh()
                                    shareFile(path)
                                    playSound(R.raw.report_sound)
                                } else {
                                    Toast.makeText(ctx, "Нет новых данных", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDeleteRecord = { id ->
                            StorageManager.deleteRecord(ctx, id)
                            refresh()
                            Toast.makeText(ctx, "Запись удалена", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionContent(
    type: String,
    onTypeChange: (String) -> Unit,
    types: List<String>,
    expandedType: Boolean,
    onExpandedTypeChange: (Boolean) -> Unit,
    fv: String,
    fc: String,
    onFvChange: (String) -> Unit,
    onFcChange: (String) -> Unit,
    cd: String,
    onCdChange: (String) -> Unit,
    ct: String,
    onCtChange: (String) -> Unit,
    onSetNow: () -> Unit,
    selectedStatus: String,
    onStatusChange: (String) -> Unit,
    validationErrors: Map<String, Boolean>,
    onSave: () -> Unit,
    lastRecord: Record?,
    onDelete: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Новое обнаружение", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    )
                )
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(0.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Тип
                ExposedDropdownMenuBox(expanded = expandedType, onExpandedChange = onExpandedTypeChange) {
                    OutlinedTextField(
                        value = type, onValueChange = {}, readOnly = true,
                        label = { Text("Тип", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        isError = validationErrors["type"] == true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedType) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expandedType, { onExpandedTypeChange(false) }) {
                        types.forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = {
                                onTypeChange(t); onExpandedTypeChange(false)
                            })
                        }
                    }
                }

                // Частота видео
                OutlinedTextField(
                    fv, onFvChange,
                    label = { Text("Частота видео (МГц)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    isError = validationErrors["fv"] == true || validationErrors["fv_range"] == true,
                    supportingText = {
                        if (validationErrors["fv_range"] == true) Text("Диапазон 100–12000", color = MaterialTheme.colorScheme.error)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                // Частота управления
                OutlinedTextField(
                    fc, onFcChange,
                    label = { Text("Частота управления (МГц)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    isError = validationErrors["fc_range"] == true,
                    supportingText = {
                        if (validationErrors["fc_range"] == true) Text("Диапазон 100–5000", color = MaterialTheme.colorScheme.error)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                // Дата и время
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        cd, onCdChange,
                        label = { Text("Дата", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        visualTransformation = DateMask(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    IconButton(onClick = onSetNow, modifier = Modifier.size(48.dp)) {
                        Text("🕒", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedTextField(
                        ct, onCtChange,
                        label = { Text("Время", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        visualTransformation = TimeMask(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                // Статус
                Text("Статус", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                SegmentedControl(
                    options = listOf("ПОДАВЛЕН", "АКТИВЕН", "ДЕТОНАЦИЯ"),
                    selected = selectedStatus,
                    onOptionSelected = onStatusChange,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("СОХРАНИТЬ", fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
            }
        }

        if (lastRecord != null) {
            Spacer(Modifier.height(24.dp))
            Text("Последняя запись", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            RecordCard(lastRecord, onDelete = { onDelete(lastRecord.id) }, showDelete = !lastRecord.exported)
        }
    }
}

@Composable
fun RecordCard(record: Record, onDelete: () -> Unit, showDelete: Boolean) {
    val status = record.status.uppercase()
    val (borderColor, bgColor, statusLabel) = when (status) {
        "ПОДАВЛЕН" -> Triple(
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f),
            "Подавлен"
        )
        "ДЕТОНАЦИЯ" -> Triple(
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.1f),
            "Детонация"
        )
        else -> Triple(
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
            "Активен"
        )
    }
    val isActive = status == "АКТИВЕН"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .animateContentSize()
            .alpha(if (isActive) pulseAnimation() else 1f)
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        bgColor,
                        bgColor.copy(alpha = 0.5f)
                    )
                )
            ),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        record.type,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = borderColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (record.freqVideo.isNotEmpty() || record.freqControl.isNotEmpty()) {
                        Text(
                            buildString {
                                append("B:${record.freqVideo}")
                                if (record.freqControl.isNotEmpty()) append(" Y:${record.freqControl}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DateRange, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(record.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(record.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(statusLabel, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = borderColor), modifier = Modifier.padding(end = 8.dp))
                if (showDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun pulseAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    return alpha
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportContent(
    todayRecords: List<Record>,
    lastReportTime: String,
    isGenerating: Boolean,
    onSendReport: () -> Unit,
    onDeleteRecord: (String) -> Unit
) {
    Column {
        Text("Статистика за сегодня", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    )
                )
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(0.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Всего записей:", style = MaterialTheme.typography.bodyLarge)
                    Text("${todayRecords.size}", style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondary))
                    Spacer(Modifier.width(8.dp))
                    Text("Подавлено: ${todayRecords.count { it.status.uppercase() == "ПОДАВЛЕН" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.error))
                    Spacer(Modifier.width(8.dp))
                    Text("Активных: ${todayRecords.count { it.status.uppercase() == "АКТИВЕН" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.tertiary))
                    Spacer(Modifier.width(8.dp))
                    Text("Детонаций: ${todayRecords.count { it.status.uppercase() == "ДЕТОНАЦИЯ" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
                if (lastReportTime.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Последний отчёт отправлен: $lastReportTime", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onSendReport,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = RoundedCornerShape(16.dp),
            enabled = !isGenerating
        ) {
            if (isGenerating) {
                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Send, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ОТПРАВИТЬ ОТЧЕТ", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), letterSpacing = 0.5.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("Обнаружения за сегодня", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(8.dp))

        if (todayRecords.isEmpty()) {
            Text("Нет записей за сегодня", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(todayRecords) { record ->
                    RecordCard(record, onDelete = { onDeleteRecord(record.id) }, showDelete = !record.exported)
                }
            }
        }
    }
}
