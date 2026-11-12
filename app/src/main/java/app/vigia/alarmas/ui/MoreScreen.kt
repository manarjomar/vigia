package app.vigia.alarmas.ui

import app.vigia.alarmas.domain.tr
import app.vigia.alarmas.domain.APP_LOCALE
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vigia.alarmas.Graph
import app.vigia.alarmas.data.BackupManager
import app.vigia.alarmas.data.HistoryType
import app.vigia.alarmas.domain.Bedtime
import app.vigia.alarmas.domain.HistoryPoint
import app.vigia.alarmas.domain.SleepAnalytics
import app.vigia.alarmas.domain.SleepPlanner
import app.vigia.alarmas.domain.SleepStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val STAMP: DateTimeFormatter get() = DateTimeFormatter.ofPattern(tr("d MMM, HH:mm", "MMM d, HH:mm"), APP_LOCALE)
private val HOUR: DateTimeFormatter get() = DateTimeFormatter.ofPattern("HH:mm")

private enum class MorePage(
    private val titleText: () -> String,
    private val detailText: () -> String,
    val icon: ImageVector,
) {
    APPEARANCE({ tr("Apariencia e idioma", "Appearance and language") }, { tr("Colores, tema e idioma", "Colours, theme and language") }, Icons.Filled.Palette),
    SLEEP({ tr("Sueño", "Sleep") }, { tr("Hora de acostarte y estadísticas", "Bedtime and statistics") }, Icons.Filled.Bedtime),
    HISTORY({ tr("Historial", "History") }, { tr("Cuándo sonó o se saltó cada alarma", "When each alarm rang or was skipped") }, Icons.Filled.History),
    RELIABILITY({ tr("Fiabilidad y ajustes", "Reliability and settings") }, { tr("Permisos, batería y aviso de la noche anterior", "Permissions, battery and night-before notice") }, Icons.Filled.VerifiedUser),
    BACKUP({ tr("Copia de seguridad", "Backup") }, { tr("Exportar e importar en un archivo", "Export and import to a file") }, Icons.Filled.Backup),
    ABOUT({ tr("Acerca de Vigía", "About Vigía") }, { tr("Qué hace, versión y autoría", "What it does, version and author") }, Icons.Filled.Info),;

    val title: String get() = titleText()
    val detail: String get() = detailText()
}

@Composable
fun MoreScreen(modifier: Modifier) {
    var pageName by rememberSaveable { mutableStateOf<String?>(null) }
    val page = pageName?.let { MorePage.valueOf(it) }
    BackHandler(enabled = page != null) { pageName = null }

    AnimatedContent(
        targetState = page,
        transitionSpec = { (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut() },
        label = "more",
        modifier = modifier,
    ) { current ->
        if (current == null) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Más", "More"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(8.dp))
                MorePage.values().forEach { item ->
                    Surface(
                        onClick = { pageName = item.name },
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    item.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { pageName = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Volver", "Back"))
                    }
                    Text(current.title, style = MaterialTheme.typography.titleLarge)
                }
                when (current) {
                    MorePage.APPEARANCE -> AppearanceScreen()
                    MorePage.SLEEP -> SleepPage()
                    MorePage.HISTORY -> HistoryPage()
                    MorePage.RELIABILITY -> DiagnosticsScreen(Modifier.fillMaxSize())
                    MorePage.BACKUP -> BackupPage()
                    MorePage.ABOUT -> AboutScreen()
                }
            }
        }
    }
}

private fun historyLabel(type: String): String = when (type) {
    HistoryType.RANG -> tr("Sonó", "Rang")
    HistoryType.DISMISSED -> tr("Apagada", "Dismissed")
    HistoryType.SNOOZED -> tr("Pospuesta", "Snoozed")
    HistoryType.SKIPPED -> tr("Saltada", "Skipped")
    HistoryType.UNANSWERED -> tr("Sin respuesta", "Unanswered")
    HistoryType.BACKUP -> tr("Respaldo", "Backup")
    HistoryType.FORCED -> tr("Sonará igualmente", "Will ring anyway")
    HistoryType.STOPPED -> tr("Detenida por hoy", "Stopped for today")
    HistoryType.ONE_TIME -> tr("Cambio puntual", "One-off change")
    HistoryType.WARNING -> tr("Aviso", "Warning")
    else -> type
}

