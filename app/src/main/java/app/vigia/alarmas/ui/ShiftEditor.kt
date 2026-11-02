package app.vigia.alarmas.ui

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vigia.alarmas.data.CalendarInfo
import app.vigia.alarmas.domain.APP_LOCALE
import app.vigia.alarmas.domain.BurstConfig
import app.vigia.alarmas.domain.CalendarSnapshot
import app.vigia.alarmas.domain.ShiftPlan
import app.vigia.alarmas.domain.ShiftPlanner
import app.vigia.alarmas.domain.ShiftSlot
import app.vigia.alarmas.domain.ShiftType
import app.vigia.alarmas.domain.tr
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.UUID

private val SHORT: DateTimeFormatter get() = DateTimeFormatter.ofPattern(tr("EEE d MMM yyyy", "EEE, MMM d, yyyy"), APP_LOCALE)

fun defaultShiftPlan(): ShiftPlan {
    val today = LocalDate.now()
    val week = { id: String -> List(5) { id } + listOf("", "") }
    return ShiftPlan(
        types = listOf(
            ShiftType("manana", tr("Mañana", "Morning"), 6, 0),
            ShiftType("tarde", tr("Tarde", "Afternoon"), 13, 0),
            ShiftType("noche", tr("Noche", "Night"), 21, 0),
        ),
        cycle = week("manana") + week("tarde") + week("noche"),
        start = today.minusDays(today.dayOfWeek.value - 1L),
    )
}

fun pickTime(context: Context, hour: Int, minute: Int, onPicked: (Int, Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPicked(h, m) }, hour, minute, true).show()
}

fun burstSummary(burst: BurstConfig): String = tr(
    "Cada ${burst.everyMinutes} min hasta las ${hhmm(burst.endHour, burst.endMinute)}",
    "Every ${burst.everyMinutes} min until ${hhmm(burst.endHour, burst.endMinute)}",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BurstFields(burst: BurstConfig, onChange: (BurstConfig) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            pickTime(context, burst.endHour, burst.endMinute) { hour, minute ->
                onChange(burst.copy(endHour = hour, endMinute = minute))
            }
        },
    ) {
        Text(tr("Hasta las ${hhmm(burst.endHour, burst.endMinute)}", "Until ${hhmm(burst.endHour, burst.endMinute)}"))
    }
    FieldLabel(tr("Repetir cada", "Repeat every"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(2, 3, 5, 10, 15, 20, 30).forEach { minutes ->
            FilterChip(
                selected = burst.everyMinutes == minutes,
                onClick = { onChange(burst.copy(everyMinutes = minutes)) },
                label = { Text("$minutes min") },
            )
        }
    }
}

@Composable
private fun shiftColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    return listOf(scheme.primary, scheme.tertiary, scheme.secondary, Sky.Rose, Color(0xFF5FB58A), Sky.Plum)
}

@Composable
private fun colorOf(plan: ShiftPlan, id: String): Color {
    val index = plan.types.indexOfFirst { it.id == id }
    val colors = shiftColors()
    return if (index < 0) MaterialTheme.colorScheme.surfaceContainerHighest else colors[index % colors.size]
}

