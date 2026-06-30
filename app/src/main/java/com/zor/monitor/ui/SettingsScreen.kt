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
import java.util.regex.Pattern

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

    // Диалоги
    var showAddDialog by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf("") }
    var newItemText by remember { mutableStateOf("") }
    var deleteItemValue by remember { mutableStateOf("") }
    var deleteExpanded by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }

    // Регулярка: только буквы русского и английского, пробелы, дефис
    val allowedPattern = Pattern.compile("^[а-яА-Яa-zA-Z\\s-]+$")

    // Выпадающие списки для каждого типа значений (стиль как у Формата)
    @Composable
    fun ListWithAddDelete(
        label: String,
        items: List<String>,
        selectedItem: String,
        onItemSelected: (String) -> Unit,
        onAddRequest: () -> Unit,
        onDeleteRequest: () -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = selectedItem,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
                )
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    items.forEach { item ->
                        DropdownMenuItem(text = { Text(item) }, onClick = {
                            onItemSelected(item)
                            expanded = false
                        })
                    }
                }
            }
            IconButton(onClick = onAddRequest) { Text("+", fontSize = 20.sp) }
            IconButton(onClick = onDeleteRequest) { Text("-", fontSize = 20.sp) }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Настройки") }, navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Формат
            ExposedDropdownMenuBox(expanded = ef, onExpandedChange = { ef = it }) {
                OutlinedTextField(
                    value = when(fmt) { "xlsx" -> "Excel"; "csv" -> "CSV"; "json" -> "JSON"; else -> "Excel" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Формат") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ef) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
                )
                ExposedDropdownMenu(ef, { ef = false }) {
                    listOf("Excel" to "xlsx", "CSV" to "csv", "JSON" to "json").forEach { (name, value) ->
                        DropdownMenuItem(text = { Text(name) }, onClick = { fmt = value; ef = false })
                    }
                }
            }
            // Период
            ExposedDropdownMenuBox(expanded = epr, onExpandedChange = { epr = it }) {
                OutlinedTextField(
                    value = when(period) { "today" -> "Сегодня"; "week" -> "Неделя"; "month" -> "Месяц"; else -> "Всё время" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Период") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(epr) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
                )
                ExposedDropdownMenu(epr, { epr = false }) {
                    listOf("Сегодня" to "today", "Неделя" to "week", "Месяц" to "month", "Всё время" to "all").forEach { (name, value) ->
                        DropdownMenuItem(text = { Text(name) }, onClick = { period = value; epr = false })
                    }
                }
            }
            Divider()

            // Направление и Точка (стандартные)
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

            // Список значений – три блока с выпадающими полями
            Text("Списки значений", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            // Типы
            ListWithAddDelete(
                label = "Типы",
                items = currentTypes,
                selectedItem = currentTypes.firstOrNull() ?: "",
                onItemSelected = { /* только просмотр, изменение не требуется */ },
                onAddRequest = { showAddDialog = "types" },
                onDeleteRequest = { showDeleteDialog = "types" }
            )
            // Направления
            ListWithAddDelete(
                label = "Направления",
                items = currentDirections,
                selectedItem = currentDirections.firstOrNull() ?: "",
                onItemSelected = { },
                onAddRequest = { showAddDialog = "directions" },
                onDeleteRequest = { showDeleteDialog = "directions" }
            )
            // Точки
            ListWithAddDelete(
                label = "Точки",
                items = currentPoints,
                selectedItem = currentPoints.firstOrNull() ?: "",
                onItemSelected = { },
                onAddRequest = { showAddDialog = "points" },
                onDeleteRequest = { showDeleteDialog = "points" }
            )

            Divider()
            Button(onClick = {
                onSave(
                    mapOf("direction" to sd, "point" to sp, "export_format" to fmt, "report_period" to period),
                    mapOf("types" to currentTypes, "directions" to currentDirections, "points" to currentPoints)
                )
            }, modifier = Modifier.fillMaxWidth()) {
                Text("СОХРАНИТЬ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Диалог добавления
    if (showAddDialog.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showAddDialog = ""; newItemText = ""; addError = null },
            title = { Text("Добавить значение") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newItemText,
                        onValueChange = { newItemText = it; addError = null },
                        label = { Text("Новое значение") },
                        isError = addError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (addError != null) {
                        Text(addError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val item = newItemText.trim()
                    if (item.isEmpty()) { addError = "Не может быть пустым"; return@TextButton }
                    if (!allowedPattern.matcher(item).matches()) { addError = "Только буквы (рус/англ), пробелы и дефис"; return@TextButton }
                    val currentList = when (showAddDialog) {
                        "types" -> currentTypes; "directions" -> currentDirections; "points" -> currentPoints; else -> emptyList()
                    }
                    if (currentList.any { it.equals(item, ignoreCase = true) }) { addError = "Такое значение уже есть"; return@TextButton }
                    when (showAddDialog) {
                        "types" -> currentTypes = currentTypes + item
                        "directions" -> currentDirections = currentDirections + item
                        "points" -> currentPoints = currentPoints + item
                    }
                    showAddDialog = ""; newItemText = ""; addError = null
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = ""; newItemText = ""; addError = null }) { Text("Отмена") } }
        )
    }

    // Диалог удаления
    if (showDeleteDialog.isNotEmpty()) {
        val list = when (showDeleteDialog) {
            "types" -> currentTypes; "directions" -> currentDirections; "points" -> currentPoints; else -> emptyList()
        }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = ""; deleteItemValue = "" },
            title = { Text("Удалить значение") },
            text = {
                ExposedDropdownMenuBox(expanded = deleteExpanded, onExpandedChange = { deleteExpanded = it }) {
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
                                deleteItemValue = item; deleteExpanded = false
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
