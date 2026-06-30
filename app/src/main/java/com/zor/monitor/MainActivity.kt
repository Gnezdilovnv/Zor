package com.zor.monitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.work.*
import com.zor.monitor.ui.MainScreen
import com.zor.monitor.worker.NotificationHelper
import com.zor.monitor.worker.ReportReminderWorker
import com.zor.monitor.utils.StorageManager
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (it.values.all { it }) schedule() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        val need = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) permLauncher.launch(need.toTypedArray()) else schedule()
        setContent {
            val settings = StorageManager.loadSettings(this)
            val systemTheme = settings["system_theme"] != "false" // по умолчанию true
            val colorScheme = when {
                systemTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(this)
                systemTheme && !isDarkSystem() -> lightColorScheme()
                systemTheme && isDarkSystem() -> darkColorScheme()
                else -> lightColorScheme() // системная тема выключена — всегда светлая
            }
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(context = this@MainActivity)
                }
            }
        }
    }
    private fun isDarkSystem(): Boolean {
        return resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    private fun schedule() {
        WorkManager.getInstance(this).cancelAllWorkByTag("report_reminder")
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 16); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1) }
        val r = PeriodicWorkRequestBuilder<ReportReminderWorker>(10, TimeUnit.MINUTES).setInitialDelay(cal.timeInMillis - System.currentTimeMillis(), TimeUnit.MILLISECONDS).setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build()).addTag("report_reminder").build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("daily_reminder", ExistingPeriodicWorkPolicy.REPLACE, r)
    }
}
