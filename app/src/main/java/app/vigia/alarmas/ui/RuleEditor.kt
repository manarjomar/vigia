package app.vigia.alarmas.ui

import app.vigia.alarmas.domain.tr
import app.vigia.alarmas.domain.APP_LOCALE
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.LooksTwo
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.vigia.alarmas.Graph
import app.vigia.alarmas.alarm.DeviceStateReader
import app.vigia.alarmas.data.CalendarInfo
import app.vigia.alarmas.domain.AlarmEngine
import app.vigia.alarmas.domain.AlarmRule
import app.vigia.alarmas.domain.AnnualCondition
import app.vigia.alarmas.domain.CalendarCondition
import app.vigia.alarmas.domain.Condition
import app.vigia.alarmas.domain.ContextCondition
import app.vigia.alarmas.domain.ContextKind
import app.vigia.alarmas.domain.DateRange
import app.vigia.alarmas.domain.DatesCondition
import app.vigia.alarmas.domain.HolidayCondition
import app.vigia.alarmas.domain.MatchMode
import app.vigia.alarmas.domain.MonthsCondition
import app.vigia.alarmas.domain.OfflineHolidays
import app.vigia.alarmas.domain.ParityCondition
import app.vigia.alarmas.domain.RuleAction
import app.vigia.alarmas.domain.ShiftCondition
import app.vigia.alarmas.domain.WeekCycleCondition
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SHORT_DATE: DateTimeFormatter get() = DateTimeFormatter.ofPattern(tr("EEE d MMM yyyy", "EEE, MMM d, yyyy"), APP_LOCALE)

fun ruleSummary(rules: List<AlarmRule>): String = when (rules.size) {
    0 -> tr("Suena todos los días elegidos", "Rings on all chosen days")
    1 -> tr("1 regla", "1 rule")
    else -> tr("${rules.size} reglas", "${rules.size} rules")
}

