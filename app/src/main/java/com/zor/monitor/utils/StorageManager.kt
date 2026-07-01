package com.zor.monitor.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.zor.monitor.models.Record
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object StorageManager {
    private const val RECORDS_KEY = "records"
    private const val SETTINGS_KEY = "settings"
    private const val CUSTOM_KEY = "custom_lists"
    private val gson = Gson()

    // ---------- Работа с записями ----------
    fun loadRecords(context: Context): List<Record> {
        val json = context.getSharedPreferences("app_data", Context.MODE_PRIVATE)
            .getString(RECORDS_KEY, null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<Record>>() {}.type)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRecords(context: Context, records: List<Record>) {
        try {
            context.getSharedPreferences("app_data", Context.MODE_PRIVATE)
                .edit().putString(RECORDS_KEY, gson.toJson(records)).apply()
            backupToFile(records)
        } catch (_: Exception) {}
    }

    fun addRecord(context: Context, record: Record) {
        val records = loadRecords(context).toMutableList()
        records.add(record)
        saveRecords(context, records)
    }

    fun deleteRecord(context: Context, id: String) {
        val records = loadRecords(context).filter { it.id != id }
        saveRecords(context, records)
    }

    fun getUnexportedRecords(context: Context) = loadRecords(context).filter { !it.exported }

    @Synchronized
    fun markAsExported(context: Context, ids: List<String>) {
        val records = loadRecords(context).map { r ->
            if (r.id in ids) r.copy(exported = true) else r
        }
        saveRecords(context, records)
    }

    private fun backupToFile(records: List<Record>) {
        try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Vzor/Backup")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val backupFile = File(dir, "backup_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())}.json")
            backupFile.writeText(gson.toJson(records))
        } catch (_: Exception) {}
    }

    // ---------- Экспорт через MediaStore ----------
    private fun createMediaStoreUri(context: Context, fileName: String, mimeType: String): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Vzor")
            } else {
                put(MediaStore.MediaColumns.DATA, "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/Vzor/$fileName")
            }
        }
        return resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
    }

    private fun writeToUri(context: Context, uri: Uri, block: (OutputStream) -> Unit): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                block(outputStream)
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun exportCSV(context: Context, records: List<Record>, baseName: String): Uri? {
        val fileName = "$baseName.csv"
        val uri = createMediaStoreUri(context, fileName, "text/csv") ?: return null
        val success = writeToUri(context, uri) { outputStream ->
            outputStream.bufferedWriter().use { writer ->
                writer.write("Дата,Время,Тип,Частота видео,Частота управления,Статус,Точка,Направление\n")
                records.forEach { r ->
                    writer.write("\"${r.date}\",\"${r.time}\",\"${r.type}\",\"${r.freqVideo}\",\"${r.freqControl}\",\"${r.status}\",\"${r.point}\",\"${r.direction}\"\n")
                }
            }
        }
        return if (success) uri else null
    }

    fun exportJSON(context: Context, records: List<Record>, baseName: String): Uri? {
        val fileName = "$baseName.json"
        val uri = createMediaStoreUri(context, fileName, "application/json") ?: return null
        val success = writeToUri(context, uri) { outputStream ->
            outputStream.write(gson.toJson(records).toByteArray())
        }
        return if (success) uri else null
    }

    fun exportXLSX(context: Context, records: List<Record>, baseName: String): Uri? {
        val fileName = "$baseName.xlsx"
        val uri = createMediaStoreUri(context, fileName, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ?: return null
        val success = writeToUri(context, uri) { outputStream ->
            val wb = XSSFWorkbook()
            try {
                val sheet = wb.createSheet("Данные")
                val headers = listOf("Дата","Время","Тип","Частота видео","Частота управления","Статус","Точка","Направление")
                val headerRow = sheet.createRow(0)
                headers.forEachIndexed { i, h -> headerRow.createCell(i).setCellValue(h) }
                records.forEachIndexed { i, r ->
                    val row = sheet.createRow(i + 1)
                    listOf(r.date, r.time, r.type, r.freqVideo, r.freqControl, r.status, r.point, r.direction)
                        .forEachIndexed { j, v -> row.createCell(j).setCellValue(v) }
                }
                wb.write(outputStream)
            } finally {
                wb.close()
            }
        }
        return if (success) uri else null
    }

    // ---------- Настройки ----------
    fun loadSettings(context: Context): Map<String, String> {
        val json = context.getSharedPreferences("app_data", Context.MODE_PRIVATE)
            .getString(SETTINGS_KEY, null) ?: return getDefaultSettings()
        return gson.fromJson(json, object : TypeToken<Map<String, String>>() {}.type)
    }

    fun saveSettings(context: Context, s: Map<String, String>) {
        try {
            context.getSharedPreferences("app_data", Context.MODE_PRIVATE)
                .edit().putString(SETTINGS_KEY, gson.toJson(s)).apply()
        } catch (_: Exception) {}
    }

    fun loadCustomLists(context: Context): Map<String, List<String>> {
        val json = context.getSharedPreferences("app_data", Context.MODE_PRIVATE)
            .getString(CUSTOM_KEY, null) ?: return getDefaults()
        return gson.fromJson(json, object : TypeToken<Map<String, List<String>>>() {}.type)
    }

    fun saveCustomLists(context: Context, l: Map<String, List<String>>) {
        try {
            context.getSharedPreferences("app_data", Context.MODE_PRIVATE)
                .edit().putString(CUSTOM_KEY, gson.toJson(l)).apply()
        } catch (_: Exception) {}
    }

    fun getDefaults() = mapOf(
        "types" to listOf("FPV", "DJI", "Яга", "Крыло", "Радар", "Радар ударный", "Перехватчик"),
        "directions" to listOf("Днепряны", "Тарасовка", "Подокалинозка", "Маячка"),
        "points" to listOf("Нарцисс", "Пион", "Ландыш", "Гладиолус", "Хризантема")
    )

    private fun getDefaultSettings(): Map<String, String> = mapOf(
        "direction" to "", "point" to "",
        "export_format" to "xlsx", "report_period" to "all"
    )
}
