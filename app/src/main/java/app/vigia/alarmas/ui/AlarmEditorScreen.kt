package app.vigia.alarmas.ui

import app.vigia.alarmas.domain.tr
import app.vigia.alarmas.domain.APP_LOCALE
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.vigia.alarmas.Graph
import app.vigia.alarmas.data.AlarmEntity
import app.vigia.alarmas.data.AlarmTemplate
import app.vigia.alarmas.data.CalendarInfo
import app.vigia.alarmas.data.CalendarRepository
import app.vigia.alarmas.data.Settings
import app.vigia.alarmas.domain.AlarmConfig
import app.vigia.alarmas.domain.BurstConfig
import app.vigia.alarmas.domain.ChallengeKind
import app.vigia.alarmas.domain.Decision
import app.vigia.alarmas.domain.ForecastDay
import app.vigia.alarmas.domain.RelativeEvent
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FULL_DATE: DateTimeFormatter get() = DateTimeFormatter.ofPattern(tr("EEEE d 'de' MMMM", "EEEE, MMMM d"), APP_LOCALE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(initial: AlarmEntity, groups: List<String>, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = remember { Graph.coordinator(context) }
    val repository = remember { CalendarRepository(context) }
    val settings = remember { Settings(context) }

    var config by remember(initial) { mutableStateOf(initial.config) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var calendarsVersion by remember { mutableIntStateOf(0) }

    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { calendarsVersion++ }

    val calendars by produceState(emptyList<CalendarInfo>(), calendarsVersion) {
        value = withContext(Dispatchers.IO) { repository.calendars() }
    }

    BackHandler(onBack = onClose)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Volver", "Back"))
                }
                Text(
                    if (initial.id == 0L) tr("Nueva alarma", "New alarm") else tr("Editar alarma", "Edit alarm"),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        scope.launch {
                            coordinator.save(initial.copy(enabled = true, skipDate = null).withConfig(config))
                            onClose()
                        }
                    },
                ) {
                    Icon(Icons.Filled.Check, null)
                    Text(tr("Guardar", "Save"), Modifier.padding(start = 6.dp))
                }
            }

            Box(Modifier.fillMaxWidth().clickable { picking = true }, Alignment.Center) {
                AnimatedContent(
                    targetState = hhmm(config.hour, config.minute),
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn()) togetherWith
                            (slideOutVertically { -it / 2 } + fadeOut())
                    },
                    label = "time",
                ) { time ->
                    Text(
                        text = time,
                        style = TimeText.copy(fontSize = 84.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            AnimatedVisibility(config.shifts == null) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    DayOfWeek.values().forEach { day ->
                        DayDot(day in config.days, dayShort(day)) {
                            val days = if (day in config.days) config.days - day else config.days + day
                            config = config.copy(days = days)
                        }
                    }
                }
            }
            AnimatedVisibility(config.isOneShot) {
                Text(
                    tr("Sin días marcados, sonará una sola vez.", "With no days selected, it rings once."),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(config.shifts != null) {
                Text(
                    tr(
                        "Con turnos, esta hora solo se usa en los días sin turno asignado.",
                        "With shifts, this time is only used on days with no shift assigned.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = config.label,
                onValueChange = { config = config.copy(label = it) },
                label = { Text(tr("Nombre", "Name")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            ExpandableSection(
                title = tr("Turnos", "Shifts"),
                summary = config.shifts?.let { tr("${it.types.size} turnos", "${it.types.size} shifts") }
                    ?: tr("Desactivado", "Off"),
                icon = Icons.Filled.Work,
                initiallyExpanded = config.shifts != null,
            ) {
                SwitchRow(
                    title = tr("Usar un cuadrante de turnos", "Use a shift rota"),
                    checked = config.shifts != null,
                    onChange = { on ->
                        config = if (on) {
                            config.copy(shifts = defaultShiftPlan(), burst = null, relative = null)
                        } else {
                            config.copy(shifts = null)
                        }
                    },
                    detail = tr(
                        "Cada turno tiene su hora, y los ciclos pueden durar varias semanas.",
                        "Each shift has its own time, and cycles can last several weeks.",
                    ),
                )
                config.shifts?.let { plan ->
                    ShiftEditor(plan, calendars) { config = config.copy(shifts = it) }
                }
            }

            AnimatedVisibility(config.shifts == null) {
                ExpandableSection(
                    title = tr("Repetir en un rango", "Repeat within a range"),
                    summary = config.burst?.let { burstSummary(it) } ?: tr("Desactivado", "Off"),
                    icon = Icons.Filled.Repeat,
                ) {
                    SwitchRow(
                        title = tr("Modo perezosos extremos", "Extreme sleepyhead mode"),
                        checked = config.burst != null,
                        onChange = { on ->
                            val end = (config.hour * 60 + config.minute + 60).coerceAtMost(23 * 60 + 59)
                            config = config.copy(burst = if (on) BurstConfig(end / 60, end % 60, 5) else null)
                        },
                        detail = tr(
                            "Suena una y otra vez sin posponer. Detener la calla hasta la siguiente; Detener por hoy acaba con todas.",
                            "It rings again and again with no snooze. Stop silences it until the next one; Stop for today ends them all.",
                        ),
                    )
                    config.burst?.let { burst ->
                        BurstFields(burst) { config = config.copy(burst = it) }
                    }
                }
            }

            ExpandableSection(
                title = tr("Cuándo no debe sonar", "When it shouldn't ring"),
                summary = ruleSummary(config.rules),
                icon = Icons.Filled.FilterAlt,
                initiallyExpanded = config.rules.isNotEmpty(),
            ) {
                if (!repository.hasPermission()) {
                    OutlinedButton(onClick = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }) {
                        Text(tr("Permitir leer el calendario", "Allow calendar access"))
                    }
                }
                RulesEditor(config.rules, calendars) { config = config.copy(rules = it) }
            }

            AnimatedVisibility(config.shifts == null) {
                ExpandableSection(
                    title = tr("Hora según tus eventos", "Time based on your events"),
                    summary = config.relative?.let { tr("${it.minutesBefore} min antes del primer evento", "${it.minutesBefore} min before the first event") } ?: tr("Desactivado", "Off"),
                    icon = Icons.Filled.EventAvailable,
                ) {
                    RelativeSection(config, calendars) { config = it }
                }
            }

            ExpandableSection(
                title = tr("Sonido y volumen", "Sound and volume"),
                summary = soundSummary(config),
                icon = Icons.Filled.MusicNote,
            ) {
                SoundSection(config) { config = it }
            }

            ExpandableSection(
                title = tr("Posponer y respaldo", "Snooze and backup"),
                summary = "${config.snooze.minutes} min" +
                    if (config.backup.enabled) tr(", con alarma de respaldo", ", with backup alarm") else "",
                icon = Icons.Filled.Snooze,
            ) {
                SnoozeSection(config) { config = it }
            }

            ExpandableSection(
                title = tr("Reto para apagar", "Challenge to dismiss"),
                summary = challengeName(config.challenge.kind),
                icon = Icons.Filled.Extension,
            ) {
                ChallengeSection(config) { config = it }
            }

            ExpandableSection(
                title = tr("Grupo", "Group"),
                summary = config.group.ifBlank { tr("Sin grupo", "No group") },
                icon = Icons.Filled.Folder,
            ) {
                GroupSection(config, groups) { config = it }
            }

            ExpandableSection(
                title = tr("Próximos 30 días", "Next 30 days"),
                summary = null,
                icon = Icons.Filled.CalendarMonth,
                initiallyExpanded = true,
            ) {
                ForecastGrid(initial, config)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        settings.templates = settings.templates +
                            AlarmTemplate(config.label.ifBlank { hhmm(config.hour, config.minute) }, config)
                    },
                ) { Text(tr("Guardar como plantilla", "Save as template")) }
                if (initial.id != 0L) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                coordinator.save(AlarmEntity().withConfig(config))
                                onClose()
                            }
                        },
                    ) { Text(tr("Duplicar", "Duplicate")) }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(tr("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (picking) {
        val state = rememberTimePickerState(config.hour, config.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        config = config.copy(hour = state.hour, minute = state.minute)
                        picking = false
                    },
                ) { Text(tr("Aceptar", "OK")) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(tr("Cancelar", "Cancel")) } },
            text = { TimePicker(state = state) },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("¿Eliminar esta alarma?", "Delete this alarm?")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            coordinator.delete(initial)
                            onClose()
                        }
                    },
                ) { Text(tr("Eliminar", "Delete")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}

private fun soundSummary(config: AlarmConfig): String {
    val fade = config.sound.fadeSeconds
    return "${config.sound.name}, ${config.sound.volumePercent} %" +
        if (fade > 0) tr(", sube en $fade s", ", rises over $fade s") else ""
}

private fun challengeName(kind: ChallengeKind): String = when (kind) {
    ChallengeKind.NONE -> tr("Sin reto", "No challenge")
    ChallengeKind.MATH -> tr("Cálculo", "Maths")
    ChallengeKind.SHAKE -> tr("Sacudir", "Shake")
    ChallengeKind.STEPS -> tr("Caminar", "Walk")
    ChallengeKind.QR -> tr("Código QR", "QR code")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelativeSection(
    config: AlarmConfig,
    calendars: List<CalendarInfo>,
    onChange: (AlarmConfig) -> Unit,
) {
    val relative = config.relative
    SwitchRow(
        title = tr("Sonar antes del primer evento del día", "Ring before the day's first event"),
        checked = relative != null,
        onChange = { onChange(config.copy(relative = if (it) RelativeEvent() else null)) },
    )
    if (relative != null) {
        FieldLabel(tr("Antelación", "Lead time"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(30, 45, 60, 90, 120, 180).forEach { minutes ->
                FilterChip(
                    selected = relative.minutesBefore == minutes,
                    onClick = { onChange(config.copy(relative = relative.copy(minutesBefore = minutes))) },
                    label = { Text("$minutes min") },
                )
            }
        }
        CalendarPicker(calendars, relative.calendarIds) {
            onChange(config.copy(relative = relative.copy(calendarIds = it)))
        }
        KeywordsField(relative.keywords) {
            onChange(config.copy(relative = relative.copy(keywords = it)))
        }
        SwitchRow(
            title = tr("Sin eventos, sonar a la hora normal", "With no events, ring at the usual time"),
            checked = relative.useBaseTimeWhenEmpty,
            onChange = { onChange(config.copy(relative = relative.copy(useBaseTimeWhenEmpty = it))) },
        )
    }
}

private class SoundPreview {
    private var player: MediaPlayer? = null

    val playing: Boolean get() = player != null

    fun start(context: Context, uri: Uri?) {
        stop()
        val source = uri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: return
        val media = MediaPlayer()
        try {
            media.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            media.setDataSource(context, source)
            media.isLooping = true
            media.prepare()
            media.start()
            player = media
        } catch (_: Exception) {
            media.release()
        }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundSection(config: AlarmConfig, onChange: (AlarmConfig) -> Unit) {
    val context = LocalContext.current
    val sound = config.sound
    val preview = remember { SoundPreview() }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }

    val tonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                val name = runCatching { RingtoneManager.getRingtone(context, uri).getTitle(context) }
                    .getOrDefault(tr("Tono elegido", "Chosen tone"))
                onChange(config.copy(sound = sound.copy(uri = uri.toString(), name = name)))
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
            onChange(config.copy(sound = sound.copy(uri = uri.toString(), name = name ?: tr("Archivo de audio", "Audio file"))))
        }
    }

    Text(sound.name)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            onClick = {
                tonePicker.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, sound.uri?.let { Uri.parse(it) }),
                )
            },
        ) { Text(tr("Elegir tono", "Choose tone")) }
        OutlinedButton(onClick = { filePicker.launch(arrayOf("audio/*")) }) { Text(tr("Usar un archivo", "Use a file")) }
        OutlinedButton(
            onClick = {
                if (playing) {
                    preview.stop()
                } else {
                    preview.start(context, sound.uri?.let { Uri.parse(it) })
                }
                playing = preview.playing
            },
        ) { Text(if (playing) tr("Parar", "Stop") else tr("Escuchar", "Listen")) }
    }

    FieldLabel(tr("Volumen máximo: ${sound.volumePercent} %", "Maximum volume: ${sound.volumePercent}%"))
    Slider(
        value = sound.volumePercent.toFloat(),
        onValueChange = { onChange(config.copy(sound = sound.copy(volumePercent = it.toInt()))) },
        valueRange = 10f..100f,
    )

    FieldLabel(tr("Subida progresiva", "Gradual rise"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0 to tr("Sin subida", "No rise"), 15 to "15 s", 30 to "30 s", 60 to "1 min", 120 to "2 min", 300 to "5 min")
            .forEach { (seconds, label) ->
                FilterChip(
                    selected = sound.fadeSeconds == seconds,
                    onClick = { onChange(config.copy(sound = sound.copy(fadeSeconds = seconds))) },
                    label = { Text(label) },
                )
            }
    }
    SwitchRow(tr("Vibrar", "Vibrate"), sound.vibrate, { onChange(config.copy(sound = sound.copy(vibrate = it))) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SnoozeSection(config: AlarmConfig, onChange: (AlarmConfig) -> Unit) {
    val snooze = config.snooze
    val backup = config.backup
    FieldLabel(tr("Posponer ${snooze.minutes} minutos", "Snooze for ${snooze.minutes} minutes"))
    Slider(
        value = snooze.minutes.toFloat(),
        onValueChange = { onChange(config.copy(snooze = snooze.copy(minutes = it.toInt()))) },
        valueRange = 1f..30f,
    )
    FieldLabel(tr("Veces que se puede posponer", "Times it can be snoozed"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0 to tr("Sin límite", "No limit"), 1 to "1", 2 to "2", 3 to "3", 5 to "5").forEach { (count, label) ->
            FilterChip(
                selected = snooze.maxCount == count,
                onClick = { onChange(config.copy(snooze = snooze.copy(maxCount = count))) },
                label = { Text(label) },
            )
        }
    }
    FieldLabel(tr("Dejar de sonar tras ${config.ringLimitMinutes} minutos", "Stop ringing after ${config.ringLimitMinutes} minutes"))
    Slider(
        value = config.ringLimitMinutes.toFloat(),
        onValueChange = { onChange(config.copy(ringLimitMinutes = it.toInt())) },
        valueRange = 1f..30f,
    )
    SwitchRow(
        title = tr("Alarma de respaldo", "Backup alarm"),
        checked = backup.enabled,
        onChange = { onChange(config.copy(backup = backup.copy(enabled = it))) },
        detail = tr("Si nadie la apaga, vuelve a sonar al máximo.", "If nobody dismisses it, it rings again at full volume."),
    )
    AnimatedVisibility(backup.enabled) {
        Column {
            FieldLabel(tr("Volver a sonar a los ${backup.afterMinutes} minutos", "Ring again after ${backup.afterMinutes} minutes"))
            Slider(
                value = backup.afterMinutes.toFloat(),
                onValueChange = { onChange(config.copy(backup = backup.copy(afterMinutes = it.toInt()))) },
                valueRange = 1f..30f,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChallengeSection(config: AlarmConfig, onChange: (AlarmConfig) -> Unit) {
    val context = LocalContext.current
    val challenge = config.challenge
    val options = listOf(
        ChallengeKind.NONE,
        ChallengeKind.MATH,
        ChallengeKind.SHAKE,
        ChallengeKind.STEPS,
        ChallengeKind.QR,
    )
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { onChange(config.copy(challenge = challenge.copy(qrValue = it))) }
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { kind ->
            FilterChip(
                selected = challenge.kind == kind,
                onClick = {
                    onChange(config.copy(challenge = challenge.copy(kind = kind)))
                    val needed = when (kind) {
                        ChallengeKind.STEPS -> Manifest.permission.ACTIVITY_RECOGNITION
                        ChallengeKind.QR -> Manifest.permission.CAMERA
                        else -> null
                    }
                    if (needed != null &&
                        ContextCompat.checkSelfPermission(context, needed) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permission.launch(needed)
                    }
                },
                label = { Text(challengeName(kind)) },
            )
        }
    }

    when (challenge.kind) {
        ChallengeKind.NONE -> Unit
        ChallengeKind.MATH -> AmountSlider(tr("Operaciones: ${challenge.amount}", "Sums: ${challenge.amount}"), challenge.amount, 1f..5f) {
            onChange(config.copy(challenge = challenge.copy(amount = it)))
        }
        ChallengeKind.SHAKE -> {
            val shakes = challenge.amount.coerceAtLeast(10)
            AmountSlider(tr("Sacudidas: $shakes", "Shakes: $shakes"), shakes, 10f..60f) {
                onChange(config.copy(challenge = challenge.copy(amount = it)))
            }
        }
        ChallengeKind.STEPS -> {
            val steps = challenge.amount.coerceAtLeast(10)
            AmountSlider(tr("Pasos: $steps", "Steps: $steps"), steps, 10f..100f) {
                onChange(config.copy(challenge = challenge.copy(amount = it)))
            }
        }
        ChallengeKind.QR -> {
            Text(
                if (challenge.qrValue.isBlank()) {
                    tr("Escanea el código que usarás para apagarla. Sin código, se pedirá un cálculo.", "Scan the code you'll use to dismiss it. Without one, you'll get a maths challenge.")
                } else {
                    tr("Código guardado.", "Code saved.")
                },
                style = MaterialTheme.typography.bodySmall,
            )
            FilledTonalButton(
                onClick = {
                    scanner.launch(
                        ScanOptions().setBeepEnabled(false).setOrientationLocked(false).setPrompt(tr("Escanea tu código", "Scan your code")),
                    )
                },
            ) { Text(tr("Escanear código", "Scan code")) }
        }
    }
}

@Composable
private fun AmountSlider(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Int) -> Unit,
) {
    FieldLabel(label)
    Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = range)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupSection(config: AlarmConfig, groups: List<String>, onChange: (AlarmConfig) -> Unit) {
    OutlinedTextField(
        value = config.group,
        onValueChange = { onChange(config.copy(group = it)) },
        label = { Text(tr("Nombre del grupo", "Group name")) },
        placeholder = { Text(tr("Trabajo", "Work")) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    if (groups.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.forEach { group ->
                FilterChip(
                    selected = config.group == group,
                    onClick = { onChange(config.copy(group = group)) },
                    label = { Text(group) },
                )
            }
        }
    }
}

@Composable
private fun ForecastGrid(initial: AlarmEntity, config: AlarmConfig) {
    val context = LocalContext.current
    val coordinator = remember { Graph.coordinator(context) }
    val spec = remember(initial, config) { initial.copy(enabled = true).withConfig(config).toSpec() }
    val forecast by produceState(emptyList<ForecastDay>(), spec) {
        value = coordinator.forecast(spec, 30)
    }
    var selected by remember { mutableStateOf<LocalDate?>(null) }

    val today = LocalDate.now()
    val start = today.minusDays(today.dayOfWeek.value - 1L)
    val byDate = forecast.associateBy { it.date }

    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.values().forEach { day ->
            Text(
                dayShort(day),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    (0 until 42 step 7).forEach { rowStart ->
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { column ->
                val date = start.plusDays((rowStart + column).toLong())
                val inRange = !date.isBefore(today) && date.isBefore(today.plusDays(30))
                ForecastCell(
                    date = date,
                    day = byDate[date],
                    visible = inRange,
                    selected = selected == date,
                    modifier = Modifier.weight(1f),
                    onClick = { selected = date },
                )
            }
        }
    }

    AnimatedContent(selected, label = "forecast") { date ->
        val decision = date?.let { byDate[it]?.decision }
        val text = when {
            date == null -> tr("Toca un día para ver qué pasará.", "Tap a day to see what will happen.")
            decision is Decision.Ring ->
                tr("${date.format(FULL_DATE)}: suena a las ${hhmm(decision.time.hour, decision.time.minute)}", "${date.format(FULL_DATE)}: rings at ${hhmm(decision.time.hour, decision.time.minute)}") +
                    (decision.note?.let { ". $it" } ?: "") +
                    (decision.burst?.let { ". ${burstSummary(it)}" } ?: "")
            decision is Decision.Skip -> tr("${date.format(FULL_DATE)}: no suena. ${decision.reason}", "${date.format(FULL_DATE)}: won't ring. ${decision.reason}")
            else -> tr("${date.format(FULL_DATE)}: no está en los días elegidos.", "${date.format(FULL_DATE)}: not one of the chosen days.")
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ForecastCell(
    date: LocalDate,
    day: ForecastDay?,
    visible: Boolean,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val decision = day?.decision
    val target = when {
        !visible -> Color.Transparent
        decision is Decision.Ring -> colors.primaryContainer
        decision is Decision.Skip -> colors.tertiaryContainer
        else -> Color.Transparent
    }
    val background by animateColorAsState(target, label = "cell")
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .background(background, CircleShape)
            .then(if (visible) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    selected -> colors.primary
                    decision is Decision.Ring -> colors.onPrimaryContainer
                    decision is Decision.Skip -> colors.onTertiaryContainer
                    else -> colors.onSurfaceVariant
                },
            )
        }
    }
}
