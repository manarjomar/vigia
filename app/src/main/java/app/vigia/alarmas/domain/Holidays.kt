package app.vigia.alarmas.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

object Holidays {
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    /** Devuelve las fiestas del año. En Andalucía, las que caen en domingo pasan al lunes. */
    fun forYear(year: Int, andalucia: Boolean): Map<LocalDate, String> {
        val easter = easterSunday(year)
        val fixed = buildMap {
            put(LocalDate.of(year, 1, 1), tr("Año Nuevo", "New Year's Day"))
            put(LocalDate.of(year, 1, 6), tr("Reyes", "Epiphany"))
            put(LocalDate.of(year, 5, 1), tr("Día del Trabajo", "Labour Day"))
            put(LocalDate.of(year, 8, 15), tr("Asunción", "Assumption Day"))
            put(LocalDate.of(year, 10, 12), tr("Fiesta Nacional", "National Day"))
            put(LocalDate.of(year, 11, 1), tr("Todos los Santos", "All Saints' Day"))
            put(LocalDate.of(year, 12, 6), tr("Constitución", "Constitution Day"))
            put(LocalDate.of(year, 12, 8), tr("Inmaculada", "Immaculate Conception"))
            put(LocalDate.of(year, 12, 25), tr("Navidad", "Christmas Day"))
            if (andalucia) put(LocalDate.of(year, 2, 28), tr("Día de Andalucía", "Andalusia Day"))
        }
        val moved = fixed.mapKeys { (date, _) ->
            if (andalucia && date.dayOfWeek == DayOfWeek.SUNDAY) date.plusDays(1) else date
        }
        val easterDays = buildMap {
            put(easter.minusDays(2), tr("Viernes Santo", "Good Friday"))
            if (andalucia) put(easter.minusDays(3), tr("Jueves Santo", "Maundy Thursday"))
        }
        return moved + easterDays
    }

    fun nameOf(date: LocalDate, andalucia: Boolean): String? = forYear(date.year, andalucia)[date]
}

sealed interface HolidayAnswer {
    data class Named(val name: String) : HolidayAnswer
    data object None : HolidayAnswer
    data object Unknown : HolidayAnswer
}

fun interface HolidayLookup {
    fun find(country: String, subdivision: String, date: LocalDate): HolidayAnswer
}

/** España y Andalucía se calculan sin conexión. El resto de lugares depende de los datos guardados. */
object OfflineHolidays : HolidayLookup {
    const val ANDALUCIA = "ES-AN"

    fun covers(country: String, subdivision: String): Boolean =
        country == "ES" && (subdivision.isBlank() || subdivision == ANDALUCIA)

    override fun find(country: String, subdivision: String, date: LocalDate): HolidayAnswer {
        if (!covers(country, subdivision)) return HolidayAnswer.Unknown
        val name = Holidays.nameOf(date, subdivision == ANDALUCIA)
        return if (name == null) HolidayAnswer.None else HolidayAnswer.Named(name)
    }
}

@Serializable
data class PublicHoliday(
    val date: String,
    val localName: String = "",
    val name: String = "",
    val global: Boolean = true,
    val counties: List<String>? = null,
    val types: List<String>? = null,
)

object PublicHolidays {
    fun parse(text: String): List<PublicHoliday> =
        AppJson.decodeFromString(ListSerializer(PublicHoliday.serializer()), text)
            .filter { it.types == null || "Public" in it.types }

    fun regions(holidays: List<PublicHoliday>): List<String> =
        holidays.flatMap { it.counties.orEmpty() }.distinct().sorted()

    fun find(holidays: List<PublicHoliday>, subdivision: String, date: LocalDate): HolidayAnswer {
        val text = date.toString()
        val match = holidays.firstOrNull { holiday ->
            holiday.date == text &&
                (holiday.counties.isNullOrEmpty() || (subdivision.isNotBlank() && subdivision in holiday.counties))
        }
        return if (match == null) {
            HolidayAnswer.None
        } else {
            HolidayAnswer.Named(match.localName.ifBlank { match.name })
        }
    }
}
