package app.vigia.alarmas.data

import app.vigia.alarmas.domain.tr
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.vigia.alarmas.domain.AlarmConfig
import app.vigia.alarmas.domain.AlarmSpec
import app.vigia.alarmas.domain.AppJson
import app.vigia.alarmas.domain.TimerStep
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val enabled: Boolean = true,
    val configJson: String = encodeConfig(AlarmConfig()),
    val skipDate: String? = null,
    val forceDate: String? = null,
    val snoozeUntil: Long? = null,
    val snoozeCount: Int = 0,
    val backupPending: Boolean = false,
    val stoppedDate: String? = null,
    val oneTime: String? = null,
) {
    val config: AlarmConfig
        get() = runCatching {
            AppJson.decodeFromString(AlarmConfig.serializer(), configJson)
        }.getOrDefault(AlarmConfig(label = tr("Alarma", "Alarm")))

    val oneTimeMoment: LocalDateTime?
        get() = oneTime?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

    /** Cambio puntual pendiente. Con repetición en rango se mantiene todo ese día. */
    val hasActiveOneTime: Boolean
        get() {
            val moment = oneTimeMoment ?: return false
            val today = LocalDate.now()
            return moment.toLocalDate().isAfter(today) ||
                (moment.toLocalDate() == today && (moment.isAfter(LocalDateTime.now()) || config.burst != null))
        }

    val hasActiveSkip: Boolean
        get() = parseDate(skipDate)?.let { !it.isBefore(LocalDate.now()) } ?: false

    fun toSpec() = AlarmSpec(
        id = id,
        enabled = enabled,
        config = config,
        skipDate = parseDate(skipDate),
        forceDate = parseDate(forceDate),
        stoppedDate = parseDate(stoppedDate),
        oneTime = oneTimeMoment,
    )

    fun withConfig(value: AlarmConfig) = copy(configJson = encodeConfig(value))

    companion object {
        fun encodeConfig(config: AlarmConfig): String =
            AppJson.encodeToString(AlarmConfig.serializer(), config)

        private fun parseDate(text: String?): LocalDate? =
            text?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }
}

@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val stepsJson: String = encodeSteps(listOf(TimerStep())),
    val loops: Int = 1,
    val running: Boolean = false,
    val stepIndex: Int = 0,
    val loopIndex: Int = 0,
    val endAt: Long? = null,
    val remainingMs: Long? = null,
) {
    val steps: List<TimerStep>
        get() = runCatching {
            AppJson.decodeFromString(ListSerializer(TimerStep.serializer()), stepsJson)
        }.getOrDefault(listOf(TimerStep()))

    companion object {
        fun encodeSteps(steps: List<TimerStep>): String =
            AppJson.encodeToString(ListSerializer(TimerStep.serializer()), steps)
    }
}

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val alarmId: Long,
    val label: String,
    val type: String,
    val detail: String = "",
)

object HistoryType {
    const val RANG = "Sonó"
    const val DISMISSED = "Apagada"
    const val SNOOZED = "Pospuesta"
    const val SKIPPED = "Saltada"
    const val UNANSWERED = "Sin respuesta"
    const val BACKUP = "Respaldo"
    const val FORCED = "Sonará igualmente"
    const val STOPPED = "Detenida por hoy"
    const val ONE_TIME = "Cambio puntual"
    const val WARNING = "Aviso"
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms")
    suspend fun all(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun byId(id: Long): AlarmEntity?

    @Insert
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Delete
    suspend fun delete(alarm: AlarmEntity)

    @Query("DELETE FROM alarms")
    suspend fun deleteAll()
}

@Dao
interface TimerDao {
    @Query("SELECT * FROM timers ORDER BY id")
    fun observeAll(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers")
    suspend fun all(): List<TimerEntity>

    @Query("SELECT * FROM timers WHERE id = :id")
    suspend fun byId(id: Long): TimerEntity?

    @Insert
    suspend fun insert(timer: TimerEntity): Long

    @Update
    suspend fun update(timer: TimerEntity)

    @Delete
    suspend fun delete(timer: TimerEntity)

    @Query("DELETE FROM timers")
    suspend fun deleteAll()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT 300")
    fun observeRecent(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE timestamp >= :from")
    suspend fun since(from: Long): List<HistoryEntry>

    @Insert
    suspend fun insert(entry: HistoryEntry)

    @Query("SELECT COUNT(*) FROM history WHERE alarmId = :alarmId AND type = :type AND detail = :detail")
    suspend fun count(alarmId: Long, type: String, detail: String): Int
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE alarms ADD COLUMN stoppedDate TEXT")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE alarms ADD COLUMN oneTime TEXT")
    }
}

@Database(
    entities = [AlarmEntity::class, TimerEntity::class, HistoryEntry::class],
    version = 4,
    exportSchema = false,
)
abstract class VigiaDatabase : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    abstract fun timers(): TimerDao
    abstract fun history(): HistoryDao
}
