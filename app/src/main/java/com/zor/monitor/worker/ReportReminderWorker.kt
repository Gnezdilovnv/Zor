package com.zor.monitor.worker

import android.content.Context
import androidx.work.*
import com.zor.monitor.utils.StorageManager
import java.text.SimpleDateFormat
import java.util.*

class ReportReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val records = StorageManager.loadRecords(applicationContext)
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val needsReminder = records.any { it.isoDate == todayIso && !it.exported }
        if (needsReminder) {
            NotificationHelper.show(applicationContext)
        }
        return Result.success()
    }
}
