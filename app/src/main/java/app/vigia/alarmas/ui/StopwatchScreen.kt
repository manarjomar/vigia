package app.vigia.alarmas.ui

import app.vigia.alarmas.domain.tr
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vigia.alarmas.clock.Stopwatch
import app.vigia.alarmas.domain.Durations
import kotlinx.coroutines.delay

@Composable
fun StopwatchScreen(modifier: Modifier) {
    val context = LocalContext.current
    val stopwatch = remember { Stopwatch.get(context) }
    val state by stopwatch.state.collectAsStateWithLifecycle()
    var elapsed by remember { mutableLongStateOf(state.elapsed()) }

    LaunchedEffect(state) {
        elapsed = state.elapsed()
        while (state.running) {
            elapsed = state.elapsed()
            delay(33)
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tertiary = MaterialTheme.colorScheme.tertiary

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), Alignment.Center) {
                Canvas(Modifier.size(280.dp)) {
                    val stroke = 8.dp.toPx()
                    drawCircle(track, style = Stroke(stroke))
                    drawArc(
                        color = primary,
                        startAngle = -90f,
                        sweepAngle = (elapsed % 60_000) / 60_000f * 360f,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Text(Durations.clock(elapsed, withHundredths = true), style = TimeText.copy(fontSize = 42.sp))
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { if (state.running) stopwatch.lap() else stopwatch.reset() },
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        if (state.running) Icons.Filled.Flag else Icons.Filled.Refresh,
                        if (state.running) tr("Vuelta", "Lap") else tr("Reiniciar", "Reset"),
                    )
                }
                FilledIconButton(
                    onClick = { if (state.running) stopwatch.pause() else stopwatch.start() },
                    modifier = Modifier.size(84.dp),
                ) {
                    Icon(
                        if (state.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (state.running) tr("Pausar", "Pause") else tr("Iniciar", "Start"),
                        Modifier.size(36.dp),
                    )
                }
                FilledTonalIconButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, tr("Vueltas del cronómetro", "Stopwatch laps"))
                            .putExtra(Intent.EXTRA_TEXT, stopwatch.exportCsv())
                        context.startActivity(Intent.createChooser(send, tr("Exportar vueltas", "Export laps")))
                    },
                    enabled = state.laps.isNotEmpty(),
                    modifier = Modifier.size(64.dp),
                ) { Icon(Icons.Filled.Share, tr("Exportar vueltas", "Export laps")) }
            }
        }

        val splits = state.laps.mapIndexed { index, total ->
            total - (state.laps.getOrNull(index - 1) ?: 0L)
        }
        val best = splits.minOrNull()
        val worst = splits.maxOrNull()
        itemsIndexed(splits.reversed(), key = { index, _ -> splits.size - index }) { index, split ->
            val number = splits.size - index
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp).animateItem(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("Vuelta $number", "Lap $number"), Modifier.weight(1f))
                AnimatedVisibility(splits.size > 1 && (split == best || split == worst)) {
                    Text(
                        if (split == best) tr("Mejor", "Best") else tr("Peor", "Worst"),
                        color = if (split == best) primary else tertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                Text(Durations.clock(split, withHundredths = true), style = TimeText.copy(fontSize = 18.sp))
            }
        }
    }
}
