package com.zor.monitor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: Map<String, String>, directions: List<String>, points: List<String>, types: List<String>, onSave: (Map<String, String>, Map<String, List<String>>) -> Unit, onBack: () -> Unit) {
    var sd by remember { mutableStateOf(settings["direction"] ?: "") }; var sp by remember { mutableStateOf(settings["point"] ?: "") }
    var theme by remember { mutableStateOf(settings["theme"] ?: "light") }; var fmt by remember { mutableStateOf(settings["export_format"] ?: "xlsx") }
    var period by remember { mutableStateOf(settings["report_period"] ?: "all") }
    var ed by remember { mutableStateOf(false) }; var ep by remember { mutableStateOf(false) }
    var et by remember { mutableStateOf(false) }; var ef by remember { mutableStateOf(false) }; var eper by remember { mutableStateOf(false) }
    var editTypes by remember { mutableStateOf(types.joinToString("\n")) }
    var editDirections by remember { mutableStateOf(directions.joinToString("\n")) }
    var editPoints by remember { mutableStateOf(points.joinToString("\n")) }

    Scaffold(topBar = { TopAppBar(title = { Text("Настройки") }, navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            listOf(
                Triple("Тема", et, theme) to listOf("Светлая" to "light", "Тёмная" to "dark"),
                Triple("Формат", ef, fmt) to listOf("Excel (XLSX)" to "xlsx", "CSV" to "csv"),
                Triple("Период", eper, period) to listOf("За сегодня" to "today", "За неделю" to "week", "За месяц" to "month", "Всё время" to "all")
            ).forEach { (triple, opts) ->
                val (label, expanded, value) = triple
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { when(label) { "Тема" -> et=it; "Формат" -> ef=it; "Период" -> eper=it } }) {
                    OutlinedTextField(opts.first { it.second == value }.first, {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded, { when(label) { "Тема" -> et=false; "Формат" -> ef=false; "Период" -> eper=false } }) { opts.forEach { (n, v) -> DropdownMenuItem(text={Text(n)}, onClick={ when(label) { "Тема" -> theme=v; "Формат" -> fmt=v; "Период" -> period=v }; when(label) { "Тема" -> et=false; "Формат" -> ef=false; "Период" -> eper=false } }) } }
                }
            }
            Divider()
            ExposedDropdownMenuBox(expanded = ed, onExpandedChange = { ed = it }) {
                OutlinedTextField(sd, {}, readOnly = true, label = { Text("Направление") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ed) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                ExposedDropdownMenu(ed, { ed = false }) { directions.forEach { d -> DropdownMenuItem(text={Text(d)}, onClick={sd=d;ed=false}) } }
            }
            ExposedDropdownMenuBox(expanded = ep, onExpandedChange = { ep = it }) {
                OutlinedTextField(sp, {}, readOnly = true, label = { Text("Точка") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ep) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                ExposedDropdownMenu(ep, { ep = false }) { points.forEach { p -> DropdownMenuItem(text={Text(p)}, onClick={sp=p;ep=false}) } }
            }
            Divider()
            Text("Типы", style = MaterialTheme.typography.titleSmall); OutlinedTextField(editTypes, { editTypes = it }, modifier = Modifier.fillMaxWidth().height(150.dp), maxLines = 20)
            Text("Направления", style = MaterialTheme.typography.titleSmall); OutlinedTextField(editDirections, { editDirections = it }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 15)
            Text("Точки", style = MaterialTheme.typography.titleSmall); OutlinedTextField(editPoints, { editPoints = it }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 15)
            Button(onClick = {
                onSave(mapOf("direction" to sd, "point" to sp, "theme" to theme, "export_format" to fmt, "report_period" to period),
                    mapOf("types" to editTypes.split("\n").map{it.trim()}.filter{it.isNotEmpty()}, "directions" to editDirections.split("\n").map{it.trim()}.filter{it.isNotEmpty()}, "points" to editPoints.split("\n").map{it.trim()}.filter{it.isNotEmpty()}))
            }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
        }
    }
}
