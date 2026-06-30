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
    suspend fun generateReport(context: Context, format: String = "xlsx", period: String = "all"): String? = withContext(Dispatchers.IO) {
        val filtered = filterByPeriod(StorageManager.getUnexportedRecords(context), period)
        if (filtered.isEmpty()) return@withContext null
        if (format == "csv") { val f = StorageManager.exportCSV(context, filtered); if (f != null) { StorageManager.markAsExported(context, filtered.map { it.id }); return@withContext f.absolutePath } return@withContext null }
        generateXlsx(context, filtered)
    }
    private fun filterByPeriod(records: List<com.zor.monitor.models.Record>, period: String): List<com.zor.monitor.models.Record> {
        if (period == "all") return records
        val cal = Calendar.getInstance(); val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()); val today = df.format(cal.time)
        return when (period) {
            "today" -> records.filter { it.date == today }
            "week" -> { cal.add(Calendar.DAY_OF_YEAR, -7); records.filter { it.date >= df.format(cal.time) } }
            "month" -> { cal.add(Calendar.DAY_OF_YEAR, -30); records.filter { it.date >= df.format(cal.time) } }
            else -> records
        }
    }
    private fun generateXlsx(context: Context, records: List<com.zor.monitor.models.Record>): String {
        val df = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val filename = "отчёт_${df.format(Date())}.xlsx"
        val dir = File(context.cacheDir, "reports").also { it.mkdirs() }
        val file = File(dir, filename)
        val wb = XSSFWorkbook(); val sheet = wb.createSheet("Данные")
        listOf("Дата","Время","Направление","Точка","Тип","Частота видео","Частота управления","Подавлен","Голосовой ввод").forEachIndexed { i, h -> sheet.createRow(0).createCell(i).setCellValue(h) }
        records.forEachIndexed { i, r ->
            val row = sheet.createRow(i + 1)
            listOf(r.date, r.time, r.direction, r.point, r.type, r.freqVideo, r.freqControl, r.suppressed, r.voiceText).forEachIndexed { j, v -> row.createCell(j).setCellValue(v) }
        }
        FileOutputStream(file).use { wb.write(it) }; wb.close()
        val pub = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename)
        file.copyTo(pub, overwrite = true)
        StorageManager.markAsExported(context, records.map { it.id })
        return pub.absolutePath
    }
}