@Composable
fun RulesEditor(
    rules: List<AlarmRule>,
    calendars: List<CalendarInfo>,
    onChange: (List<AlarmRule>) -> Unit,
) {
    Text(
        tr("Las excepciones ganan a todo. Después se aplican las reglas de no sonar y las de sonar solo si.", "Exceptions beat everything. Don't-ring rules apply next, then ring-only-if rules."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    rules.forEachIndexed { index, rule ->
        RuleCard(
            rule = rule,
            calendars = calendars,
            onChange = { updated -> onChange(rules.toMutableList().also { it[index] = updated }) },
            onDelete = { onChange(rules.filterIndexed { i, _ -> i != index }) },
        )
    }
    FilledTonalButton(
        onClick = { onChange(rules + AlarmRule(id = UUID.randomUUID().toString())) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Add, null)
        Spacer(Modifier.width(8.dp))
        Text(tr("Añadir regla", "Add rule"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleCard(
    rule: AlarmRule,
    calendars: List<CalendarInfo>,
    onChange: (AlarmRule) -> Unit,
    onDelete: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val actions = listOf(
                RuleAction.SKIP to tr("No sonar", "Don't ring"),
                RuleAction.ONLY to tr("Solo sonar", "Ring only if"),
                RuleAction.ALWAYS to tr("Excepción", "Exception"),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                actions.forEachIndexed { index, (action, label) ->
                    SegmentedButton(
                        selected = rule.action == action,
                        onClick = { onChange(rule.copy(action = action)) },
                        shape = SegmentedButtonDefaults.itemShape(index, actions.size),
                    ) {
                        Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            AnimatedContent(rule.action, label = "action") { action ->
                Text(
                    when (action) {
                        RuleAction.SKIP -> tr("La alarma no suena cuando se cumple esto.", "The alarm won't ring when this is true.")
                        RuleAction.ONLY -> tr("La alarma solo suena cuando se cumple esto.", "The alarm only rings when this is true.")
                        RuleAction.ALWAYS -> tr("La alarma suena aunque otra regla diga lo contrario.", "The alarm rings even if another rule says otherwise.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (rule.conditions.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = rule.mode == MatchMode.ANY,
                        onClick = { onChange(rule.copy(mode = MatchMode.ANY)) },
                        label = { Text(tr("Basta con una", "Any of them")) },
                    )
                    FilterChip(
                        selected = rule.mode == MatchMode.ALL,
                        onClick = { onChange(rule.copy(mode = MatchMode.ALL)) },
                        label = { Text(tr("Todas a la vez", "All of them")) },
                    )
                }
            }

            rule.conditions.forEachIndexed { index, condition ->
                if (index > 0) HorizontalDivider()
                ConditionEditor(
                    condition = condition,
                    calendars = calendars,
                    onChange = { updated ->
                        onChange(rule.copy(conditions = rule.conditions.toMutableList().also { it[index] = updated }))
                    },
                    onDelete = {
                        onChange(rule.copy(conditions = rule.conditions.filterIndexed { i, _ -> i != index }))
                    },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { picking = true }) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text(tr("Añadir condición", "Add condition"))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, tr("Eliminar regla", "Delete rule")) }
            }
        }
    }

    if (picking) {
        ConditionSheet(
            onDismiss = { picking = false },
            onPick = {
                onChange(rule.copy(conditions = rule.conditions + it))
                picking = false
            },
        )
    }
}

private data class ConditionOption(
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val create: () -> Condition,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionSheet(onDismiss: () -> Unit, onPick: (Condition) -> Unit) {
    val today = LocalDate.now()
    val options = listOf(
        ConditionOption(tr("Calendario", "Calendar"), tr("Eventos de tus calendarios", "Events in your calendars"), Icons.Filled.Event) { CalendarCondition() },
        ConditionOption(tr("Festivos", "Holidays"), tr("De tu país y tu región", "From your country and region"), Icons.Filled.Celebration) {
            HolidayCondition(country = Locale.getDefault().country)
        },
        ConditionOption(tr("Fechas y periodos", "Dates and periods"), tr("Días sueltos o rangos", "Single days or ranges"), Icons.Filled.DateRange) { DatesCondition() },
        ConditionOption(tr("Fechas anuales", "Yearly dates"), tr("Se repiten cada año", "Repeat every year"), Icons.Filled.EventRepeat) { AnnualCondition() },
        ConditionOption(tr("Meses o temporada", "Months or season"), tr("Por ejemplo, julio y agosto", "For example, July and August"), Icons.Filled.WbSunny) { MonthsCondition() },
        ConditionOption(tr("Semanas alternas", "Alternate weeks"), tr("Una de cada varias semanas", "One in every few weeks"), Icons.Filled.Repeat) { WeekCycleCondition(today) },
        ConditionOption(tr("Días pares o impares", "Even or odd days"), tr("Según el día del mes", "Based on the day of the month"), Icons.Filled.LooksTwo) { ParityCondition() },
        ConditionOption(tr("Estado del móvil", "Phone status"), tr("Wifi, lugar, carga, avión o No molestar", "Wi-Fi, place, charging, airplane mode or Do Not Disturb"), Icons.Filled.PhoneAndroid) {
            ContextCondition(ContextKind.CHARGING)
        },
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 8.dp, end = 8.dp, bottom = 32.dp),
        ) {
            Text(
                tr("Elige una condición", "Choose a condition"),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
            )
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(option.create()) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(option.icon, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(option.title)
                        Text(
                            option.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConditionEditor(
    condition: Condition,
    calendars: List<CalendarInfo>,
    onChange: (Condition) -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                conditionTitle(condition),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, tr("Quitar condición", "Remove condition")) }
        }
        when (condition) {
            is CalendarCondition -> CalendarEditor(condition, calendars, onChange)
            is DatesCondition -> DatesEditor(condition, onChange)
            is AnnualCondition -> AnnualEditor(condition, onChange)
            is HolidayCondition -> HolidayEditor(condition, onChange)
            is WeekCycleCondition -> WeekCycleEditor(condition, onChange)
            is ShiftCondition -> ShiftEditor(condition, onChange)
            is ParityCondition -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(condition.even, { onChange(ParityCondition(true)) }, { Text(tr("Pares", "Even")) })
                FilterChip(!condition.even, { onChange(ParityCondition(false)) }, { Text(tr("Impares", "Odd")) })
            }
            is MonthsCondition -> MonthsEditor(condition, onChange)
            is ContextCondition -> ContextEditor(condition, onChange)
        }
    }
}

private fun conditionTitle(condition: Condition): String = when (condition) {
    is CalendarCondition -> tr("Calendario", "Calendar")
    is DatesCondition -> tr("Fechas y periodos", "Dates and periods")
    is AnnualCondition -> tr("Fechas anuales", "Yearly dates")
    is HolidayCondition -> tr("Festivos", "Holidays")
    is WeekCycleCondition -> tr("Semanas alternas", "Alternate weeks")
    is ShiftCondition -> tr("Turnos", "Shifts")
    is ParityCondition -> tr("Días pares o impares", "Even or odd days")
    is MonthsCondition -> tr("Meses o temporada", "Months or season")
    is ContextCondition -> tr("Estado del móvil", "Phone status")
}

@Composable
private fun CalendarEditor(
    condition: CalendarCondition,
    calendars: List<CalendarInfo>,
    onChange: (Condition) -> Unit,
) {
    CalendarPicker(calendars, condition.calendarIds) { onChange(condition.copy(calendarIds = it)) }
    SwitchRow(tr("Solo eventos de día completo", "All-day events only"), condition.allDayOnly, { onChange(condition.copy(allDayOnly = it)) })
    SwitchRow(tr("Solo si figuro como ocupado", "Only if I'm shown as busy"), condition.busyOnly, { onChange(condition.copy(busyOnly = it)) })
    KeywordsField(condition.keywords) { onChange(condition.copy(keywords = it)) }
}

@Composable
fun KeywordsField(keywords: List<String>, onChange: (List<String>) -> Unit) {
    var text by remember { mutableStateOf(keywords.joinToString(", ")) }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            text = value
            onChange(value.split(",").map { it.trim() }.filter { it.isNotEmpty() })
        },
        label = { Text(tr("El título contiene (opcional)", "Title contains (optional)")) },
        placeholder = { Text(tr("vacaciones, festivo", "vacation, holiday")) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DatesEditor(condition: DatesCondition, onChange: (Condition) -> Unit) {
    val context = LocalContext.current
    condition.ranges.forEach { range ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            val text = if (range.start == range.end) {
                range.start.format(SHORT_DATE)
            } else {
                tr("${range.start.format(SHORT_DATE)} al ${range.end.format(SHORT_DATE)}", "${range.start.format(SHORT_DATE)} to ${range.end.format(SHORT_DATE)}")
            }
            Text(text, Modifier.weight(1f))
            IconButton(onClick = { onChange(condition.copy(ranges = condition.ranges - range)) }) {
                Icon(Icons.Filled.Close, tr("Quitar fecha", "Remove date"))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                pickDate(context, LocalDate.now()) {
                    onChange(condition.copy(ranges = condition.ranges + DateRange(it, it)))
                }
            },
        ) { Text(tr("Añadir día", "Add day")) }
        OutlinedButton(
            onClick = {
                pickDate(context, LocalDate.now()) { start ->
                    pickDate(context, start) { end ->
                        if (!end.isBefore(start)) {
                            onChange(condition.copy(ranges = condition.ranges + DateRange(start, end)))
                        }
                    }
                }
            },
        ) { Text(tr("Añadir periodo", "Add period")) }
    }
}

@Composable
private fun AnnualEditor(condition: AnnualCondition, onChange: (Condition) -> Unit) {
    val context = LocalContext.current
    condition.dates.forEach { date ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            val parts = date.split("-")
            val month = parts.getOrNull(0)?.toIntOrNull() ?: 1
            val day = parts.getOrNull(1)?.toIntOrNull() ?: 1
            Text(
                tr("$day de ${Month.of(month).getDisplayName(TextStyle.FULL, APP_LOCALE)}", "${Month.of(month).getDisplayName(TextStyle.FULL, APP_LOCALE)} $day"),
                Modifier.weight(1f),
            )
            IconButton(onClick = { onChange(condition.copy(dates = condition.dates - date)) }) {
                Icon(Icons.Filled.Close, tr("Quitar fecha", "Remove date"))
            }
        }
    }
    OutlinedButton(
        onClick = {
            pickDate(context, LocalDate.now()) {
                val value = "%02d-%02d".format(it.monthValue, it.dayOfMonth)
                onChange(condition.copy(dates = (condition.dates + value).distinct()))
            }
        },
    ) { Text(tr("Añadir fecha anual", "Add yearly date")) }
}

@Composable
private fun WeekCycleEditor(condition: WeekCycleCondition, onChange: (Condition) -> Unit) {
    val context = LocalContext.current
    FieldLabel(tr("Una de cada ${condition.everyWeeks} semanas", "One in every ${condition.everyWeeks} weeks"))
    Slider(
        value = condition.everyWeeks.toFloat(),
        onValueChange = { onChange(condition.copy(everyWeeks = it.toInt())) },
        valueRange = 2f..6f,
        steps = 3,
    )
    OutlinedButton(onClick = { pickDate(context, condition.anchor) { onChange(condition.copy(anchor = it)) } }) {
        Text(tr("Semana de referencia: ${condition.anchor.format(SHORT_DATE)}", "Reference week: ${condition.anchor.format(SHORT_DATE)}"))
    }
}

@Composable
private fun ShiftEditor(condition: ShiftCondition, onChange: (Condition) -> Unit) {
    val context = LocalContext.current
    OutlinedTextField(
        value = condition.pattern,
        onValueChange = { value ->
            onChange(condition.copy(pattern = value.uppercase().filter { it == 'T' || it == 'D' }))
        },
        label = { Text(tr("Patrón: T trabajo, D descanso", "Pattern: T work, D day off")) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedButton(onClick = { pickDate(context, condition.anchor) { onChange(condition.copy(anchor = it)) } }) {
        Text(tr("Empieza: ${condition.anchor.format(SHORT_DATE)}", "Starts: ${condition.anchor.format(SHORT_DATE)}"))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthsEditor(condition: MonthsCondition, onChange: (Condition) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Month.values().forEach { month ->
            val number = month.value
            val selected = number in condition.months
            FilterChip(
                selected = selected,
                onClick = {
                    val months = if (selected) condition.months - number else condition.months + number
                    onChange(condition.copy(months = months))
                },
                label = {
                    Text(month.getDisplayName(TextStyle.SHORT, APP_LOCALE).replaceFirstChar { it.uppercase(APP_LOCALE) })
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextEditor(condition: ContextCondition, onChange: (Condition) -> Unit) {
    val context = LocalContext.current
    val reader = remember { DeviceStateReader(context) }
    var message by remember { mutableStateOf<String?>(null) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        message = if (granted) tr("Permiso concedido. Vuelve a pulsar el botón.", "Permission granted. Tap the button again.") else tr("Sin ese permiso, la alarma sonará siempre.", "Without that permission, the alarm will always ring.")
    }
    val kinds = listOf(
        ContextKind.CHARGING to tr("Cargando", "Charging"),
        ContextKind.WIFI to "Wifi",
        ContextKind.LOCATION to tr("Lugar", "Place"),
        ContextKind.AIRPLANE to tr("Modo avión", "Airplane mode"),
        ContextKind.DO_NOT_DISTURB to tr("No molestar", "Do Not Disturb"),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        kinds.forEach { (kind, label) ->
            FilterChip(
                selected = condition.kind == kind,
                onClick = { onChange(condition.copy(kind = kind)) },
                label = { Text(label) },
            )
        }
    }
    when (condition.kind) {
        ContextKind.WIFI -> {
            OutlinedTextField(
                value = condition.ssid,
                onValueChange = { onChange(condition.copy(ssid = it)) },
                label = { Text(tr("Nombre de la wifi", "Wi-Fi name")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedButton(
                onClick = {
                    val ssid = reader.wifiSsid()
                    if (ssid != null) {
                        onChange(condition.copy(ssid = ssid))
                    } else {
                        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
            ) { Text(tr("Usar la wifi actual", "Use current Wi-Fi")) }
        }
        ContextKind.LOCATION -> {
            OutlinedTextField(
                value = condition.placeName,
                onValueChange = { onChange(condition.copy(placeName = it)) },
                label = { Text(tr("Nombre del lugar", "Place name")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            FieldLabel(tr("Radio: ${condition.radiusMeters} m", "Radius: ${condition.radiusMeters} m"))
            Slider(
                value = condition.radiusMeters.toFloat(),
                onValueChange = { onChange(condition.copy(radiusMeters = it.toInt())) },
                valueRange = 100f..2000f,
            )
            OutlinedButton(
                onClick = {
                    val location = reader.lastLocation()
                    if (location != null) {
                        onChange(condition.copy(latitude = location.latitude, longitude = location.longitude))
                        message = tr("Lugar guardado.", "Place saved.")
                    } else {
                        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        message = tr("Activa la ubicación y abre un mapa si no se guarda el lugar.", "Turn on location and open a map if the place isn't saved.")
                    }
                },
            ) { Text(tr("Usar mi ubicación actual", "Use my current location")) }
        }
        else -> Unit
    }
    if (condition.kind == ContextKind.WIFI || condition.kind == ContextKind.LOCATION) {
        Text(
            tr("Necesita la ubicación permitida todo el tiempo. Actívala en Más, Fiabilidad y ajustes.", "Needs location allowed all the time. Turn it on in More, Reliability and settings."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Text(
        tr("Se comprueba al llegar la hora: ${AlarmEngine.describe(condition)}.", "Checked when the time comes: ${AlarmEngine.describe(condition)}."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val APP_LOCALE_REGIONS = mapOf(
    "ES-AN" to "Andalucía", "ES-AR" to "Aragón", "ES-AS" to "Asturias", "ES-CB" to "Cantabria",
    "ES-CE" to "Ceuta", "ES-CL" to "Castilla y León", "ES-CM" to "Castilla-La Mancha",
    "ES-CN" to "Canarias", "ES-CT" to "Cataluña", "ES-EX" to "Extremadura", "ES-GA" to "Galicia",
    "ES-IB" to "Islas Baleares", "ES-MC" to "Murcia", "ES-MD" to "Madrid", "ES-ML" to "Melilla",
    "ES-NC" to "Navarra", "ES-PV" to "País Vasco", "ES-RI" to "La Rioja", "ES-VC" to "Comunidad Valenciana",
)

private fun regionLabel(code: String): String = APP_LOCALE_REGIONS[code] ?: code

private fun countryName(code: String): String = Locale("", code).getDisplayCountry(APP_LOCALE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HolidayEditor(condition: HolidayCondition, onChange: (Condition) -> Unit) {
    val context = LocalContext.current
    val repository = remember { Graph.holidays(context) }
    val country = condition.effectiveCountry
    val subdivision = condition.effectiveSubdivision
    var picking by remember { mutableStateOf(false) }
    var loading by remember(country) { mutableStateOf(true) }
    var available by remember(country) { mutableStateOf(true) }
    var regions by remember(country) { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(country) {
        loading = true
        available = country.isNotBlank() && repository.ensure(country)
        regions = withContext(Dispatchers.IO) { repository.regions(country) }
        loading = false
    }

    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (country.isBlank()) tr("Elegir país", "Choose country") else countryName(country))
    }

    if (loading) {
        Text(tr("Buscando festivos", "Looking up holidays"), style = MaterialTheme.typography.bodySmall)
    } else if (!available && !OfflineHolidays.covers(country, subdivision)) {
        Text(
            tr("No hay datos de festivos para este país. La alarma sonará. También puedes añadir una regla ", "No holiday data for this country. The alarm will ring. You can also add a rule ") +
                tr("con el calendario de festivos de tu cuenta.", "using your account's holiday calendar."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (regions.isNotEmpty()) {
        FieldLabel(tr("Región", "Region"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = subdivision.isBlank(),
                onClick = { onChange(HolidayCondition(country = country)) },
                label = { Text(tr("Solo nacionales", "National only")) },
            )
            regions.forEach { code ->
                FilterChip(
                    selected = subdivision == code,
                    onClick = { onChange(HolidayCondition(country = country, subdivision = code)) },
                    label = { Text(regionLabel(code)) },
                )
            }
        }
    }
    Text(
        tr("Añade los festivos de tu ciudad como fechas anuales.", "Add your city's holidays as yearly dates."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (picking) {
        CountryPicker(
            onDismiss = { picking = false },
            onPick = {
                onChange(HolidayCondition(country = it))
                picking = false
            },
        )
    }
}

@Composable
private fun CountryPicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val countries = remember {
        Locale.getISOCountries().map { it to countryName(it) }.sortedBy { it.second }
    }
    val filtered = countries.filter { it.second.contains(query.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Elegir país", "Choose country")) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(tr("Buscar", "Search")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.first }) { (code, name) ->
                        Text(name, Modifier.fillMaxWidth().clickable { onPick(code) }.padding(12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
}
