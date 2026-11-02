package app.vigia.alarmas.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.vigia.alarmas.Graph

class ReconcileWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val coordinator = Graph.coordinator(applicationContext)
        runCatching { coordinator.refreshHolidays() }
        coordinator.reconcile()
        return Result.success()
    }
}
