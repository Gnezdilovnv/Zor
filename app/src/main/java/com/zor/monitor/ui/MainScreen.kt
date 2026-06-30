package com.zor.monitor.ui

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(context: Context) {
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var records by remember { mutableStateOf(StorageManager.loadRecords(ctx)) }
    var settings by remember { mutableStateOf(StorageManager.loadSettings(ctx)) }
    var customLists by remember { mutableStateOf(StorageManager.loadCustomLists(ctx)) }
    var type by remember { mutableStateOf("") }; var fv by remember { mutableStateOf("") }; var fc by remember { mutableStateOf("") }
    var suppressed by remember { mutableStateOf(false) }
    val df = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }; val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var cd by remember { mutableStateOf(df.format(Date())) }; var ct by remember { mutableStateOf(tf.format(Date())) }
    var expandedType by remember { mutableStateOf(false) }; var showSettings by remember { mutableStateOf(false) }
    var voiceText by remember { mutableStateOf("") }; var deleteId by remember { mutableStateOf<String?>(null) }

    val directions = customLists["directions"] ?: emptyList(); val points = customLists["points"] ?: emptyList(); val types = customLists["types"] ?: emptyList()
    val isDark = settings["theme"] == "dark"; val exportFormat = settings["export_format"] ?: "xlsx"; val reportPeriod = settings["report_period"] ?: "all"
    val typeColors = mapOf("FPV" to Color(0xFFFF9800), "DJI" to Color(0xFF2196F3), "Яга" to Color(0xFFFF5722), "Крыло" to Color(0xFF4CAF50), "Радар" to Color(0xFF9C27B0), "Радар ударный" to Color(0xFFE91E63), "Перехватчик" to Color(0xFF00BCD4))
    val today = df.format(Date()); val todayRecords = records.filter { it.date == today }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            voiceText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            val words = voiceText.lowercase().split(" ")
            words.forEachIndexed { i, w ->
                when {
                    types.any { it.lowercase() == w } -> type = types.first { it.lowercase() == w }
                    w == "подавлен" || w == "подавлена" -> suppressed = true
                    w == "активен" || w == "активна" -> suppressed = false
                    w.matches(Regex("\\d{3,5}")) && i == words.indexOfFirst { it.matches(Regex("\\d{3,5}")) } -> fv = w
                    w.matches(Regex("\\d{3,5}")) && i != words.indexOfFirst { it.matches(Regex("\\d{3,5}")) } -> fc = w
                }
            }
        }
    }

    fun refresh() { records = StorageManager.loadRecords(ctx) }
    fun shareFile(path: String) {
        try {
            val file = File(path); val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply { type = if (path.endsWith(".csv")) "text/csv" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            ctx.startActivity(Intent.createChooser(intent, "Поделиться отчётом"))
        } catch (e: Exception) { Toast.makeText(ctx, "Ошибка", Toast.LENGTH_SHORT).show() }
    }

    if (deleteId != null) AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Удалить запись?") }, text = { Text("Нельзя отменить") },
        confirmButton = { TextButton(onClick = { deleteId?.let { StorageManager.deleteRecord(ctx, it) }; deleteId = null; refresh() }) { Text("Удалить", color = Color.Red) } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Отмена") } })

    if (showSettings) { SettingsScreen(settings, directions, points, types, onSave = { s, l -> settings = s; customLists = l; StorageManager.saveSettings(ctx, s); StorageManager.saveCustomLists(ctx, l); showSettings = false }, onBack = { showSettings = false }); return }

    Scaffold(topBar = { TopAppBar(title = { Text("Zor") }, actions = { IconButton(onClick = { showSettings = true }) { Text("⚙️", fontSize = 20.sp) } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF37474F) else Color(0xFFE3F2FD))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("📊 Статистика за сегодня", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Всего: ${todayRecords.size}  |  ✅ ${todayRecords.count{it.suppressed=="НЕТ"}}  |  ⚠️ ${todayRecords.count{it.suppressed=="ДА"}}", fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Последние записи", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (records.isEmpty()) Text("Нет записей", style = MaterialTheme.typography.bodySmall)
            else LazyColumn(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                items(records.takeLast(20).reversed()) { rec ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = when { rec.suppressed == "ДА" -> Color(0xFFFFCDD2); rec.exported -> if (isDark) Color(0xFF37474F) else Color(0xFFECEFF1); else -> typeColors[rec.type]?.copy(alpha=0.15f) ?: Color(0xFFFFFFFF) })) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(buildString { append(if(rec.exported) "✅" else "🔄"); append(" ${rec.date} ${rec.time} | ${rec.type} | В:${rec.freqVideo} У:${rec.freqControl} | "); append(if(rec.suppressed=="ДА") "⚠️ Подавлен" else "✅ Активен"); if(rec.voiceText.isNotEmpty()) append(" | 🎤:${rec.voiceText.take(30)}") }, fontSize = 11.sp)
                            }
                            TextButton(onClick = { deleteId = rec.id }) { Text("🗑", fontSize = 16.sp) }
                        }
                    }
                }
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Новая запись", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU"); putExtra(RecognizerIntent.EXTRA_PROMPT, "Тип, частота видео, частота управления, подавлен/активен") }) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF607D8B))) { Text("🎤 Голосовой ввод") }
            if (voiceText.isNotEmpty()) Text("🎤: $voiceText", fontSize = 11.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(expanded = expandedType, onExpandedChange = { expandedType = it }) {
                OutlinedTextField(type, {}, readOnly = true, label = { Text("Тип") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedType) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                ExposedDropdownMenu(expandedType, { expandedType = false }) { types.forEach { t -> DropdownMenuItem(text={Text(t)}, onClick={type=t;expandedType=false}) } }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(fv, { fv = it }, label = { Text("Частота видео (МГц)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(fc, { fc = it }, label = { Text("Частота управления (МГц)") }, modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth()) { OutlinedTextField(cd, { cd = it }, label = { Text("Дата") }, modifier = Modifier.weight(1f)); Spacer(modifier = Modifier.width(8.dp)); OutlinedTextField(ct, { ct = it }, label = { Text("Время") }, modifier = Modifier.weight(1f)) }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Подавлен"); Spacer(modifier = Modifier.weight(1f)); Switch(suppressed, { suppressed = it }) }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Button(onClick = {
                    if(type.isEmpty()||fv.isEmpty()||fc.isEmpty()) { Toast.makeText(ctx,"Заполните поля!",Toast.LENGTH_SHORT).show(); return@Button }
                    if(settings["direction"]==null||settings["point"]==null) { Toast.makeText(ctx,"Выберите направление и точку!",Toast.LENGTH_SHORT).show(); return@Button }
                    StorageManager.addRecord(ctx, Record(date=cd,time=ct,direction=settings["direction"]!!,point=settings["point"]!!,type=type,freqVideo=fv,freqControl=fc,suppressed=if(suppressed)"ДА" else "НЕТ",voiceText=voiceText))
                    refresh(); fv=""; fc=""; suppressed=false; cd=df.format(Date()); ct=tf.format(Date()); voiceText=""
                    Toast.makeText(ctx,"Сохранено!",Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f)) { Text("Сохранить") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { scope.launch { val p = ReportGenerator.generateReport(ctx, exportFormat, reportPeriod); if(p!=null) { Toast.makeText(ctx,"Отчёт: $p",Toast.LENGTH_LONG).show(); refresh(); shareFile(p) } else Toast.makeText(ctx,"Нет новых данных",Toast.LENGTH_SHORT).show() } }, modifier = Modifier.weight(1f)) { Text("Отчёт") }
            }
        }
    }
}