@Composable
private fun HistoryPage() {
    val context = LocalContext.current
    val entries by remember { Graph.database(context).history().observeRecent() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    if (entries.isEmpty()) {
        Text(
            tr("Aquí verás cuándo sonó o se saltó cada alarma, y por qué.", "You'll see here when each alarm rang or was skipped, and why."),
            Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            Column {
                Text("${historyLabel(entry.type)}: ${entry.label}", style = MaterialTheme.typography.titleSmall)
                val time = Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).format(STAMP)
                Text(
                    if (entry.detail.isBlank()) time else "$time. ${entry.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SleepPage() {
    val context = LocalContext.current
    val next by produceState<ZonedDateTime?>(null) {
        value = Graph.coordinator(context).nextFire()
    }
    val stats by produceState<SleepStats?>(null) {
        val zone = ZoneId.systemDefault()
        val since = LocalDate.now().minusDays(14).atStartOfDay(zone).toInstant().toEpochMilli()
        val points = Graph.database(context).history().since(since).map { entry ->
            val moment = Instant.ofEpochMilli(entry.timestamp).atZone(zone)
            HistoryPoint(moment.toLocalDate(), moment.toLocalTime(), entry.type)
        }
        value = SleepAnalytics.build(points, LocalDate.now())
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val wake = next
        if (wake == null) {
            Text(tr("Activa una alarma para calcular tu hora de acostarte.", "Turn on an alarm to work out your bedtime."))
        } else {
            Text(tr("Para despertar a las ${wake.format(HOUR)}", "To wake up at ${wake.format(HOUR)}"), style = MaterialTheme.typography.titleMedium)
            SleepPlanner.bedtimes(wake).forEach { BedtimeRow(it) }
            Text(
                tr("Cuenta ${SleepPlanner.FALL_ASLEEP_MINUTES} minutos para dormirte y ciclos de ", "Allows ${SleepPlanner.FALL_ASLEEP_MINUTES} minutes to fall asleep and cycles of ") +
                    tr("${SleepPlanner.CYCLE_MINUTES} minutos. Es una orientación general.", "${SleepPlanner.CYCLE_MINUTES} minutes. This is general guidance."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(tr("Últimos 14 días", "Last 14 days"), style = MaterialTheme.typography.titleMedium)
        stats?.let { StatsPanel(it) }
    }
}

@Composable
private fun BedtimeRow(bedtime: Bedtime) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(bedtime.time.format(HOUR), style = TimeText.copy(fontSize = 34.sp), modifier = Modifier.weight(1f))
            val hours = bedtime.sleepMinutes / 60.0
            Text(tr("${bedtime.cycles} ciclos", "${bedtime.cycles} cycles") + ", ${"%.1f".format(APP_LOCALE, hours)} h")
        }
    }
}

@Composable
private fun StatsPanel(stats: SleepStats) {
    if (stats.records.isEmpty()) {
        Text(
            tr("Cuando apagues alarmas durante unos días, verás aquí tus horas de despertar.", "After you dismiss alarms for a few days, your wake-up times will appear here."),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val primary = MaterialTheme.colorScheme.primary
    val minimum = stats.records.minOf { it.time.toSecondOfDay() } - 1800
    val maximum = stats.records.maxOf { it.time.toSecondOfDay() } + 300
    val span = (maximum - minimum).coerceAtLeast(1).toFloat()

    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column {
            Text(stats.averageWake?.format(HOUR).orEmpty(), style = TimeText.copy(fontSize = 34.sp))
            Text(tr("Despertar medio", "Average wake-up"), style = MaterialTheme.typography.bodySmall)
        }
        Column {
            Text("%.1f".format(APP_LOCALE, stats.averageSnoozes), style = TimeText.copy(fontSize = 34.sp))
            Text(tr("Pospuestas al día", "Snoozes per day"), style = MaterialTheme.typography.bodySmall)
        }
        Column {
            Text(stats.skipped.toString(), style = TimeText.copy(fontSize = 34.sp))
            Text(tr("Saltadas", "Skipped"), style = MaterialTheme.typography.bodySmall)
        }
    }
    FieldLabel(tr("Hora de apagar la alarma. Cada barra es un día.", "Time the alarm was dismissed. Each bar is a day."))
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val count = stats.records.size
        val gap = 6.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtMost(28.dp.toPx())
        stats.records.forEachIndexed { index, record ->
            val height = (record.time.toSecondOfDay() - minimum) / span * size.height
            drawRoundRect(
                color = primary,
                topLeft = Offset(index * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
        }
    }
}

@Composable
private fun BackupPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backup = remember { BackupManager(context) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Uri?>(null) }

    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                message = runCatching { backup.export(uri) }
                    .fold({ tr("Copia guardada.", "Backup saved.") }, { tr("No se pudo guardar: ${it.message}", "Couldn't save: ${it.message}") })
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pending = uri
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("Guarda alarmas, temporizadores, ciudades, plantillas y aviso en un archivo.", "Save alarms, timers, cities, templates and notice settings to a file."))
        Button(onClick = { export.launch("vigia-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Exportar copia", "Export backup"))
        }
        OutlinedButton(
            onClick = { import.launch(arrayOf("application/json", "*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(tr("Importar copia", "Import backup"))
        }
        Text(
            tr("Los calendarios elegidos pueden no coincidir en otro móvil. Revísalos después de importar.", "Chosen calendars may differ on another phone. Check them after importing."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let { Text(it) }
    }

    pending?.let { uri ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(tr("¿Sustituir tus alarmas?", "Replace your alarms?")) },
            text = { Text(tr("Las alarmas y los temporizadores actuales se reemplazarán por los de la copia.", "Your current alarms and timers will be replaced by those in the backup.")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        scope.launch {
                            message = runCatching { backup.import(uri) }
                                .fold({ tr("Importadas $it alarmas.", "Imported $it alarms.") }, { tr("No se pudo importar: ${it.message}", "Couldn't import: ${it.message}") })
                        }
                    },
                ) { Text(tr("Sustituir", "Replace")) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}
