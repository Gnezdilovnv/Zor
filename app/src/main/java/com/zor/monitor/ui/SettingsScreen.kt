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
fun SettingsScreen(
    settings: Map<String, String>,
    directions: List<String>,
    points: List<String>,
    types: List<String>,
    onSave: (Map<String, String>, Map<String, List<String>>) -> Unit,
    onBack: () -> Unit
) {
    var sd by remember { mutableStateOf(settings["direction"] ?: "") }
    var sp by remember { mutableStateOf(settings["point"] ?: "") }
    var systemTheme by remember { mutableStateOf(settings["system_theme"] != "false") }
    var fmt by remember { mutableStateOf(settings["export_format"] ?: "xlsx") }
    var period by remember { mutableStateOf(settings["report_period"] ?: "all") }
    var ef by remember { mutableStateOf(false) }
    var epr by remember { mutableStateOf(false) }
    var ed by remember { mutableStateOf(false) }
    var ep by remember { mutableStateOf(false) }
    var etxt by remember { mutableStateOf(types.joinToString("\n")) }
    var edir by remember { mutableStateOf(directions.joinToString("\n")) }
    var epts by remember { mutableStateOf(points.joinToString("\n")) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Настройки") }, navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Системная тема")
                Spacer(Modifier.weight(1f))
                Switch(checked = systemTheme, onCheckedChange = { systemTheme = it })
            }
            Divider()

            listOf(
                Triple("Формат", { ef }, { fmt }) to listOf("Excel" to "xlsx", "CSV" to "csv"),
                Triple("Период", { epr }, { period }) to listOf("Сегодня" to "today", "Неделя" to "week", "Месяц" to "month", "Всё" to "all")
            ).forEach { (triple, opts) ->
                val (label, expanded, value) = triple
                ExposedDropdownMenuBox(expanded = expanded(), onExpandedChange = { when (label) { "Формат" -> ef = it; "Период" -> epr = it } }) {
                    OutlinedTextField(
                        opts.first { it.second == value() }.first, {}, readOnly = true,
                        label = { Text(label) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded()) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded(), { when (label) { "Формат" -> ef = false; "Период" -> epr = false } }) {
                        opts.forEach { (n, v) ->
                            DropdownMenuItem(text = { Text(n) }, onClick = {
                                when (label) { "Формат" -> fmt = v; "Период" -> period = v }
                                when (label) { "Формат" -> ef = false; "Период" -> epr = false }
                            })
                        }
                    }
                }
            }
            Divider()

            ExposedDropdownMenuBox(expanded = ed, onExpandedChange = { ed = it }) {
                OutlinedTextField(sd, {}, readOnly = true, label = { Text("Направление") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ed) },
                    modifier = Modifier.fillMaxWidth().menuAnchor())
                ExposedDropdownMenu(ed, { ed = false }) { directions.forEach { d -> DropdownMenuItem(text = { Text(d) }, onClick = { sd = d; ed = false }) } }
            }
            ExposedDropdownMenuBox(expanded = ep, onExpandedChange = { ep = it }) {
                OutlinedTextField(sp, {}, readOnly = true, label = { Text("Точка") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ep) },
                    modifier = Modifier.fillMaxWidth().menuAnchor())
                ExposedDropdownMenu(ep, { ep = false }) { points.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { sp = p; ep = false }) } }
            }
            Divider()

            OutlinedTextField(etxt, { etxt = it }, label = { Text("Типы") }, modifier = Modifier.fillMaxWidth().height(150.dp), maxLines = 20)
            OutlinedTextField(edir, { edir = it }, label = { Text("Направления") }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 15)
            OutlinedTextField(epts, { epts = it }, label = { Text("Точки") }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 15)

            Button(onClick = {
                onSave(
                    mapOf("direction" to sd, "point" to sp, "system_theme" to if (systemTheme) "true" else "false", "export_format" to fmt, "report_period" to period),
                    mapOf("types" to etxt.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                          "directions" to edir.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                          "points" to epts.split("\n").map { it.trim() }.filter { it.isNotEmpty() })
                )
            }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
        }
    }
}
