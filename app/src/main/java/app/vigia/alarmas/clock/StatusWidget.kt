package app.vigia.alarmas.clock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import app.vigia.alarmas.Graph
import app.vigia.alarmas.R
import app.vigia.alarmas.alarm.ActionReceiver
import app.vigia.alarmas.alarm.runAsync
import app.vigia.alarmas.data.Settings
import app.vigia.alarmas.domain.APP_LOCALE
import app.vigia.alarmas.domain.AlarmEngine
import app.vigia.alarmas.domain.DayState
import app.vigia.alarmas.domain.DayStatus
import app.vigia.alarmas.domain.tr
import app.vigia.alarmas.ui.MainActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.cos
import kotlin.math.sin

data class WidgetData(val focus: DayStatus, val week: List<DayStatus>)

object StatusText {
    private val HOUR: DateTimeFormatter get() = DateTimeFormatter.ofPattern("HH:mm")

    fun dayName(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> tr("Hoy", "Today")
            today.plusDays(1) -> tr("Mañana", "Tomorrow")
            else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, APP_LOCALE)
                .replaceFirstChar { it.uppercase(APP_LOCALE) }
        }
    }

    fun pill(status: DayStatus): String = when (status.state) {
        DayState.RINGS -> tr("Suena", "Rings")
        DayState.SKIPPED -> tr("No suena", "Won't ring")
        DayState.DEPENDS -> tr("Puede sonar", "May ring")
        DayState.NO_DATA -> tr("Sonará", "Will ring")
        DayState.NONE -> tr("Sin alarmas", "No alarms")
    }

    fun weekRow(status: DayStatus): String = when (status.state) {
        DayState.SKIPPED -> status.detail ?: pill(status)
        DayState.NONE -> "—"
        else -> listOfNotNull(time(status), status.detail?.takeIf { it.isNotBlank() }).joinToString("  ")
    }

    fun time(status: DayStatus): String =
        if (status.state == DayState.SKIPPED || status.state == DayState.NONE) {
            "—"
        } else {
            status.time?.format(HOUR) ?: "—"
        }

    fun detail(status: DayStatus): String = when (status.state) {
        DayState.DEPENDS -> AlarmEngine.CONTEXT_NOTE
        DayState.NONE -> tr("No hay nada programado.", "Nothing scheduled.")
        else -> status.detail.orEmpty()
    }

    fun action(status: DayStatus): String? = when {
        status.alarmId == null -> null
        status.state == DayState.SKIPPED -> tr("Sonar igualmente", "Ring anyway")
        else -> tr("Saltar", "Skip")
    }

    fun tile(status: DayStatus): String {
        val day = dayName(status.date)
        return if (status.state == DayState.SKIPPED || status.state == DayState.NONE) {
            tr("$day: no suena", "$day: won't ring")
        } else {
            "$day: ${time(status)}"
        }
    }
}

private enum class Tier { MINI, SMALL, MEDIUM, WEEK }

