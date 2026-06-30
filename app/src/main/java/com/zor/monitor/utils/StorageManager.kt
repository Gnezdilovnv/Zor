package com.zor.monitor.utils

import android.content.Context
import android.os.Environment
import com.zor.monitor.models.Record
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object StorageManager {
    private const val RECORDS_KEY = "records"
    private const val SETTINGS_KEY = "settings"
    private const val CUSTOM_KEY = "custom_lists"
    private val gson = Gson()

    fun loadRecords(context: Context): List<Record> {
        val json = context.getSharedPreferences("app_data", Context.MODE_PRIVATE).getString(RECORDS_KEY, null) ?: return emptyList()
        return gson.fromJson(json, object : TypeToken<List<Record>>() {}.type)
    }
    fun saveRecords(context: Context, records: List<Record>) {
        context.getSharedPreferences("app_data", Context.MODE_PRIVATE).edit().putString(RECORDS_KEY, gson.toJson(records)).apply()
        backupToFile(context, records)
    }
    fun addRecord(context: Context, record: Record) { saveRecords(context, loadRecords(context).toMutableList().also { it.add(record) }) }
    fun deleteRecord(context: Context, id: String) { saveRecords(context, loadRecords(context).filter { it.id != id }) }
    fun getUnexportedRecords(context: Context) = loadRecords(context).filter { !it.exported }
    fun markAsExported(context: Context, ids: List<String>) { saveRecords(context, loadRecords(context).map { if (it.id in ids) it.copy(exported = true) else it }) }

    private fun backupToFile(context: Context, records: List<Record>) {
        try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ZorBackup").also { it.mkdirs() }
            val df = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            File(dir, "backup_${df.format(Date())}.json").writeText(gson.toJson(records))
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    fun exportCSV(context: Context, records: List<Record>): File? = try {
        val df = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "отчёт_${df.format(Date())}.csv")
        file.bufferedWriter().use { w ->
            w.write("Дата,Время,Направление,Точка,Тип,Частота видео,Частота управления,Подавлен,Голосовой ввод\n")
            records.forEach { r -> w.write("\"${r.date}\",\"${r.time}\",\"${r.direction}\",\"${r.point}\",\"${r.type}\",\"${r.freqVideo}\",\"${r.freqControl}\",\"${r.suppressed}\",\"${r.voiceText}\"\n") }
        }; file
    } catch (_: Exception) { null }

    fun loadSettings(context: Context): Map<String, String> {
        val json = context.getSharedPreferences("app_data", Context.MODE_PRIVATE).getString(SETTINGS_KEY, null) ?: return mapOf("direction" to "", "point" to "", "theme" to "light", "export_format" to "xlsx", "report_period" to "all")
        return gson.fromJson(json, object : TypeToken<Map<String, String>>() {}.type)
    }
    fun saveSettings(context: Context, s: Map<String, String>) { context.getSharedPreferences("app_data", Context.MODE_PRIVATE).edit().putString(SETTINGS_KEY, gson.toJson(s)).apply() }

    fun loadCustomLists(context: Context): Map<String, List<String>> {
        val json = context.getSharedPreferences("app_data", Context.MODE_PRIVATE).getString(CUSTOM_KEY, null) ?: return getDefaults()
        return gson.fromJson(json, object : TypeToken<Map<String, List<String>>>() {}.type)
    }
    fun saveCustomLists(context: Context, l: Map<String, List<String>>) { context.getSharedPreferences("app_data", Context.MODE_PRIVATE).edit().putString(CUSTOM_KEY, gson.toJson(l)).apply() }
    fun getDefaults() = mapOf("types" to listOf("FPV","DJI","Яга","Крыло","Радар","Радар ударный","Перехватчик"), "directions" to listOf("Днепряны","Тарасовка","Подокалинозка","Маячка"), "points" to listOf("Нарцисс","Пион","Ландыш","Гладиолус","Хризантема"))
}
