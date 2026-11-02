package app.vigia.alarmas.ui

import app.vigia.alarmas.domain.tr
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vigia.alarmas.Graph
import app.vigia.alarmas.data.TimerEntity
import app.vigia.alarmas.domain.Durations
import app.vigia.alarmas.domain.TimerStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TimersScreen(modifier: Modifier) {
    val context = LocalContext.current
    val timers by remember { Graph.database(context).timers().observeAll() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<TimerEntity?>(null) }

    Box(modifier.fillMaxSize()) {
        if (timers.isEmpty()) {
            Text(
                tr("Crea un temporizador. Puedes tener varios a la vez y encadenar tramos para hacer intervalos.", "Create a timer. You can run several at once and chain steps into intervals."),
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(timers, key = { it.id }) { timer ->
                TimerCard(timer, Modifier.animateItem()) { editing = timer }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = TimerEntity() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text(tr("Nuevo temporizador", "New timer")) },
        )
    }

    editing?.let { timer ->
        TimerEditor(timer) { editing = null }
    }
}

@Composable
private fun TimerCard(timer: TimerEntity, modifier: Modifier, onEdit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { Graph.timers(context) }
    val steps = timer.steps
    val step = steps.getOrNull(timer.stepIndex) ?: TimerStep()
    val stepMs = step.seconds * 1000L
    var remaining by remember { mutableLongStateOf(timer.remainingMs ?: stepMs) }

    LaunchedEffect(timer) {
        val endAt = timer.endAt
        if (timer.running && endAt != null) {
            while (true) {
                remaining = (endAt - System.currentTimeMillis()).coerceAtLeast(0)
                delay(100)
            }
        } else {
            remaining = timer.remainingMs ?: stepMs
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fraction = if (stepMs > 0) remaining.toFloat() / stepMs else 0f

    Surface(
        onClick = onEdit,
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(64.dp)) {
                val stroke = 6.dp.toPx()
                drawCircle(track, style = Stroke(stroke))
                drawArc(
                    color = primary,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(timer.name.ifBlank { tr("Temporizador", "Timer") }, style = MaterialTheme.typography.titleMedium)
                Text(Durations.clock(remaining), style = TimeText.copy(fontSize = 34.sp))
                if (steps.size > 1 || timer.loops > 1) {
                    Text(
                        tr("Tramo ${timer.stepIndex + 1} de ${steps.size}", "Step ${timer.stepIndex + 1} of ${steps.size}") +
                            if (timer.loops > 1) tr(", ronda ${timer.loopIndex + 1} de ${timer.loops}", ", round ${timer.loopIndex + 1} of ${timer.loops}") else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { scope.launch { controller.reset(timer.id) } }) {
                Icon(Icons.Filled.Refresh, tr("Reiniciar", "Reset"))
            }
            FilledIconButton(
                onClick = {
                    scope.launch {
                        if (timer.running) controller.pause(timer.id) else controller.start(timer.id)
                    }
                },
            ) {
                Icon(
                    if (timer.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (timer.running) tr("Pausar", "Pause") else tr("Iniciar", "Start"),
                )
            }
        }
    }
}

private class StepDraft(name: String, minutes: Int, seconds: Int) {
    var name by mutableStateOf(name)
    var minutes by mutableStateOf(minutes.toString())
    var seconds by mutableStateOf(seconds.toString())

    fun toStep(): TimerStep {
        val total = (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
        return TimerStep(name.trim(), total.coerceAtLeast(1))
    }
}

@Composable
private fun TimerEditor(timer: TimerEntity, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { Graph.timers(context) }
    var name by remember { mutableStateOf(timer.name) }
    var loops by remember { mutableIntStateOf(timer.loops) }
    val drafts = remember {
        mutableStateListOf(*timer.steps.map { StepDraft(it.name, it.seconds / 60, it.seconds % 60) }.toTypedArray())
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (timer.id == 0L) tr("Nuevo temporizador", "New timer") else tr("Editar temporizador", "Edit timer")) },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr("Nombre", "Name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                drafts.forEachIndexed { index, draft ->
                    Column(Modifier.animateContentSize()) {
                        if (drafts.size > 1) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FieldLabel(tr("Tramo ${index + 1}", "Step ${index + 1}"))
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { drafts.removeAt(index) }) {
                                    Icon(Icons.Filled.Close, tr("Quitar tramo", "Remove step"))
                                }
                            }
                            OutlinedTextField(
                                value = draft.name,
                                onValueChange = { draft.name = it },
                                label = { Text(tr("Nombre del tramo", "Step name")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = draft.minutes,
                                onValueChange = { draft.minutes = it.filter(Char::isDigit).take(3) },
                                label = { Text(tr("Minutos", "Minutes")) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = draft.seconds,
                                onValueChange = { draft.seconds = it.filter(Char::isDigit).take(2) },
                                label = { Text(tr("Segundos", "Seconds")) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                TextButton(onClick = { drafts.add(StepDraft("", 1, 0)) }) { Text(tr("Añadir tramo", "Add step")) }
                FieldLabel(tr("Repetir $loops ${if (loops == 1) "vez" else "veces"}", "Repeat $loops ${if (loops == 1) "time" else "times"}"))
                Slider(
                    value = loops.toFloat(),
                    onValueChange = { loops = it.toInt() },
                    valueRange = 1f..20f,
                )
                if (timer.id != 0L) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                controller.delete(timer)
                                onClose()
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Delete, null)
                        Text(tr("Eliminar", "Delete"), Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val steps = drafts.map { it.toStep() }.ifEmpty { listOf(TimerStep()) }
                        controller.save(
                            timer.copy(
                                name = name.trim(),
                                stepsJson = TimerEntity.encodeSteps(steps),
                                loops = loops,
                                stepIndex = if (timer.running) timer.stepIndex.coerceAtMost(steps.lastIndex) else 0,
                                loopIndex = if (timer.running) timer.loopIndex else 0,
                                remainingMs = if (timer.running) timer.remainingMs else null,
                            ),
                        )
                        onClose()
                    }
                },
            ) { Text(tr("Guardar", "Save")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancelar", "Cancel")) } },
    )
}
