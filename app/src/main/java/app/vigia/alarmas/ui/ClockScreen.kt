package app.vigia.alarmas.ui

import app.vigia.alarmas.domain.tr
import app.vigia.alarmas.domain.APP_LOCALE
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.vigia.alarmas.data.Settings
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val SECONDS: DateTimeFormatter get() = DateTimeFormatter.ofPattern("HH:mm:ss")
private val MINUTES: DateTimeFormatter get() = DateTimeFormatter.ofPattern("HH:mm")
private val DATE: DateTimeFormatter get() = DateTimeFormatter.ofPattern(tr("EEEE d 'de' MMMM", "EEEE, MMMM d"), APP_LOCALE)

@Composable
private fun rememberClock(periodMs: Long = 1_000): ZonedDateTime {
    val now by produceState(ZonedDateTime.now()) {
        while (true) {
            value = ZonedDateTime.now()
            delay(periodMs)
        }
    }
    return now
}

private fun cityName(zone: String): String = zone.substringAfterLast('/').replace('_', ' ')

@Composable
fun ClockScreen(modifier: Modifier) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val now = rememberClock()
    var zones by remember { mutableStateOf(settings.worldClocks) }
    var adding by remember { mutableStateOf(false) }
    var night by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(now.format(SECONDS), style = TimeText.copy(fontSize = 68.sp))
                Text(
                    now.format(DATE).replaceFirstChar { it.uppercase(APP_LOCALE) },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { night = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.DarkMode, null)
                    Text(tr("Nocturno", "Night"), Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(AndroidSettings.ACTION_DREAM_SETTINGS))
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Wallpaper, null)
                    Text(tr("Salvapantallas", "Screensaver"), Modifier.padding(start = 8.dp), maxLines = 1)
                }
            }
        }
        item {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Otras ciudades", "Other cities"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, tr("Añadir ciudad", "Add city")) }
            }
        }
        if (zones.isEmpty()) {
            item {
                Text(
                    tr("Añade una ciudad para ver su hora aquí.", "Add a city to see its time here."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(zones, key = { it }) { zone ->
            WorldClockRow(zone, now, Modifier.animateItem()) {
                zones = zones - zone
                settings.worldClocks = zones
            }
        }
    }

    if (adding) {
        ZonePicker(
            onDismiss = { adding = false },
            onPick = {
                zones = (zones + it).distinct()
                settings.worldClocks = zones
                adding = false
            },
        )
    }

    if (night) {
        Dialog(
            onDismissRequest = { night = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            NightClock(onExit = { night = false })
        }
    }
}

@Composable
private fun WorldClockRow(zone: String, now: ZonedDateTime, modifier: Modifier, onDelete: () -> Unit) {
    val local = now.withZoneSameInstant(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault()))
    val hours = (local.offset.totalSeconds - now.offset.totalSeconds) / 3600.0
    val difference = when {
        hours == 0.0 -> tr("Misma hora", "Same time")
        hours % 1.0 == 0.0 -> "%+d h".format(hours.toInt())
        else -> "%+.1f h".format(hours)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(cityName(zone), style = MaterialTheme.typography.titleMedium)
                Text(
                    difference,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(local.format(MINUTES), style = TimeText.copy(fontSize = 30.sp))
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, tr("Quitar ciudad", "Remove city")) }
        }
    }
}

@Composable
private fun ZonePicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember {
        ZoneId.getAvailableZoneIds().filter { it.contains('/') && !it.startsWith("Etc") }.sorted()
    }
    val filtered = all.filter { cityName(it).contains(query.trim(), ignoreCase = true) }.take(40)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Añadir ciudad", "Add city")) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(tr("Busca en inglés: London, Tokyo", "Search: London, Tokyo")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it }) { zone ->
                        Text(
                            cityName(zone),
                            modifier = Modifier.fillMaxWidth().clickable { onPick(zone) }.padding(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
}

@Composable
private fun NightClock(onExit: () -> Unit) {
    val now = rememberClock(10_000)
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(onBack = onExit)

    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onExit),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = now.format(MINUTES),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "night",
            ) { time ->
                Text(time, style = TimeText.copy(fontSize = 112.sp), color = Color(0xFF9C4B3A))
            }
            Text(tr("Toca para salir", "Tap to exit"), color = Color(0xFF5E2E25))
        }
    }
}
