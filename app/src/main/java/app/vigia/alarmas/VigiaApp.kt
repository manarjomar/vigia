package app.vigia.alarmas

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.vigia.alarmas.alarm.Notifications
import app.vigia.alarmas.alarm.ReconcileWorker
import app.vigia.alarmas.data.Settings
import app.vigia.alarmas.ui.AppearanceState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VigiaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings(this).applyLanguage()
        AppearanceState.load(Settings(this))
        Notifications.createChannels(this)

        val work = PeriodicWorkRequestBuilder<ReconcileWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "reconcile",
            ExistingPeriodicWorkPolicy.KEEP,
            work,
        )

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            Graph.coordinator(this@VigiaApp).reconcile()
        }
    }
}
