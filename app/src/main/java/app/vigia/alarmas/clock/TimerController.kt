package app.vigia.alarmas.clock

import app.vigia.alarmas.domain.tr
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.vigia.alarmas.Graph
import app.vigia.alarmas.alarm.AlarmScheduler
import app.vigia.alarmas.alarm.AlarmService
import app.vigia.alarmas.alarm.Notifications
import app.vigia.alarmas.alarm.runAsync
import app.vigia.alarmas.data.TimerDao
import app.vigia.alarmas.data.TimerEntity
import app.vigia.alarmas.domain.TimerPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TimerController(
    private val context: Context,
    private val timers: TimerDao,
    private val scheduler: AlarmScheduler,
) {
    suspend fun start(id: Long) = withContext(Dispatchers.IO) {
        val timer = timers.byId(id) ?: return@withContext
        if (timer.running) return@withContext
        val step = timer.steps.getOrNull(timer.stepIndex) ?: return@withContext
        val remaining = timer.remainingMs ?: (step.seconds * 1000L)
        arm(timer, System.currentTimeMillis() + remaining)
    }

    suspend fun pause(id: Long) = withContext(Dispatchers.IO) {
        val timer = timers.byId(id) ?: return@withContext
        val endAt = timer.endAt ?: return@withContext
        scheduler.cancelTimer(id)
        Notifications.cancelTimer(context, id)
        timers.update(
            timer.copy(
                running = false,
                endAt = null,
                remainingMs = (endAt - System.currentTimeMillis()).coerceAtLeast(0),
            ),
        )
    }

    suspend fun reset(id: Long) = withContext(Dispatchers.IO) {
        val timer = timers.byId(id) ?: return@withContext
        scheduler.cancelTimer(id)
        Notifications.cancelTimer(context, id)
        timers.update(
            timer.copy(running = false, stepIndex = 0, loopIndex = 0, endAt = null, remainingMs = null),
        )
    }

    suspend fun delete(timer: TimerEntity) = withContext(Dispatchers.IO) {
        scheduler.cancelTimer(timer.id)
        Notifications.cancelTimer(context, timer.id)
        timers.delete(timer)
    }

    suspend fun save(timer: TimerEntity) = withContext(Dispatchers.IO) {
        if (timer.id == 0L) timers.insert(timer) else timers.update(timer)
    }

    suspend fun expired(id: Long) = withContext(Dispatchers.IO) {
        val timer = timers.byId(id) ?: return@withContext
        val steps = timer.steps
        val position = TimerPlan.Position(timer.stepIndex, timer.loopIndex)
        val next = TimerPlan.next(position, steps.size, timer.loops)
        val name = timer.name.ifBlank { tr("Temporizador", "Timer") }

        if (next == null) {
            reset(id)
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java)
                    .setAction(AlarmService.ACTION_TIMER)
                    .putExtra(AlarmService.EXTRA_LABEL, name),
            )
        } else {
            val step = steps[next.step]
            Notifications.timerStep(context, tr("$name: ${step.name.ifBlank { "siguiente tramo" }}", "$name: ${step.name.ifBlank { "next step" }}"))
            arm(
                timer.copy(stepIndex = next.step, loopIndex = next.loop, remainingMs = null),
                System.currentTimeMillis() + step.seconds * 1000L,
            )
        }
    }

    /** Tras reiniciar el móvil, vuelve a programar los temporizadores que seguían en marcha. */
    suspend fun restore() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        timers.all().filter { it.running }.forEach { timer ->
            val endAt = timer.endAt ?: return@forEach
            arm(timer.copy(running = false), maxOf(endAt, now + 1_000))
        }
    }

    private suspend fun arm(timer: TimerEntity, endAt: Long) {
        timers.update(timer.copy(running = true, endAt = endAt, remainingMs = null))
        scheduler.scheduleTimer(timer.id, endAt)
        val step = timer.steps.getOrNull(timer.stepIndex)?.name.orEmpty()
        val title = listOf(timer.name.ifBlank { tr("Temporizador", "Timer") }, step)
            .filter { it.isNotBlank() }
            .joinToString(": ")
        Notifications.postTimer(context, timer.id, title, endAt)
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_TIMER_ID, 0)
        runAsync { Graph.timers(context).expired(id) }
    }
}
