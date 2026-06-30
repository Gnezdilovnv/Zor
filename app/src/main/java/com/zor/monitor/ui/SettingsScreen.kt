package com.zor.monitor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    var fmt by remember { mutableStateOf(settings["export_format"] ?: "xlsx") }
    var period by remember { mutableStateOf(settings["report_period"] ?: "all") }
    var ef by remember { mutableStateOf(false) }
    var epr by remember { mutableStateOf(false) }
    var ed by remember { mutableStateOf(false) }
    var ep by remember { mutableStateOf(false) }

    // Редактируемые списки
    var currentTypes by remember { mutableStateOf(types) }
    var currentDirections by remember { mutableStateOf(directions) }
    var currentPoints by remember { mutableStateOf(points) }

    // Диалоги добавления/удаления
    var showAddDialog by remember { mutableStateOf("") } // "types", "directions", "points"
    var showDeleteDialog by remember { mutableStateOf("") }
    var newItemText by remember { mutableStateOf("") }
    var deleteItemValue by remember { mutableStateOf("") }
    var deleteExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Настройки") }, navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Формат и Период
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
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
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

            // Направление и Точка (как раньше)
            ExposedDropdownMenuBox(expanded = ed, onExpandedChange = { ed = it }) {
                OutlinedTextField(sd, {}, readOnly = true, label = { Text("Направление") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ed) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center))
                ExposedDropdownMenu(ed, { ed = false }) { directions.forEach { d -> DropdownMenuItem(text = { Text(d) }, onClick = { sd = d; ed = false }) } }
            }
            ExposedDropdownMenuBox(expanded = ep, onExpandedChange = { ep = it }) {
                OutlinedTextField(sp, {}, readOnly = true, label = { Text("Точка") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ep) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center))
                ExposedDropdownMenu(ep, { ep = false }) { points.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { sp = p; ep = false }) } }
            }
            Divider()

            // Редактируемые списки с кнопками +/-
            Text("Списки значений", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            // Типы
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Типы", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { showAddDialog = "types" }) { Text("+", fontSize = 20.sp) }
                IconButton(onClick = { showDeleteDialog = "types" }) { Text("-", fontSize = 20.sp) }
            }
            Text(currentTypes.joinToString(", "), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            // Направления
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Направления", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { showAddDialog = "directions" }) { Text("+", fontSize = 20.sp) }
                IconButton(onClick = { showDeleteDialog = "directions" }) { Text("-", fontSize = 20.sp) }
            }
            Text(currentDirections.joinToString(", "), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            // Точки
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Точки", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { showAddDialog = "points" }) { Text("+", fontSize = 20.sp) }
                IconButton(onClick = { showDeleteDialog = "points" }) { Text("-", fontSize = 20.sp) }
            }
            Text(currentPoints.joinToString(", "), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            Divider()
            Button(onClick = {
                onSave(
                    mapOf("direction" to sd, "point" to sp, "export_format" to fmt, "report_period" to period),
                    mapOf("types" to currentTypes, "directions" to currentDirections, "points" to currentPoints)
                )
            }, modifier = Modifier.fillMaxWidth()) { Text("СОХРАНИТЬ", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }

    // Диалог добавления
    if (showAddDialog.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showAddDialog = ""; newItemText = "" },
            title = { Text("Добавить значение") },
            text = {
                OutlinedTextField(
                    value = newItemText,
                    onValueChange = { newItemText = it },
                    label = { Text("Новое значение") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val item = newItemText.trim()
                    if (item.isNotEmpty()) {
                        when (showAddDialog) {
                            "types" -> currentTypes = currentTypes + item
                            "directions" -> currentDirections = currentDirections + item
                            "points" -> currentPoints = currentPoints + item
                        }
                    }
                    showAddDialog = ""; newItemText = ""
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = ""; newItemText = "" }) { Text("Отмена") } }
        )
    }

    // Диалог удаления
    if (showDeleteDialog.isNotEmpty()) {
        val list = when (showDeleteDialog) {
            "types" -> currentTypes
            "directions" -> currentDirections
            "points" -> currentPoints
            else -> emptyList()
        }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = ""; deleteItemValue = "" },
            title = { Text("Удалить значение") },
            text = {
                ExposedDropdownMenuBox(
                    expanded = deleteExpanded,
                    onExpandedChange = { deleteExpanded = it }
                ) {
                    OutlinedTextField(
                        value = deleteItemValue,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Выберите") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(deleteExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(deleteExpanded, { deleteExpanded = false }) {
                        list.forEach { item ->
                            DropdownMenuItem(text = { Text(item) }, onClick = {
                                deleteItemValue = item
                                deleteExpanded = false
                            })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (deleteItemValue.isNotEmpty()) {
                        when (showDeleteDialog) {
                            "types" -> currentTypes = currentTypes - deleteItemValue
                            "directions" -> currentDirections = currentDirections - deleteItemValue
                            "points" -> currentPoints = currentPoints - deleteItemValue
                        }
                    }
                    showDeleteDialog = ""; deleteItemValue = ""
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = ""; deleteItemValue = "" }) { Text("Отмена") } }
        )
    }
}
