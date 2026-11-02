package app.vigia.alarmas.alarm

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import app.vigia.alarmas.domain.DeviceState

class DeviceStateReader(private val context: Context) {
    fun read(): DeviceState {
        val location = lastLocation()
        return DeviceState(
            charging = charging(),
            airplane = runCatching {
                Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            }.getOrNull(),
            doNotDisturb = runCatching {
                context.getSystemService(NotificationManager::class.java).currentInterruptionFilter !=
                    NotificationManager.INTERRUPTION_FILTER_ALL
            }.getOrNull(),
            wifiSsid = wifiSsid(),
            latitude = location?.latitude,
            longitude = location?.longitude,
        )
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun charging(): Boolean? {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    @Suppress("DEPRECATION")
    fun wifiSsid(): String? {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) return null
        return runCatching {
            val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
            wifi.connectionInfo?.ssid
                ?.trim('"')
                ?.takeUnless { it.isBlank() || it == "<unknown ssid>" }
        }.getOrNull()
    }

    fun lastLocation(): Location? {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) return null
        return try {
            val manager = context.getSystemService(LocationManager::class.java)
            val newest = manager.getProviders(true)
                .mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
            newest?.takeIf { System.currentTimeMillis() - it.time < MAX_LOCATION_AGE_MS }
        } catch (_: SecurityException) {
            null
        }
    }

    private companion object {
        const val MAX_LOCATION_AGE_MS = 6 * 60 * 60_000L
    }
}