@Composable
private fun DayCell(
    label: String,
    fill: Color,
    outlined: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(fill, label = "shiftCell")
    val onFill = if (background.luminance() > 0.45f) Color(0xFF14183F) else Color.White
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .background(background, RoundedCornerShape(10.dp))
            .then(
                if (outlined) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = onFill, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftEditor(plan: ShiftPlan, calendars: List<CalendarInfo>, onChange: (ShiftPlan) -> Unit) {
    var brush by remember { mutableStateOf(plan.types.firstOrNull()?.id.orEmpty()) }
    var month by remember { mutableStateOf(YearMonth.now()) }

    FieldLabel(tr("Tus turnos", "Your shifts"))
    plan.types.forEachIndexed { index, type ->
        ShiftTypeCard(
            type = type,
            color = colorOf(plan, type.id),
            onChange = { updated ->
                onChange(plan.copy(types = plan.types.toMutableList().also { it[index] = updated }))
            },
            onDelete = {
                onChange(
                    plan.copy(
                        types = plan.types - type,
                        cycle = plan.cycle.map { if (it == type.id) "" else it },
                        overrides = plan.overrides.filterValues { it != type.id },
                    ),
                )
                if (brush == type.id) brush = ""
            },
        )
    }
    FilledTonalButton(
        onClick = {
            val id = UUID.randomUUID().toString().take(8)
            onChange(plan.copy(types = plan.types + ShiftType(id, tr("Turno", "Shift"), 8, 0)))
            brush = id
        },
    ) {
        Icon(Icons.Filled.Add, null)
        Spacer(Modifier.width(8.dp))
        Text(tr("Añadir turno", "Add shift"))
    }

    HorizontalDivider()
    FieldLabel(tr("Elige qué pintar y toca los días", "Choose what to paint, then tap the days"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(brush.isEmpty(), { brush = "" }, { Text(tr("Descanso", "Day off")) })
        plan.types.forEach { type ->
            FilterChip(brush == type.id, { brush = type.id }, { Text(type.name.ifBlank { "?" }) })
        }
    }

    SwitchRow(
        title = tr("Repetir un ciclo", "Repeat a cycle"),
        checked = plan.cycle.isNotEmpty(),
        onChange = { onChange(plan.copy(cycle = if (it) List(21) { "" } else emptyList())) },
        detail = tr("Para cuadrantes que vuelven a empezar.", "For rotas that start over."),
    )
    AnimatedVisibility(plan.cycle.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CycleEditor(plan, brush, onChange)
        }
    }

    HorizontalDivider()
    FieldLabel(tr("Días concretos", "Specific days"))
    Text(
        tr(
            "Pinta un mes entero o cambia un día suelto. Vuelve a tocarlo para deshacerlo.",
            "Paint a whole month or change a single day. Tap it again to undo.",
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    MonthEditor(plan, brush, month, { month = it }, onChange)

    HorizontalDivider()
    FieldLabel(tr("Turnos desde tu calendario", "Shifts from your calendar"))
    Text(
        tr(
            "Elige calendarios y Vigía usará los eventos cuyo título contenga el nombre de un turno.",
            "Choose calendars and Vigía will use events whose title contains a shift name.",
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    CalendarPicker(calendars, plan.calendarIds) { onChange(plan.copy(calendarIds = it)) }

    HorizontalDivider()
    SwitchRow(
        title = tr("Si un día no tiene turno, sonar a la hora normal", "If a day has no shift, ring at the usual time"),
        checked = plan.unassignedRings,
        onChange = { onChange(plan.copy(unassignedRings = it)) },
    )
}

@Composable
private fun ShiftTypeCard(
    type: ShiftType,
    color: Color,
    onChange: (ShiftType) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).background(color, CircleShape))
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = type.name,
                    onValueChange = { onChange(type.copy(name = it)) },
                    label = { Text(tr("Nombre", "Name")) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, tr("Quitar turno", "Remove shift")) }
            }
            OutlinedButton(
                onClick = {
                    pickTime(context, type.hour, type.minute) { hour, minute ->
                        onChange(type.copy(hour = hour, minute = minute))
                    }
                },
            ) { Text(tr("Alarma a las ${hhmm(type.hour, type.minute)}", "Alarm at ${hhmm(type.hour, type.minute)}")) }
            val burst = type.burst
            SwitchRow(
                title = tr("Repetir en un rango", "Repeat within a range"),
                checked = burst != null,
                onChange = { on ->
                    val end = (type.hour * 60 + type.minute + 30).coerceAtMost(23 * 60 + 59)
                    onChange(type.copy(burst = if (on) BurstConfig(end / 60, end % 60, 5) else null))
                },
            )
            if (burst != null) BurstFields(burst) { onChange(type.copy(burst = it)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CycleEditor(plan: ShiftPlan, brush: String, onChange: (ShiftPlan) -> Unit) {
    val context = LocalContext.current
    val size = plan.cycle.size
    fun resize(days: Int) {
        val cycle = List(days) { plan.cycle.getOrElse(it) { "" } }
        onChange(plan.copy(cycle = cycle))
    }

    FieldLabel(tr("Duración: $size días", "Length: $size days"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            6 to tr("6 días", "6 days"),
            7 to tr("1 semana", "1 week"),
            14 to tr("2 semanas", "2 weeks"),
            21 to tr("3 semanas", "3 weeks"),
            28 to tr("4 semanas", "4 weeks"),
        ).forEach { (days, label) ->
            FilterChip(size == days, { resize(days) }, { Text(label) })
        }
    }
    Slider(
        value = size.coerceIn(2, 84).toFloat(),
        onValueChange = { resize(it.toInt()) },
        valueRange = 2f..84f,
    )
    OutlinedButton(
        onClick = { pickDate(context, plan.start) { onChange(plan.copy(start = it)) } },
    ) { Text(tr("El día 1 es: ${plan.start.format(SHORT)}", "Day 1 is: ${plan.start.format(SHORT)}")) }

    plan.cycle.chunked(7).forEachIndexed { rowIndex, row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEachIndexed { columnIndex, id ->
                val index = rowIndex * 7 + columnIndex
                val type = plan.types.firstOrNull { it.id == id }
                DayCell(
                    label = type?.name?.take(1)?.uppercase() ?: (index + 1).toString(),
                    fill = colorOf(plan, id),
                    outlined = false,
                    modifier = Modifier.weight(1f),
                ) {
                    onChange(plan.copy(cycle = plan.cycle.toMutableList().also { it[index] = brush }))
                }
            }
            repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun MonthEditor(
    plan: ShiftPlan,
    brush: String,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    onChange: (ShiftPlan) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, tr("Mes anterior", "Previous month"))
        }
        AnimatedContent(month, label = "month", modifier = Modifier.weight(1f)) { shown ->
            Text(
                "${shown.month.getDisplayName(TextStyle.FULL_STANDALONE, APP_LOCALE)} ${shown.year}"
                    .replaceFirstChar { it.uppercase(APP_LOCALE) },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        IconButton(onClick = { onMonth(month.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, tr("Mes siguiente", "Next month"))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.values().forEach { day ->
            Text(
                dayShort(day),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }

    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val cells = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val noEvents = CalendarSnapshot.Available(emptyList())
    cells.chunked(7).forEach { row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEach { date ->
                if (date == null) {
                    Spacer(Modifier.weight(1f))
                } else {
                    val key = date.toString()
                    val slot = ShiftPlanner.slot(plan, date, noEvents)
                    val fill = when (slot) {
                        is ShiftSlot.Work -> colorOf(plan, slot.type.id)
                        ShiftSlot.Rest -> MaterialTheme.colorScheme.surfaceContainerHighest
                        ShiftSlot.Unassigned -> Color.Transparent
                    }
                    DayCell(
                        label = date.dayOfMonth.toString(),
                        fill = fill,
                        outlined = key in plan.overrides,
                        modifier = Modifier.weight(1f),
                    ) {
                        val overrides = if (plan.overrides[key] == brush) {
                            plan.overrides - key
                        } else {
                            plan.overrides + (key to brush)
                        }
                        onChange(plan.copy(overrides = overrides))
                    }
                }
            }
            repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
