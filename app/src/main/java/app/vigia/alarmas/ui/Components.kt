package app.vigia.alarmas.ui

import app.vigia.alarmas.domain.APP_LOCALE
import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin


fun dayShort(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, APP_LOCALE).take(2)
    .replaceFirstChar { it.uppercase(APP_LOCALE) }

fun hhmm(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

fun pickDate(context: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}

/** Sección que se pliega y despliega con una animación de altura. */
@Composable
fun ExpandableSection(
    title: String,
    summary: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    AnimatedVisibility(visible = !expanded && !summary.isNullOrBlank()) {
                        Text(
                            summary.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.rotate(rotation))
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(280, easing = FastOutSlowInEasing)) + fadeIn(),
                exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, detail: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun skyStops(hour: Float): List<Color> {
    val night = listOf(Sky.Night, Sky.Dusk)
    val dawn = listOf(Sky.Dusk, Sky.Rose)
    val day = listOf(Sky.Day, Sky.DayHigh)
    val dusk = listOf(Sky.Plum, Sky.Rose)
    fun mix(a: List<Color>, b: List<Color>, t: Float) = a.indices.map { lerp(a[it], b[it], t.coerceIn(0f, 1f)) }
    return when {
        hour < 5f -> night
        hour < 7.5f -> mix(night, dawn, (hour - 5f) / 2.5f)
        hour < 10f -> mix(dawn, day, (hour - 7.5f) / 2.5f)
        hour < 17.5f -> day
        hour < 20f -> mix(day, dusk, (hour - 17.5f) / 2.5f)
        hour < 22f -> mix(dusk, night, (hour - 20f) / 2f)
        else -> night
    }
}

/** Cielo del momento del día, con el sol o la luna recorriendo el arco de las 24 horas. */
@Composable
fun SkyHorizon(
    now: LocalTime,
    alarmDayFraction: Float?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val hour = now.hour + now.minute / 60f
    val stops = skyStops(hour)
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(1_100, easing = FastOutSlowInEasing)) }

    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.verticalGradient(stops)),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val radius = width * 1.05f
            val center = Offset(width / 2f, height * 0.84f + radius)
            drawArc(
                color = Color.White.copy(alpha = 0.32f),
                startAngle = 232f,
                sweepAngle = 76f * reveal.value,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 2.dp.toPx()),
            )
            fun point(fraction: Float): Offset {
                val angle = (232f + 76f * fraction) * PI.toFloat() / 180f
                return Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))
            }
            val current = point((hour / 24f) * reveal.value)
            val isDay = hour in 7f..20f
            drawCircle(
                color = (if (isDay) Sky.Dawn else Color.White).copy(alpha = 0.25f),
                radius = 16.dp.toPx(),
                center = current,
            )
            drawCircle(
                color = if (isDay) Sky.Dawn else Color(0xFFEDEBFF),
                radius = 8.dp.toPx(),
                center = current,
            )
            alarmDayFraction?.let { fraction ->
                drawCircle(Color.White, radius = 4.dp.toPx(), center = point(fraction * reveal.value))
            }
        }
        Column(Modifier.padding(24.dp)) { content() }
    }
}

@Composable
fun DayDot(selected: Boolean, text: String, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "day",
    )
    val foreground by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "dayText",
    )
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(background, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = foreground, style = MaterialTheme.typography.labelLarge)
    }
}
