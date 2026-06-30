package com.zor.monitor.worker

import android.content.Context
import androidx.work.*
import com.zor.monitor.utils.StorageManager
import java.text.SimpleDateFormat
import java.util.*

class ReportReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val records = StorageManager.loadRecords(applicationContext)
        val inputFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
        val todayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = todayFormat.format(Date())

        // Уведомление, если есть хотя бы одна неэкспортированная запись за сегодня
        val needsReminder = records.any { rec ->
            try {
                val date = inputFormat.parse(rec.date) ?: return@any false
                todayFormat.format(date) == today && !rec.exported
            } catch (_: Exception) { false }
        }

        if (needsReminder) {
            NotificationHelper.show(applicationContext)
        }
        return Result.success()
    }
}
