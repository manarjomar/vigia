package app.vigia.alarmas.ui

import java.time.LocalDateTime
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import android.app.TimePickerDialog
import app.vigia.alarmas.domain.tr
import app.vigia.alarmas.domain.APP_LOCALE
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vigia.alarmas.Graph
import app.vigia.alarmas.data.AlarmEntity
import app.vigia.alarmas.data.AlarmTemplate
import app.vigia.alarmas.data.Settings
import app.vigia.alarmas.domain.AlarmConfig
import app.vigia.alarmas.domain.AlarmRule
import app.vigia.alarmas.domain.BurstConfig
import app.vigia.alarmas.domain.Durations
import app.vigia.alarmas.domain.HolidayCondition
import app.vigia.alarmas.domain.Plan
import app.vigia.alarmas.domain.RuleAction
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DATE_TIME: DateTimeFormatter get() = DateTimeFormatter.ofPattern(tr("EEE d MMM, HH:mm", "EEE, MMM d, HH:mm"), APP_LOCALE)
private val WEEKDAY: DateTimeFormatter get() = DateTimeFormatter.ofPattern(tr("EEEE d 'de' MMMM", "EEEE, MMMM d"), APP_LOCALE)

private val WEEKDAYS = DayOfWeek.values().take(5).toSet()