open class StatusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        runAsync { Graph.coordinator(context).refreshWidgets() }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        options: Bundle,
    ) {
        runAsync { Graph.coordinator(context).refreshWidgets() }
    }

    companion object {
        private const val MINI_HEIGHT_DP = 100
        private const val MINI_STAYS_HEIGHT_DP = 150
        private const val SMALL_WIDTH_DP = 230
        private const val WEEK_HEIGHT_DP = 230
        private const val DIAL_SIZE = 252

        /** Cada tamaño de la lista de widgets arranca con su diseño y se adapta si lo redimensionas. */
        fun show(context: Context, data: WidgetData) {
            val manager = AppWidgetManager.getInstance(context)
            val alpha = Settings(context).widgetOpacity * 255 / 100
            mapOf(
                StatusWidgetMini::class.java to Tier.MINI,
                StatusWidgetSmall::class.java to Tier.SMALL,
                StatusWidget::class.java to Tier.MEDIUM,
                StatusWidgetWeek::class.java to Tier.WEEK,
            ).forEach { (provider, tier) ->
                manager.getAppWidgetIds(ComponentName(context, provider)).forEach { id ->
                    val options = manager.getAppWidgetOptions(id)
                    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                    manager.updateAppWidget(id, views(context, data, tierFor(tier, width, height), alpha))
                }
            }
        }

        private fun tierFor(default: Tier, width: Int, height: Int): Tier = when {
            width == 0 || height == 0 -> default
            height < MINI_HEIGHT_DP || (default == Tier.MINI && height < MINI_STAYS_HEIGHT_DP) -> Tier.MINI
            width < SMALL_WIDTH_DP -> Tier.SMALL
            height >= WEEK_HEIGHT_DP -> Tier.WEEK
            else -> Tier.MEDIUM
        }

        private fun views(context: Context, data: WidgetData, tier: Tier, alpha: Int): RemoteViews {
            val status = data.focus
            val layout = when (tier) {
                Tier.MINI -> R.layout.widget_status_mini
                Tier.SMALL -> R.layout.widget_status_small
                Tier.MEDIUM -> R.layout.widget_status
                Tier.WEEK -> R.layout.widget_status_week
            }
            val views = RemoteViews(context.packageName, layout)
            views.setInt(R.id.widget_bg, "setImageAlpha", alpha)
            views.setTextViewText(R.id.status_day, StatusText.dayName(status.date))
            views.setTextViewText(R.id.status_time, StatusText.time(status))
            views.setTextViewText(R.id.status_detail, StatusText.detail(status))

            if (tier == Tier.MINI || tier == Tier.SMALL) {
                views.setTextViewText(R.id.status_pill, StatusText.pill(status))
                applyPill(context, views, R.id.status_pill, status.state)
            } else {
                views.setImageViewBitmap(R.id.status_dial, dial(status))
            }

            val action = StatusText.action(status)
            if (tier == Tier.MEDIUM && action != null) {
                views.setTextViewText(R.id.status_action, action)
                views.setViewVisibility(R.id.status_action, View.VISIBLE)
                views.setInt(
                    R.id.status_action,
                    "setBackgroundResource",
                    if (status.state == DayState.SKIPPED) R.drawable.widget_pill_ring else R.drawable.widget_pill_skip,
                )
                views.setOnClickPendingIntent(R.id.status_action, actionIntent(context, status))
            }

            if (tier == Tier.WEEK) fillWeek(context, views, data.week)

            views.setOnClickPendingIntent(R.id.status_refresh, refreshIntent(context))
            views.setOnClickPendingIntent(R.id.status_root, openIntent(context))
            return views
        }

        private fun fillWeek(context: Context, views: RemoteViews, week: List<DayStatus>) {
            week.forEachIndexed { index, day ->
                val dayId = context.resources.getIdentifier("week_day_$index", "id", context.packageName)
                val rowId = context.resources.getIdentifier("week_row_$index", "id", context.packageName)
                val name = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, APP_LOCALE).take(2)
                    .replaceFirstChar { it.uppercase(APP_LOCALE) }
                views.setTextViewText(dayId, "$name ${day.date.dayOfMonth}")
                views.setTextViewText(rowId, StatusText.weekRow(day))
                applyPill(context, views, rowId, day.state)
            }
        }

        private fun applyPill(context: Context, views: RemoteViews, id: Int, state: DayState) {
            val background = when (state) {
                DayState.RINGS -> R.drawable.widget_pill_ring
                DayState.SKIPPED -> R.drawable.widget_pill_skip
                DayState.DEPENDS -> R.drawable.widget_pill_depends
                DayState.NO_DATA -> R.drawable.widget_pill_nodata
                DayState.NONE -> R.drawable.widget_pill_none
            }
            views.setInt(id, "setBackgroundResource", background)
            val text = if (state == DayState.NONE) {
                ContextCompat.getColor(context, R.color.widget_text)
            } else {
                0xFF14183F.toInt()
            }
            views.setTextColor(id, text)
        }

        /** Esfera con las agujas en la hora de la alarma, como el widget del reloj del sistema. */
        private fun dial(status: DayStatus): Bitmap {
            val size = DIAL_SIZE.toFloat()
            val center = size / 2f
            val bitmap = Bitmap.createBitmap(DIAL_SIZE, DIAL_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
            val accent = when (status.state) {
                DayState.RINGS -> 0xFFA9C5FF.toInt()
                DayState.SKIPPED -> 0xFFFFC97A.toInt()
                DayState.DEPENDS -> 0xFFCDBBFF.toInt()
                DayState.NO_DATA -> 0xFFFF9F8E.toInt()
                DayState.NONE -> 0xFF8D90A0.toInt()
            }

            paint.color = 0xFF2A2C33.toInt()
            canvas.drawCircle(center, center, center - 2f, paint)

            paint.color = 0xFF8D90A0.toInt()
            for (hour in 0 until 12) {
                val angle = Math.toRadians(hour * 30.0)
                val major = hour % 3 == 0
                paint.strokeWidth = if (major) 7f else 4f
                val inner = size * if (major) 0.40f else 0.42f
                val outer = size * 0.46f
                canvas.drawLine(
                    center + inner * sin(angle).toFloat(),
                    center - inner * cos(angle).toFloat(),
                    center + outer * sin(angle).toFloat(),
                    center - outer * cos(angle).toFloat(),
                    paint,
                )
            }

            val time = status.time
            if (time == null || status.state == DayState.SKIPPED || status.state == DayState.NONE) {
                paint.color = accent
                paint.strokeWidth = 12f
                canvas.drawLine(center - size * 0.2f, center, center + size * 0.2f, center, paint)
                return bitmap
            }

            val minuteAngle = Math.toRadians(time.minute * 6.0)
            val hourAngle = Math.toRadians((time.hour % 12) * 30.0 + time.minute * 0.5)
            paint.color = 0xFFFFFFFF.toInt()
            paint.strokeWidth = 11f
            canvas.drawLine(
                center,
                center,
                center + size * 0.24f * sin(hourAngle).toFloat(),
                center - size * 0.24f * cos(hourAngle).toFloat(),
                paint,
            )
            paint.color = accent
            paint.strokeWidth = 7f
            canvas.drawLine(
                center,
                center,
                center + size * 0.31f * sin(minuteAngle).toFloat(),
                center - size * 0.31f * cos(minuteAngle).toFloat(),
                paint,
            )
            canvas.drawCircle(center, center, 9f, paint)
            return bitmap
        }

        private fun actionIntent(context: Context, status: DayStatus): PendingIntent {
            val action = if (status.state == DayState.SKIPPED) ActionReceiver.ACTION_FORCE else ActionReceiver.ACTION_SKIP
            return PendingIntent.getBroadcast(
                context,
                70,
                Intent(context, ActionReceiver::class.java)
                    .setAction(action)
                    .putExtra(ActionReceiver.EXTRA_ALARM_ID, status.alarmId ?: 0L)
                    .putExtra(ActionReceiver.EXTRA_DATE, status.date.toString()),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            71,
            Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun openIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            72,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class StatusWidgetMini : StatusWidget()

class StatusWidgetSmall : StatusWidget()

class StatusWidgetWeek : StatusWidget()
