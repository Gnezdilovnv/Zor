package com.zor.monitor.utils

import android.content.Context
import android.os.Environment
import com.zor.monitor.models.Record
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object StorageManager {
    private const val RECORDS_KEY = "records"
    private const val SETTINGS_KEY = "settings"
    private const val CUSTOM_KEY = "custom_lists"
    private val gson = Gson()

    private fun getVzorDir() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Vzor")
    private fun getBackupDir() = File(getVzorDir(), "Backup")

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
            val dir = getBackupDir()
            if (!dir.exists()) dir.mkdirs()
            File(dir, "backup.json").writeText(gson.toJson(records))
        } catch (_: Exception) {}
    }

    fun exportCSV(context: Context, records: List<Record>, baseName: String): File? = try {
        val dir = getVzorDir()
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$baseName.csv")
        file.bufferedWriter().use { w ->
            w.write("Дата,Время,Тип,Частота видео,Частота управления,Статус,Точка,Направление\n")
            records.forEach { r ->
                w.write("\"${r.date}\",\"${r.time}\",\"${r.type}\",\"${r.freqVideo}\",\"${r.freqControl}\",\"${r.status}\",\"${r.point}\",\"${r.direction}\"\n")
            }
        }
        file
    } catch (_: Exception) { null }

    fun exportJSON(context: Context, records: List<Record>, baseName: String): File? = try {
        val dir = getVzorDir()
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$baseName.json")
        file.writeText(gson.toJson(records))
        file
    } catch (_: Exception) { null }

    fun exportXLSX(context: Context, records: List<Record>, baseName: String): File? {
        try {
            val dir = getVzorDir()
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "$baseName.xlsx")
            file.outputStream().use { fos ->
                val wb = XSSFWorkbook()
                val sheet = wb.createSheet("Данные")
                val headers = listOf("Дата","Время","Тип","Частота видео","Частота управления","Статус","Точка","Направление")
                val headerRow = sheet.createRow(0)
                headers.forEachIndexed { i, h -> headerRow.createCell(i).setCellValue(h) }
                records.forEachIndexed { i, r ->
                    val row = sheet.createRow(i + 1)
                    listOf(r.date, r.time, r.type, r.freqVideo, r.freqControl, r.status, r.point, r.direction)
                        .forEachIndexed { j, v -> row.createCell(j).setCellValue(v) }
                }
                wb.write(fos)
                wb.close()
            }
            return file
        } catch (_: Exception) { return null }
    }

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
