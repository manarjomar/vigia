package app.vigia.alarmas.data

import app.vigia.alarmas.domain.tr
import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.vigia.alarmas.domain.AlarmEngine
import app.vigia.alarmas.domain.CalendarEvent
import app.vigia.alarmas.domain.CalendarSnapshot
import java.time.LocalDate
import java.time.ZoneId

data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val syncing: Boolean,
)

class CalendarRepository(private val context: Context) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun calendars(): List<CalendarInfo> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.SYNC_EVENTS,
        )
        val result = mutableListOf<CalendarInfo>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    result += CalendarInfo(
                        id = cursor.getLong(0),
                        name = cursor.getString(1).orEmpty().ifBlank { tr("Sin nombre", "Unnamed") },
                        account = cursor.getString(2).orEmpty().ifBlank { tr("En el móvil", "On this phone") },
                        syncing = cursor.getInt(3) == 1,
                    )
                }
            }
        }
        return result
    }

    fun snapshot(calendarIds: Set<Long>, from: LocalDate, days: Long): CalendarSnapshot {
        if (calendarIds.isEmpty()) return CalendarSnapshot.Available(emptyList())
        if (!hasPermission()) return CalendarSnapshot.Unavailable

        val zone = ZoneId.systemDefault()
        val begin = from.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = from.plusDays(days + 2).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.AVAILABILITY,
        )

        return try {
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
                ?: return CalendarSnapshot.Unavailable
            val events = mutableListOf<CalendarEvent>()
            cursor.use {
                while (it.moveToNext()) {
                    val calendarId = it.getLong(0)
                    val cancelled = !it.isNull(5) &&
                        it.getInt(5) == CalendarContract.Instances.STATUS_CANCELED
                    if (calendarId !in calendarIds || cancelled) continue

                    val allDay = it.getInt(4) == 1
                    val (first, last) = AlarmEngine.eventDates(
                        it.getLong(2),
                        it.getLong(3),
                        allDay,
                        zone,
                    )
                    events += CalendarEvent(
                        calendarId = calendarId,
                        title = it.getString(1).orEmpty(),
                        firstDate = first,
                        lastDate = last,
                        allDay = allDay,
                        busy = it.isNull(6) ||
                            it.getInt(6) != CalendarContract.Instances.AVAILABILITY_FREE,
                        startMillis = it.getLong(2),
                    )
                }
            }
            CalendarSnapshot.Available(events)
        } catch (_: Exception) {
            CalendarSnapshot.Unavailable
        }
    }
}
