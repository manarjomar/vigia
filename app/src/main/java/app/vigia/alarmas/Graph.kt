package app.vigia.alarmas

import android.content.Context
import androidx.room.Room
import app.vigia.alarmas.alarm.AlarmCoordinator
import app.vigia.alarmas.alarm.AlarmScheduler
import app.vigia.alarmas.alarm.DeviceStateReader
import app.vigia.alarmas.clock.TimerController
import app.vigia.alarmas.data.CalendarRepository
import app.vigia.alarmas.data.HolidayRepository
import app.vigia.alarmas.data.MIGRATION_2_3
import app.vigia.alarmas.data.Settings
import app.vigia.alarmas.data.VigiaDatabase

object Graph {
    @Volatile
    private var database: VigiaDatabase? = null

    @Volatile
    private var holidays: HolidayRepository? = null

    fun database(context: Context): VigiaDatabase =
        database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                VigiaDatabase::class.java,
                "vigia.db",
            ).addMigrations(MIGRATION_2_3).fallbackToDestructiveMigrationFrom(1).build().also { database = it }
        }

    fun holidays(context: Context): HolidayRepository =
        holidays ?: synchronized(this) {
            holidays ?: HolidayRepository(context.applicationContext).also { holidays = it }
        }

    fun coordinator(context: Context): AlarmCoordinator {
        val appContext = context.applicationContext
        return AlarmCoordinator(
            database(appContext),
            CalendarRepository(appContext),
            AlarmScheduler(appContext),
            Settings(appContext),
            DeviceStateReader(appContext),
            holidays(appContext),
            appContext,
        )
    }

    fun timers(context: Context): TimerController {
        val appContext = context.applicationContext
        return TimerController(appContext, database(appContext).timers(), AlarmScheduler(appContext))
    }
}
