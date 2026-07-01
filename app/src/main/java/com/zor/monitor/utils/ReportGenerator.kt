package com.zor.monitor.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object ReportGenerator {
    suspend fun generateReport(context: Context, format: String = "xlsx", period: String = "all"): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val filtered = filterByPeriod(StorageManager.getUnexportedRecords(context), period)
                if (filtered.isEmpty()) return@withContext null
                val settings = StorageManager.loadSettings(context)
                val direction = settings["direction"]?.takeIf { it.isNotBlank() } ?: "направление"
                val now = Date()
                val datePart = SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(now)
                val timePart = SimpleDateFormat("HH.mm", Locale.getDefault()).format(now)
                val baseName = "${datePart}_${timePart}_$direction"

                val uri = when (format) {
                    "csv" -> StorageManager.exportCSV(context, filtered, baseName)
                    "json" -> StorageManager.exportJSON(context, filtered, baseName)
                    else -> StorageManager.exportXLSX(context, filtered, baseName)
                }

                if (uri != null) {
                    StorageManager.markAsExported(context, filtered.map { it.id })
                    return@withContext uri
                }
                null
            } catch (_: Exception) {
                null
            }
        }

    private fun filterByPeriod(records: List<Record>, period: String): List<Record> {
        if (period == "all") return records
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val cal = Calendar.getInstance()
        return when (period) {
            "today" -> records.filter { it.isoDate == todayIso }
            "week" -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                val weekAgo = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                records.filter { it.isoDate >= weekAgo }
            }
            "month" -> {
                cal.add(Calendar.DAY_OF_YEAR, -30)
                val monthAgo = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                records.filter { it.isoDate >= monthAgo }
            }
            else -> records
        }
    }
}