private fun builtInTemplates(): List<AlarmTemplate> = listOf(
    AlarmTemplate(tr("Días laborables", "Weekdays"), AlarmConfig(label = tr("Trabajo", "Work"), days = WEEKDAYS)),
    AlarmTemplate(
        tr("Laborables sin festivos", "Weekdays without holidays"),
        AlarmConfig(
            label = tr("Trabajo", "Work"),
            days = WEEKDAYS,
            rules = listOf(
                AlarmRule(id = "festivos", action = RuleAction.SKIP, conditions = listOf(HolidayCondition(country = Locale.getDefault().country))),
            ),
        ),
    ),
    AlarmTemplate(
        tr("Fin de semana", "Weekend"),
        AlarmConfig(label = tr("Fin de semana", "Weekend"), hour = 9, minute = 30, days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
    ),
    AlarmTemplate(
        tr("Turnos rotativos", "Rotating shifts"),
        AlarmConfig(label = tr("Turno", "Shift"), shifts = defaultShiftPlan()),
    ),
    AlarmTemplate(
        tr("Perezosos extremos", "Extreme sleepyheads"),
        AlarmConfig(
            label = tr("Perezosos", "Sleepyheads"),
            days = WEEKDAYS,
            burst = BurstConfig(8, 0, 5),
        ),
    ),
    AlarmTemplate(tr("Una sola vez", "Just once"), AlarmConfig()),
)

@Composable
fun rememberNow(periodMs: Long = 30_000): LocalTime {
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(periodMs)
        }
    }
    return now
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(modifier: Modifier, onEdit: (AlarmEntity) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = remember { Graph.coordinator(context) }
    val settings = remember { Settings(context) }
    val alarms by remember { Graph.database(context).alarms().observeAll() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var showTemplates by remember { mutableStateOf(false) }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }

    val sorted = alarms.sortedBy { it.config.hour * 60 + it.config.minute }
    val grouped = sorted.groupBy { it.config.group.trim() }.toSortedMap()
    val showHeaders = grouped.keys.any { it.isNotBlank() }

    val next by produceState<ZonedDateTime?>(null, alarms) {
        value = alarms.filter { it.enabled }
            .mapNotNull { coordinator.plan(it).fireAt }
            .minOrNull()
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "sky") { NextAlarmHero(next) }

            if (alarms.isEmpty()) {
                item(key = "empty") {
                    Text(
                        tr("Crea tu primera alarma para que Vigía sepa cuándo despertarte.", "Create your first alarm so Vigía knows when to wake you."),
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            grouped.forEach { (group, items) ->
                if (showHeaders) {
                    item(key = "group-$group") {
                        GroupHeader(
                            name = group.ifBlank { tr("Sin grupo", "No group") },
                            count = items.size,
                            expanded = collapsed[group] != true,
                            enabled = items.any { it.enabled },
                            onToggleExpanded = { collapsed[group] = collapsed[group] != true },
                            onToggleEnabled = { value ->
                                scope.launch { coordinator.setGroupEnabled(group, value) }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (collapsed[group] != true) {
                    items(items, key = { it.id }) { alarm ->
                        AlarmCard(alarm, onClick = { onEdit(alarm) }, modifier = Modifier.animateItem())
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showTemplates = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text(tr("Nueva alarma", "New alarm")) },
        )
    }

    if (showTemplates) {
        var saved by remember { mutableStateOf(settings.templates) }
        ModalBottomSheet(onDismissRequest = { showTemplates = false }) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 8.dp, end = 8.dp, bottom = 32.dp),
            ) {
                Text(
                    tr("Empieza desde", "Start from"),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
                builtInTemplates().forEach { template ->
                    TemplateRow(template.name, null) {
                        showTemplates = false
                        onEdit(AlarmEntity().withConfig(template.config))
                    }
                }
                saved.forEach { template ->
                    TemplateRow(
                        template.name,
                        onDelete = {
                            saved = saved - template
                            settings.templates = saved
                        },
                    ) {
                        showTemplates = false
                        onEdit(AlarmEntity().withConfig(template.config))
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(name: String, onDelete: (() -> Unit)?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Alarm, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(name, Modifier.weight(1f))
        if (onDelete != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, tr("Eliminar plantilla", "Delete template")) }
        }
    }
}

@Composable
private fun NextAlarmHero(next: ZonedDateTime?) {
    val now = rememberNow()
    val fraction = next?.let { (it.hour + it.minute / 60f) / 24f }
    val light = now.hour in 9..17
    val foreground = if (light) Color(0xFF14183F) else Color.White

    SkyHorizon(now, fraction, Modifier.height(232.dp)) {
        Text(
            LocalDate.now().format(WEEKDAY).replaceFirstChar { it.uppercase(APP_LOCALE) },
            color = foreground,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(20.dp))
        if (next == null) {
            Text(tr("Sin alarmas", "No alarms"), color = foreground, style = TimeText.copy(fontSize = 44.sp))
            Text(tr("No sonará nada por ahora.", "Nothing will ring for now."), color = foreground)
        } else {
            Text(hhmm(next.hour, next.minute), color = foreground, style = TimeText.copy(fontSize = 64.sp))
            val left = Durations.human(Duration.between(ZonedDateTime.now(), next))
            Text(
                tr("Sonará ${next.format(DATE_TIME)}, dentro de $left", "Rings ${next.format(DATE_TIME)}, in $left"),
                color = foreground,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun GroupHeader(
    name: String,
    count: Int,
    expanded: Boolean,
    enabled: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "group")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded)
            .padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.rotate(rotation))
        Spacer(Modifier.width(8.dp))
        Text("$name ($count)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = onToggleEnabled)
    }
}

@Composable
private fun AlarmCard(alarm: AlarmEntity, onClick: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = remember { Graph.coordinator(context) }
    val config = remember(alarm) { alarm.config }
    val plan by produceState<Plan?>(initialValue = null, alarm) {
        value = coordinator.plan(alarm)
    }
    val contentAlpha by animateFloatAsState(if (alarm.enabled) 1f else 0.5f, label = "enabled")
    var changing by remember { mutableStateOf(false) }
    if (changing) OneTimeFlow(alarm) { changing = false }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.large,
        color = if (alarm.enabled) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).alpha(contentAlpha)) {
                    Text(hhmm(config.hour, config.minute), style = TimeText.copy(fontSize = 44.sp))
                    Text(
                        listOf(config.label, daysText(config)).filter { it.isNotBlank() }.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = { checked ->
                        scope.launch { coordinator.save(alarm.copy(enabled = checked)) }
                    },
                )
            }

            AnimatedVisibility(alarm.enabled) {
                Column(Modifier.padding(top = 4.dp)) {
                    val current = plan
                    val fireAt = current?.fireAt
                    Text(
                        text = when {
                            current == null -> tr("Calculando", "Calculating")
                            fireAt == null -> tr("Sin próxima alarma", "No next alarm")
                            else -> tr("Próxima: ${fireAt.format(DATE_TIME)}", "Next: ${fireAt.format(DATE_TIME)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    config.burst?.let {
                        Text(
                            burstSummary(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    current?.skipped?.firstOrNull()?.let {
                        Text(
                            skippedText(current.skipped.size, it.reason),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    current?.note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    val oneTime = alarm.oneTimeMoment?.takeIf { alarm.hasActiveOneTime }
                    if (oneTime != null) {
                        Text(
                            tr(
                                "Solo esta vez: ${oneTime.format(DATE_TIME)}",
                                "Just this once: ${oneTime.format(DATE_TIME)}",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (oneTime != null) {
                            TextButton(
                                onClick = { scope.launch { coordinator.clearOneTime(alarm) } },
                                contentPadding = PaddingValues(0.dp),
                            ) { Text(tr("Deshacer el cambio", "Undo change")) }
                        } else {
                            TextButton(
                                onClick = { changing = true },
                                contentPadding = PaddingValues(0.dp),
                            ) { Text(tr("Cambiar solo la próxima", "Change next only")) }
                        }
                        if (fireAt != null || alarm.hasActiveSkip) {
                            TextButton(
                                onClick = { scope.launch { coordinator.toggleSkipNext(alarm) } },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(if (alarm.hasActiveSkip) tr("Recuperar la saltada", "Restore skipped alarm") else tr("Saltar la próxima", "Skip next"))
                            }
                        }
                    }
                }
            }
        }
    }
}

private val DAY_TEXT: DateTimeFormatter get() = DateTimeFormatter.ofPattern(tr("EEEE d 'de' MMMM", "EEEE, MMMM d"), APP_LOCALE)

/** Elegir la hora de la próxima vez y, si una regla dice que no suena, pedir confirmación. */
@Composable
private fun OneTimeFlow(alarm: AlarmEntity, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = remember { Graph.coordinator(context) }
    var conflict by remember { mutableStateOf<Pair<LocalDateTime, String>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(alarm.id) {
        val target = coordinator.oneTimeTarget(alarm)
        if (target == null) {
            message = tr("Esta alarma no tiene una próxima vez que cambiar.", "This alarm has no next time to change.")
            return@LaunchedEffect
        }
        val start = target.current ?: target.usual
        val dayText = target.date.format(DAY_TEXT)
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val moment = target.date.atTime(hour, minute)
                scope.launch {
                    if (!moment.isAfter(LocalDateTime.now())) {
                        message = tr("Esa hora ya ha pasado.", "That time has already passed.")
                        return@launch
                    }
                    val reason = coordinator.oneTimeConflict(alarm, target.date)
                    if (reason == null) {
                        coordinator.setOneTime(alarm, moment)
                        onDone()
                    } else {
                        conflict = moment to reason
                    }
                }
            },
            start.hour,
            start.minute,
            true,
        ).apply {
            setTitle(tr("Solo el $dayText", "Only on $dayText"))
            setOnCancelListener { if (conflict == null && message == null) onDone() }
        }.show()
    }

    conflict?.let { (moment, reason) ->
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text(tr("Ese día no iba a sonar", "It wasn't going to ring that day")) },
            text = {
                Text(
                    tr(
                        "Motivo: $reason. ¿Quieres que suene el ${moment.format(DAY_TEXT)} a las ${hhmm(moment.hour, moment.minute)} igualmente?",
                        "Reason: $reason. Do you want it to ring on ${moment.format(DAY_TEXT)} at ${hhmm(moment.hour, moment.minute)} anyway?",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            coordinator.setOneTime(alarm, moment)
                            onDone()
                        }
                    },
                ) { Text(tr("Sonar igualmente", "Ring anyway")) }
            },
            dismissButton = { TextButton(onClick = onDone) { Text(tr("Cancelar", "Cancel")) } },
        )
    }

    message?.let {
        AlertDialog(
            onDismissRequest = onDone,
            text = { Text(it) },
            confirmButton = { TextButton(onClick = onDone) { Text(tr("Aceptar", "OK")) } },
        )
    }
}

private fun skippedText(count: Int, reason: String): String = if (count == 1) {
    tr("Antes se salta 1 día. $reason", "Skips 1 day first. $reason")
} else {
    tr("Antes se saltan $count días. Primero: $reason", "Skips $count days first. First: $reason")
}

private fun daysText(config: AlarmConfig): String = when {
    config.shifts != null -> tr("Turnos", "Shifts")
    config.isOneShot -> tr("Una vez", "Once")
    config.days.size == 7 -> tr("Todos los días", "Every day")
    config.days == WEEKDAYS -> tr("De lunes a viernes", "Monday to Friday")
    else -> DayOfWeek.values().filter { it in config.days }.joinToString(" ") { dayShort(it) }
}
