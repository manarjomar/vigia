package app.vigia.alarmas.ui

import app.vigia.alarmas.domain.tr
import app.vigia.alarmas.domain.APP_LOCALE
import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.vigia.alarmas.Graph
import app.vigia.alarmas.alarm.AlarmScheduler
import app.vigia.alarmas.data.CalendarRepository
import app.vigia.alarmas.data.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val NEXT: DateTimeFormatter get() = DateTimeFormatter.ofPattern(tr("EEE d MMM, HH:mm", "EEE, MMM d, HH:mm"), APP_LOCALE)

@Composable
fun DiagnosticsScreen(modifier: Modifier) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val scheduler = remember { AlarmScheduler(context) }
    val calendars = remember { CalendarRepository(context) }
    val coordinator = remember { Graph.coordinator(context) }

    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }

    val power = context.getSystemService(PowerManager::class.java)
    val notifications = context.getSystemService(NotificationManager::class.java)
    var testMessage by remember { mutableStateOf<String?>(null) }
    var noticeEnabled by remember { mutableStateOf(settings.noticeEnabled) }
    var noticeTime by remember {
        mutableStateOf("%02d:%02d".format(settings.noticeHour, settings.noticeMinute))
    }

    key(refresh) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val next = scheduler.nextSystemAlarm()?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(NEXT)
        }
        Text(tr("Próxima alarma registrada en Android: ${next ?: "ninguna"}", "Next alarm registered with Android: ${next ?: "none"}"))

        Button(
            onClick = {
                val at = System.currentTimeMillis() + 60_000
                scheduler.schedule(AlarmScheduler.TEST_ALARM_ID, at, LocalDate.now(), snoozed = false)
                testMessage = tr("Bloquea el móvil. Sonará en 1 minuto.", "Lock your phone. It will ring in 1 minute.")
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(tr("Probar alarma en 1 minuto", "Test alarm in 1 minute"))
        }
        testMessage?.let { Text(it) }

        Check(
            title = tr("Notificaciones", "Notifications"),
            ok = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            action = tr("Activar", "Turn on"),
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                open(context, appDetails(context))
            }
        }
        Check(tr("Alarmas exactas", "Exact alarms"), scheduler.canScheduleExact(), tr("Abrir", "Open")) {
            open(context, appDetails(context))
        }
        Check(
            title = tr("Pantalla completa", "Full screen"),
            ok = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                notifications.canUseFullScreenIntent(),
            action = tr("Activar", "Turn on"),
        ) {
            open(
                context,
                Intent(
                    AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
        Check(tr("Mostrar sobre otras apps", "Display over other apps"), AndroidSettings.canDrawOverlays(context), tr("Permitir", "Allow")) {
            open(
                context,
                Intent(
                    AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
        Check(tr("Calendario", "Calendar"), calendars.hasPermission(), tr("Permitir", "Allow")) {
            permissions.launch(Manifest.permission.READ_CALENDAR)
        }
        Check(
            title = tr("Batería sin restricciones", "Unrestricted battery"),
            ok = power.isIgnoringBatteryOptimizations(context.packageName),
            action = tr("Permitir", "Allow"),
        ) {
            open(
                context,
                Intent(
                    AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }

        Check(
            title = tr("Ubicación todo el tiempo (solo reglas de wifi y lugar)", "Location all the time (Wi-Fi and place rules only)"),
            ok = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
            action = tr("Abrir", "Open"),
        ) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissions.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                open(context, appDetails(context))
            }
        }

        if (isXiaomiDevice()) {
            Text(tr("Ajustes de HyperOS", "HyperOS settings"), style = MaterialTheme.typography.titleLarge)
                Text(
            tr("Android no permite comprobar estos ajustes. Actívalos y márcalos como hechos.", "Android can't check these settings. Turn them on and tick them as done."),
            style = MaterialTheme.typography.bodySmall,
        )
        ManualCheck(settings, "autostart", tr("Inicio automático", "Autostart"), tr("Abrir", "Open")) {
            open(
                context,
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity",
                    ),
                ),
            )
        }
        ManualCheck(
            settings,
            "lockscreen",
            tr("Mostrar en pantalla de bloqueo y ventanas emergentes en segundo plano", "Show on lock screen and background pop-ups"),
            tr("Abrir", "Open"),
        ) {
            open(
                context,
                Intent("miui.intent.action.APP_PERM_EDITOR")
                    .setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity",
                    )
                    .putExtra("extra_pkgname", context.packageName),
            )
        }
        ManualCheck(settings, "battery", tr("Ahorro de batería de la app: Sin restricciones", "App battery saver: No restrictions"), tr("Abrir", "Open")) {
            open(context, appDetails(context))
        }
        ManualCheck(settings, "locked", tr("App bloqueada en recientes (candado)", "App locked in recents (padlock)"), null) { }
        } else {
            Text(tr("Ajustes de tu móvil", "Your phone's settings"), style = MaterialTheme.typography.titleLarge)
            Text(
                tr("Algunas marcas cierran las apps en segundo plano. Quita las restricciones de batería ", "Some brands close apps in the background. Remove battery restrictions ") +
                    tr("de Vigía y haz una prueba con el móvil bloqueado.", "for Vigía and run a test with your phone locked."),
                style = MaterialTheme.typography.bodySmall,
            )
            ManualCheck(settings, "battery", tr("Vigía sin restricciones de batería", "Vigía has no battery restrictions"), tr("Abrir", "Open")) {
                open(context, appDetails(context))
            }
        }

        Text(tr("Aviso de la noche anterior", "Night-before notice"), style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = noticeEnabled,
                onCheckedChange = {
                    noticeEnabled = it
                    settings.noticeEnabled = it
                    coordinator.refreshNoticeSchedule()
                },
            )
            Text(tr("Avisar de las alarmas que no sonarán", "Tell me about alarms that won't ring"), Modifier.padding(start = 12.dp))
        }
        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        settings.noticeHour = hour
                        settings.noticeMinute = minute
                        noticeTime = "%02d:%02d".format(hour, minute)
                        coordinator.refreshNoticeSchedule()
                    },
                    settings.noticeHour,
                    settings.noticeMinute,
                    true,
                ).show()
            },
        ) {
            Text(tr("Hora del aviso: $noticeTime", "Notice time: $noticeTime"))
        }
    }
    }
}

@Composable
private fun Check(title: String, ok: Boolean, action: String, onAction: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title)
                Text(
                    text = if (ok) tr("Correcto", "OK") else tr("Pendiente", "To do"),
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!ok) OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun ManualCheck(
    settings: Settings,
    key: String,
    title: String,
    action: String?,
    onAction: () -> Unit,
) {
    var checked by remember { mutableStateOf(settings.isChecked(key)) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    settings.setChecked(key, it)
                },
            )
            Text(title, Modifier.weight(1f))
            if (action != null) OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

private fun isXiaomiDevice(): Boolean =
    Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

private fun appDetails(context: Context): Intent = Intent(
    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.parse("package:${context.packageName}"),
)

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        context.startActivity(appDetails(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
