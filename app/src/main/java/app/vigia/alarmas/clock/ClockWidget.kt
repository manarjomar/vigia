package app.vigia.alarmas.clock

import app.vigia.alarmas.domain.tr
import android.app.PendingIntent
import app.vigia.alarmas.domain.APP_LOCALE
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.vigia.alarmas.R
import app.vigia.alarmas.Graph
import app.vigia.alarmas.alarm.runAsync
import app.vigia.alarmas.data.Settings
import app.vigia.alarmas.ui.MainActivity
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ClockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        runAsync { Graph.coordinator(context).refreshWidgets() }
    }

    companion object {
        private val FORMAT: DateTimeFormatter
            get() = DateTimeFormatter.ofPattern("EEE HH:mm", APP_LOCALE)

        fun show(context: Context, nextAlarm: ZonedDateTime?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, nextAlarm))
        }

        private fun views(context: Context, nextAlarm: ZonedDateTime?): RemoteViews {
            val next = nextAlarm?.format(FORMAT)
            val open = PendingIntent.getActivity(
                context,
                60,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteViews(context.packageName, R.layout.widget_clock).apply {
                setInt(R.id.widget_bg, "setImageAlpha", Settings(context).widgetOpacity * 255 / 100)
                setTextViewText(R.id.widget_next, next?.let { tr("Alarma: $it", "Alarm: $it") } ?: tr("Sin alarmas", "No alarms"))
                setOnClickPendingIntent(R.id.widget_root, open)
            }
        }
    }
}
