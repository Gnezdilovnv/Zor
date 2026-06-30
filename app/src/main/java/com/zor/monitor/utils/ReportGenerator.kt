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
        if (format == "csv") {
            val f = StorageManager.exportCSV(context, filtered, baseName)
            if (f != null) {
                StorageManager.markAsExported(context, filtered.map { it.id })
                return@withContext f.absolutePath
            }
            return@withContext null
        }
        generateXlsx(context, filtered, baseName)
    }

    private fun filterByPeriod(records: List<com.zor.monitor.models.Record>, period: String): List<com.zor.monitor.models.Record> {
        if (period == "all") return records
        val cal = Calendar.getInstance()
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = df.format(cal.time)
        return when (period) {
            "today" -> records.filter { it.date == today }
            "week" -> { cal.add(Calendar.DAY_OF_YEAR, -7); records.filter { it.date >= df.format(cal.time) } }
            "month" -> { cal.add(Calendar.DAY_OF_YEAR, -30); records.filter { it.date >= df.format(cal.time) } }
            else -> records
        }
    }

    private fun generateXlsx(context: Context, records: List<com.zor.monitor.models.Record>, baseName: String): String {
        val filename = "$baseName.xlsx"
        val cacheDir = File(context.cacheDir, "reports").also { it.mkdirs() }
        val file = File(cacheDir, filename)
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
        StorageManager.markAsExported(context, records.map { it.id })
        return pub.absolutePath
    }
}
