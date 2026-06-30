package com.zor.monitor.utils

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ReportGenerator {
    private fun getVzorDir() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Vzor")

    suspend fun generateReport(context: Context, format: String = "xlsx", period: String = "all"): String? = withContext(Dispatchers.IO) {
        val filtered = filterByPeriod(StorageManager.getUnexportedRecords(context), period)
        if (filtered.isEmpty()) return@withContext null
        val settings = StorageManager.loadSettings(context)
        val direction = settings["direction"]?.takeIf { it.isNotBlank() } ?: "направление"
        val now = Date()
        val datePart = SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(now)
        val timePart = SimpleDateFormat("HH.mm", Locale.getDefault()).format(now)
        val baseName = "${datePart}_${timePart}_$direction"

        val file = when (format) {
            "csv" -> StorageManager.exportCSV(context, filtered, baseName)
            "json" -> StorageManager.exportJSON(context, filtered, baseName)
            else -> generateXlsx(context, filtered, baseName)
        }
        if (file != null) {
            StorageManager.markAsExported(context, filtered.map { it.id })
            return@withContext file.absolutePath
        }
        null
    }

    private fun filterByPeriod(records: List<com.zor.monitor.models.Record>, period: String): List<com.zor.monitor.models.Record> {
        if (period == "all") return records

        // Парсеры: из dd.MM.yy -> Date -> yyyy-MM-dd для сравнения
        val inputFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
        val comparableFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val today = comparableFormat.format(cal.time)      // yyyy-MM-dd

        return when (period) {
            "today" -> records.filter {
                try {
                    val date = inputFormat.parse(it.date) ?: return@filter false
                    comparableFormat.format(date) == today
                } catch (_: Exception) { false }
            }
            "week" -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                val weekAgo = comparableFormat.format(cal.time)
                records.filter {
                    try {
                        val date = inputFormat.parse(it.date) ?: return@filter false
                        comparableFormat.format(date) >= weekAgo
                    } catch (_: Exception) { false }
                }
            }
            "month" -> {
                cal.add(Calendar.DAY_OF_YEAR, -30)
                val monthAgo = comparableFormat.format(cal.time)
                records.filter {
                    try {
                        val date = inputFormat.parse(it.date) ?: return@filter false
                        comparableFormat.format(date) >= monthAgo
                    } catch (_: Exception) { false }
                }
            }
            else -> records
        }
    }

    private fun generateXlsx(context: Context, records: List<com.zor.monitor.models.Record>, baseName: String): File? {
        val filename = "$baseName.xlsx"
        val cacheDir = File(context.cacheDir, "reports").also { it.mkdirs() }
        val file = File(cacheDir, filename)
        try {
            val wb = XSSFWorkbook()
            val sheet = wb.createSheet("Данные")
            val headers = listOf("Дата","Время","Тип","Частота видео","Частота управления","Подавлен","Точка","Направление")
            val headerRow = sheet.createRow(0)
            headers.forEachIndexed { i, h -> headerRow.createCell(i).setCellValue(h) }
            records.forEachIndexed { i, r ->
                val row = sheet.createRow(i + 1)
                listOf(r.date, r.time, r.type, r.freqVideo, r.freqControl, r.suppressed, r.point, r.direction)
                    .forEachIndexed { j, v -> row.createCell(j).setCellValue(v) }
            }
            FileOutputStream(file).use { wb.write(it) }
            wb.close()
            val vzorDir = getVzorDir().also { it.mkdirs() }
            val pub = File(vzorDir, filename)
            file.copyTo(pub, overwrite = true)
            return pub
        } catch (_: Exception) { return null }
    }
}
