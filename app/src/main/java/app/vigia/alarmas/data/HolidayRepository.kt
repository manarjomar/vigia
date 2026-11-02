package app.vigia.alarmas.data

import android.content.Context
import app.vigia.alarmas.domain.HolidayAnswer
import app.vigia.alarmas.domain.HolidayLookup
import app.vigia.alarmas.domain.OfflineHolidays
import app.vigia.alarmas.domain.PublicHoliday
import app.vigia.alarmas.domain.PublicHolidays
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Descarga los festivos de cada país una vez al mes y los guarda para funcionar sin conexión. */
class HolidayRepository(context: Context) : HolidayLookup {
    private val directory = File(context.filesDir, "festivos").apply { mkdirs() }
    private val memory = ConcurrentHashMap<String, List<PublicHoliday>>()

    override fun find(country: String, subdivision: String, date: LocalDate): HolidayAnswer {
        if (OfflineHolidays.covers(country, subdivision)) {
            return OfflineHolidays.find(country, subdivision, date)
        }
        if (country.isBlank()) return HolidayAnswer.Unknown
        val holidays = load(country, date.year) ?: return HolidayAnswer.Unknown
        return PublicHolidays.find(holidays, subdivision, date)
    }

    fun regions(country: String): List<String> {
        val year = LocalDate.now().year
        return PublicHolidays.regions(load(country, year).orEmpty() + load(country, year + 1).orEmpty())
    }

    /** Devuelve true si hay datos del año actual, descargados ahora o en otro momento. */
    suspend fun ensure(country: String): Boolean = withContext(Dispatchers.IO) {
        val year = LocalDate.now().year
        listOf(year, year + 1).forEach { download(country, it) }
        load(country, year) != null
    }

    private fun file(country: String, year: Int) = File(directory, "$country-$year.json")

    private fun load(country: String, year: Int): List<PublicHoliday>? {
        val key = "$country-$year"
        memory[key]?.let { return it }
        val file = file(country, year)
        if (!file.exists()) return null
        return runCatching { PublicHolidays.parse(file.readText()) }.getOrNull()
            ?.also { memory[key] = it }
    }

    private fun download(country: String, year: Int) {
        val file = file(country, year)
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS) return

        runCatching {
            val connection = URL("$BASE_URL/$year/$country").openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            try {
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    val parsed = PublicHolidays.parse(text)
                    file.writeText(text)
                    memory["$country-$year"] = parsed
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val BASE_URL = "https://date.nager.at/api/v3/PublicHolidays"
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val TIMEOUT_MS = 10_000
    }
}
