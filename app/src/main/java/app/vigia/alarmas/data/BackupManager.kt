package app.vigia.alarmas.data

import app.vigia.alarmas.domain.tr
import android.content.Context
import android.net.Uri
import app.vigia.alarmas.Graph
import app.vigia.alarmas.domain.AlarmConfig
import app.vigia.alarmas.domain.AppJson
import app.vigia.alarmas.domain.TimerStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class BackupAlarm(val enabled: Boolean, val config: AlarmConfig)

@Serializable
data class BackupTimer(val name: String, val steps: List<TimerStep>, val loops: Int)

@Serializable
data class BackupFile(
    val version: Int = 1,
    val alarms: List<BackupAlarm> = emptyList(),
    val timers: List<BackupTimer> = emptyList(),
    val worldClocks: List<String> = emptyList(),
    val templates: List<AlarmTemplate> = emptyList(),
    val noticeEnabled: Boolean = true,
    val noticeHour: Int = 21,
    val noticeMinute: Int = 0,
)

class BackupManager(private val context: Context) {
    private val database = Graph.database(context)
    private val settings = Settings(context)

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val backup = BackupFile(
            alarms = database.alarms().all().map { BackupAlarm(it.enabled, it.config) },
            timers = database.timers().all().map { BackupTimer(it.name, it.steps, it.loops) },
            worldClocks = settings.worldClocks,
            templates = settings.templates,
            noticeEnabled = settings.noticeEnabled,
            noticeHour = settings.noticeHour,
            noticeMinute = settings.noticeMinute,
        )
        val text = AppJson.encodeToString(BackupFile.serializer(), backup)
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(text.toByteArray())
        } ?: error(tr("No se pudo crear el archivo", "Couldn't create the file"))
    }

    /** Sustituye las alarmas y los temporizadores por los del archivo. */
    suspend fun import(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().decodeToString()
        } ?: error(tr("No se pudo abrir el archivo", "Couldn't open the file"))
        val backup = AppJson.decodeFromString(BackupFile.serializer(), text)

        val coordinator = Graph.coordinator(context)
        database.alarms().all().forEach { coordinator.delete(it) }
        database.timers().deleteAll()
        backup.alarms.forEach { coordinator.save(AlarmEntity(enabled = it.enabled).withConfig(it.config)) }
        backup.timers.forEach {
            database.timers().insert(
                TimerEntity(name = it.name, stepsJson = TimerEntity.encodeSteps(it.steps), loops = it.loops),
            )
        }
        settings.worldClocks = backup.worldClocks
        settings.templates = backup.templates
        settings.noticeEnabled = backup.noticeEnabled
        settings.noticeHour = backup.noticeHour
        settings.noticeMinute = backup.noticeMinute
        coordinator.reconcile()
        backup.alarms.size
    }
}
