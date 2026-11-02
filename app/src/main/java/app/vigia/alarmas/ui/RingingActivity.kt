package app.vigia.alarmas.ui

import app.vigia.alarmas.domain.tr
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vigia.alarmas.alarm.AlarmService
import app.vigia.alarmas.alarm.RingState
import app.vigia.alarmas.domain.ChallengeConfig
import app.vigia.alarmas.domain.ChallengeKind
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Lector QR que se muestra sobre la pantalla de bloqueo. */
class LockedScanActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        super.onCreate(savedInstanceState)
    }
}

private const val NO_ALARM_GRACE_MS = 3_000L

class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            VigiaTheme {
                val state by AlarmService.ringing.collectAsStateWithLifecycle()
                var seen by remember { mutableStateOf(false) }
                LaunchedEffect(state) {
                    if (state != null) seen = true
                    if (state == null && seen) finish()
                    if (state == null && !seen) {
                        delay(NO_ALARM_GRACE_MS)
                        finish()
                    }
                }
                BackHandler { }
                RingingScreen(
                    state = state,
                    onSnooze = { send(AlarmService.ACTION_SNOOZE) },
                    onDismiss = { send(AlarmService.ACTION_DISMISS) },
                    onStopDay = { send(AlarmService.ACTION_STOP_DAY) },
                )
            }
        }
    }

    private fun send(action: String) {
        startService(Intent(this, AlarmService::class.java).setAction(action))
    }
}

@Composable
private fun RingingScreen(
    state: RingState?,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    onStopDay: () -> Unit,
) {
    val sunrise = remember { Animatable(0.25f) }
    LaunchedEffect(state?.fadeSeconds) {
        val seconds = state?.fadeSeconds?.takeIf { it > 0 } ?: 12
        sunrise.animateTo(1f, tween(seconds * 1_000, easing = LinearEasing))
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val rise = sunrise.value
            drawRect(
                Brush.verticalGradient(
                    0f to Sky.Night,
                    0.55f to lerp(Sky.Night, Sky.Dusk, rise),
                    1f to lerp(Sky.Dusk, Sky.Rose, rise),
                ),
            )
            val center = Offset(size.width / 2f, size.height * (1.2f - 0.3f * rise))
            val radius = size.width * (0.6f + 0.6f * rise)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Sky.Dawn,
                    0.45f to Sky.Dawn.copy(alpha = 0.55f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(48.dp))
            Text(
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                style = TimeText.copy(fontSize = 96.sp),
                color = Color.White,
            )
            Text(
                state?.label ?: tr("Alarma", "Alarm"),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = Color.White,
            )
            Spacer(Modifier.weight(1f))

            if (state?.burst == true) {
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(76.dp)) {
                    Text(tr("Detener", "Stop"), style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    tr("Volverá a sonar en el siguiente intervalo.", "It will ring again at the next interval."),
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                ChallengePanel(state.challenge, onStopDay, tr("Detener por hoy", "Stop for today"), outlined = true)
            } else {
                ChallengePanel(state?.challenge ?: ChallengeConfig(), onDismiss)
            }

            state?.snoozeMinutes?.let { minutes ->
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Text(tr("Posponer $minutes minutos", "Snooze for $minutes minutes"), color = Color.White)
                }
            }
        }
    }
}

