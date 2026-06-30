package com.zor.monitor.worker

import android.content.Context
import androidx.work.*
import com.zor.monitor.utils.StorageManager
import java.text.SimpleDateFormat
import java.util.*

class ReportReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return if (StorageManager.loadRecords(applicationContext).any { it.date == today && it.exported }) Result.success()
        else { NotificationHelper.show(applicationContext); Result.success() }
    }
}
