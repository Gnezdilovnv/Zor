package com.zor.monitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.work.*
import com.zor.monitor.ui.MainScreen
import com.zor.monitor.ui.SplashScreen
import com.zor.monitor.ui.theme.ZorTheme
import com.zor.monitor.utils.SettingsDataStore
import com.zor.monitor.worker.NotificationHelper
import com.zor.monitor.worker.ReportReminderWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { if (it.values.all { it }) schedule() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        } else {
            schedule()
        }

        setContent {
            var showSplash by remember { mutableStateOf(true) }
            val scope = rememberCoroutineScope()
            val darkThemeFlow = SettingsDataStore.getDarkThemeFlow(this)
            var isDark by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                isDark = darkThemeFlow.first()
            }

            LaunchedEffect(Unit) {
                delay(3000)
                showSplash = false
            }

            ZorTheme(darkTheme = isDark, dynamicColor = true) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (showSplash) {
                        SplashScreen()
                    } else {
                        MainScreen(
                            onThemeChange = { newDark ->
                                scope.launch {
                                    SettingsDataStore.saveDarkTheme(this@MainActivity, newDark)
                                    isDark = newDark
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun schedule() {
        WorkManager.getInstance(this).cancelAllWorkByTag("report_reminder")
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.set(Calendar.HOUR_OF_DAY, 16)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (calendar.before(Calendar.getInstance())) calendar.add(Calendar.DAY_OF_YEAR, 1)
        val initialDelay = calendar.timeInMillis - System.currentTimeMillis()
        val reminderRequest = PeriodicWorkRequestBuilder<ReportReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
            .addTag("report_reminder")
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_report_reminder",
            ExistingPeriodicWorkPolicy.REPLACE,
            reminderRequest
        )
    }
}
