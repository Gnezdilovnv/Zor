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
    suspend fun generateReport(c: Context, f: String = "xlsx", p: String = "all"): String? = withContext(Dispatchers.IO) {
        val flt = filter(StorageManager.getUnexportedRecords(c), p)
        if (flt.isEmpty()) return@withContext null
        if (f == "csv") {
            val ff = StorageManager.exportCSV(c, flt)
            if (ff != null) {
                StorageManager.markAsExported(c, flt.map { it.id })
                return@withContext ff.absolutePath
            }
            return@withContext null
        }
        generateXlsx(c, flt)
    }

    private fun filter(r: List<com.zor.monitor.models.Record>, p: String): List<com.zor.monitor.models.Record> {
        if (p == "all") return r
        val cal = Calendar.getInstance()
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val t = df.format(cal.time)
        return when (p) {
            "today" -> r.filter { it.date == t }
            "week" -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                r.filter { it.date >= df.format(cal.time) }
            }
            "month" -> {
                cal.add(Calendar.DAY_OF_YEAR, -30)
                r.filter { it.date >= df.format(cal.time) }
            }
            else -> r
        }
    }

    private fun generateXlsx(c: Context, r: List<com.zor.monitor.models.Record>): String {
        val fn = "отчёт_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.xlsx"
        val d = File(c.cacheDir, "reports").also { it.mkdirs() }
        val ff = File(d, fn)
        val wb = XSSFWorkbook()
        val s = wb.createSheet("Данные")
        listOf("Дата","Время","Направление","Точка","Тип","Частота видео","Частота управления","Подавлен","Голосовой ввод")
            .forEachIndexed { i, h -> s.createRow(0).createCell(i).setCellValue(h) }
        r.forEachIndexed { i, v ->
            val row = s.createRow(i + 1)
            listOf(v.date, v.time, v.direction, v.point, v.type, v.freqVideo, v.freqControl, v.suppressed, v.voiceText)
                .forEachIndexed { j, x -> row.createCell(j).setCellValue(x) }
        }
        FileOutputStream(ff).use { wb.write(it) }
        wb.close()
        val pub = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fn)
        ff.copyTo(pub, true)
        StorageManager.markAsExported(c, r.map { it.id })
        return pub.absolutePath
    }
}