private fun available(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun stepsAvailable(context: Context): Boolean =
    available(context, Manifest.permission.ACTIVITY_RECOGNITION) &&
        context.getSystemService(SensorManager::class.java)
            ?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null

@Composable
private fun ChallengePanel(
    challenge: ChallengeConfig,
    onDone: () -> Unit,
    doneLabel: String = tr("Apagar", "Dismiss"),
    outlined: Boolean = false,
) {
    val context = LocalContext.current
    val kind = when {
        challenge.kind == ChallengeKind.QR &&
            (challenge.qrValue.isBlank() || !available(context, Manifest.permission.CAMERA)) -> ChallengeKind.MATH
        challenge.kind == ChallengeKind.STEPS && !stepsAvailable(context) -> ChallengeKind.SHAKE
        else -> challenge.kind
    }
    when (kind) {
        ChallengeKind.NONE -> if (outlined) {
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Text(doneLabel, color = Color.White)
            }
        } else {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(76.dp)) {
                Text(doneLabel, style = MaterialTheme.typography.titleLarge)
            }
        }
        ChallengeKind.MATH -> MathChallenge(challenge.amount.coerceIn(1, 5), onDone)
        ChallengeKind.SHAKE -> ShakeChallenge(challenge.amount.coerceAtLeast(10), onDone)
        ChallengeKind.STEPS -> StepsChallenge(challenge.amount.coerceAtLeast(10), onDone)
        ChallengeKind.QR -> QrChallenge(challenge.qrValue, onDone)
    }
}

@Composable
private fun MathChallenge(total: Int, onDone: () -> Unit) {
    var solved by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(Random.nextInt(12, 60)) }
    var right by remember { mutableIntStateOf(Random.nextInt(11, 40)) }
    var answer by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tr("Cálculo ${solved + 1} de $total", "Sum ${solved + 1} of $total"), color = Color.White)
        Text("$left + $right", style = TimeText.copy(fontSize = 48.sp), color = Color.White)
        OutlinedTextField(
            value = answer,
            onValueChange = {
                answer = it.filter(Char::isDigit)
                wrong = false
            },
            label = { Text(if (wrong) tr("Prueba otra vez", "Try again") else tr("Resultado", "Answer")) },
            isError = wrong,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (answer.toIntOrNull() == left + right) {
                    if (solved + 1 >= total) {
                        onDone()
                    } else {
                        solved++
                        left = Random.nextInt(12, 60)
                        right = Random.nextInt(11, 40)
                        answer = ""
                    }
                } else {
                    wrong = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
        ) { Text(tr("Comprobar", "Check")) }
    }
}

@Composable
private fun CounterProgress(label: String, count: Int, total: Int) {
    Column {
        Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { count.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = Sky.Dawn,
        )
    }
}

@Composable
private fun ShakeChallenge(total: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val sensors = context.getSystemService(SensorManager::class.java)
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                val force = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                val now = System.currentTimeMillis()
                if (force > 2.2f && now - last > 250) {
                    last = now
                    count++
                    if (count >= total) onDone()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensors.registerListener(
            listener,
            sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_GAME,
        )
        onDispose { sensors.unregisterListener(listener) }
    }
    CounterProgress(tr("Sacude el móvil: $count de $total", "Shake your phone: $count of $total"), count, total)
}

@Composable
private fun StepsChallenge(total: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                count++
                if (count >= total) onDone()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensors.registerListener(
            listener,
            sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR),
            SensorManager.SENSOR_DELAY_UI,
        )
        onDispose { sensors.unregisterListener(listener) }
    }
    CounterProgress(tr("Camina con el móvil: $count de $total pasos", "Walk with your phone: $count of $total steps"), count, total)
}

@Composable
private fun QrChallenge(expected: String, onDone: () -> Unit) {
    var failed by remember { mutableStateOf(false) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents == expected) onDone() else failed = result.contents != null
    }
    Column {
        if (failed) Text(tr("Ese no es tu código.", "That's not your code."), color = Color.White)
        Button(
            onClick = {
                scanner.launch(
                    ScanOptions()
                        .setCaptureActivity(LockedScanActivity::class.java)
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                        .setPrompt(tr("Escanea tu código para apagar la alarma", "Scan your code to dismiss the alarm")),
                )
            },
            modifier = Modifier.fillMaxWidth().height(76.dp),
        ) { Text(tr("Escanear código", "Scan code"), style = MaterialTheme.typography.titleLarge) }
    }
}
