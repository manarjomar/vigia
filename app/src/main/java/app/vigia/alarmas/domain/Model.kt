package app.vigia.alarmas.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "tipo"
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

@Serializable
data class DateRange(
    @Serializable(with = LocalDateSerializer::class) val start: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val end: LocalDate,
) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

@Serializable
enum class RuleAction { SKIP, ONLY, ALWAYS }

@Serializable
enum class MatchMode { ANY, ALL }

@Serializable
enum class ContextKind { WIFI, LOCATION, AIRPLANE, DO_NOT_DISTURB, CHARGING }

@Serializable
sealed interface Condition

@Serializable
@SerialName("calendario")
data class CalendarCondition(
    val calendarIds: Set<Long> = emptySet(),
    val keywords: List<String> = emptyList(),
    val allDayOnly: Boolean = true,
    val busyOnly: Boolean = false,
) : Condition

@Serializable
@SerialName("fechas")
data class DatesCondition(val ranges: List<DateRange> = emptyList()) : Condition

/** Fechas que se repiten cada año, en formato MM-dd. */
@Serializable
@SerialName("anual")
data class AnnualCondition(val dates: List<String> = emptyList()) : Condition

@Serializable
@SerialName("festivos")
data class HolidayCondition(
    val andalucia: Boolean = false,
    val country: String = "",
    val subdivision: String = "",
) : Condition {
    /** Las reglas creadas antes de poder elegir país conservan el campo [andalucia]. */
    val effectiveCountry: String get() = if (andalucia) "ES" else country
    val effectiveSubdivision: String get() = if (andalucia) OfflineHolidays.ANDALUCIA else subdivision
}

/** Coincide una semana de cada [everyWeeks], contando desde la semana de [anchor]. */
@Serializable
@SerialName("semanas")
data class WeekCycleCondition(
    @Serializable(with = LocalDateSerializer::class) val anchor: LocalDate,
    val everyWeeks: Int = 2,
) : Condition

/** Turnos: T trabaja y D descansa. El patrón empieza en [anchor] y coincide los días T. */
@Serializable
@SerialName("turnos")
data class ShiftCondition(
    @Serializable(with = LocalDateSerializer::class) val anchor: LocalDate,
    val pattern: String = "TTTTDD",
) : Condition

@Serializable
@SerialName("par_impar")
data class ParityCondition(val even: Boolean = true) : Condition

@Serializable
@SerialName("meses")
data class MonthsCondition(val months: Set<Int> = emptySet()) : Condition

@Serializable
@SerialName("contexto")
data class ContextCondition(
    val kind: ContextKind,
    val ssid: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Int = 200,
    val placeName: String = "",
) : Condition

@Serializable
data class AlarmRule(
    val id: String,
    val action: RuleAction = RuleAction.SKIP,
    val mode: MatchMode = MatchMode.ANY,
    val conditions: List<Condition> = emptyList(),
)

@Serializable
data class RelativeEvent(
    val calendarIds: Set<Long> = emptySet(),
    val keywords: List<String> = emptyList(),
    val minutesBefore: Int = 90,
    val useBaseTimeWhenEmpty: Boolean = false,
)

@Serializable
data class SoundConfig(
    val uri: String? = null,
    val name: String = tr("Sonido de alarma del sistema", "System alarm sound"),
    val volumePercent: Int = 80,
    val fadeSeconds: Int = 30,
    val vibrate: Boolean = true,
)

@Serializable
data class SnoozeConfig(val minutes: Int = 9, val maxCount: Int = 3)

@Serializable
data class BackupConfig(val enabled: Boolean = true, val afterMinutes: Int = 5)

@Serializable
enum class ChallengeKind { NONE, MATH, SHAKE, STEPS, QR }

@Serializable
data class ChallengeConfig(
    val kind: ChallengeKind = ChallengeKind.NONE,
    val amount: Int = 3,
    val qrValue: String = "",
)

/** Repite la alarma cada [everyMinutes] minutos desde su hora hasta el final del rango. */
@Serializable
data class BurstConfig(
    val endHour: Int = 8,
    val endMinute: Int = 0,
    val everyMinutes: Int = 5,
)

@Serializable
data class ShiftType(
    val id: String,
    val name: String,
    val hour: Int,
    val minute: Int,
    val burst: BurstConfig? = null,
)

/**
 * Cuadrante de turnos. [cycle] guarda un turno por día (vacío significa descanso) y se repite desde
 * [start]. [overrides] guarda días pintados o cambios puntuales, y gana al calendario y al ciclo.
 */
@Serializable
data class ShiftPlan(
    val types: List<ShiftType> = emptyList(),
    val cycle: List<String> = emptyList(),
    @Serializable(with = LocalDateSerializer::class) val start: LocalDate = LocalDate.now(),
    val overrides: Map<String, String> = emptyMap(),
    val calendarIds: Set<Long> = emptySet(),
    val unassignedRings: Boolean = true,
)

@Serializable
data class AlarmConfig(
    val label: String = "",
    val group: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val days: Set<DayOfWeek> = emptySet(),
    val rules: List<AlarmRule> = emptyList(),
    val relative: RelativeEvent? = null,
    val shifts: ShiftPlan? = null,
    val burst: BurstConfig? = null,
    val sound: SoundConfig = SoundConfig(),
    val snooze: SnoozeConfig = SnoozeConfig(),
    val backup: BackupConfig = BackupConfig(),
    val challenge: ChallengeConfig = ChallengeConfig(),
    val ringLimitMinutes: Int = 10,
) {
    val isOneShot: Boolean get() = days.isEmpty() && shifts == null

    fun runsOn(date: LocalDate): Boolean = shifts != null || isOneShot || date.dayOfWeek in days

    val calendarIds: Set<Long>
        get() = rules.flatMap { it.conditions }
            .filterIsInstance<CalendarCondition>()
            .flatMap { it.calendarIds }
            .toSet() + relative?.calendarIds.orEmpty() + shifts?.calendarIds.orEmpty()
}

data class AlarmSpec(
    val id: Long = 0,
    val enabled: Boolean = true,
    val config: AlarmConfig = AlarmConfig(),
    val skipDate: LocalDate? = null,
    val forceDate: LocalDate? = null,
    val stoppedDate: LocalDate? = null,
    val oneTime: LocalDateTime? = null,
)

data class CalendarEvent(
    val calendarId: Long,
    val title: String,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
    val allDay: Boolean,
    val busy: Boolean = true,
    val startMillis: Long = 0,
)

sealed interface CalendarSnapshot {
    data class Available(val events: List<CalendarEvent>) : CalendarSnapshot
    data object Unavailable : CalendarSnapshot
}

/** Estado del móvil en el momento de sonar. Un valor nulo significa que no se conoce. */
data class DeviceState(
    val charging: Boolean? = null,
    val airplane: Boolean? = null,
    val doNotDisturb: Boolean? = null,
    val wifiSsid: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
