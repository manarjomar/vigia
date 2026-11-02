package app.vigia.alarmas.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vigia.alarmas.Graph
import app.vigia.alarmas.alarm.Notifications
import app.vigia.alarmas.data.Settings
import app.vigia.alarmas.domain.tr
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val scope = rememberCoroutineScope()
    var opacity by remember { mutableIntStateOf(settings.widgetOpacity) }
    val palettes = ThemePalette.values().filter { it != ThemePalette.DYNAMIC || supportsDynamicColor() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(tr("Tema", "Theme"), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "system" to tr("Del móvil", "Phone default"),
                "light" to tr("Claro", "Light"),
                "dark" to tr("Oscuro", "Dark"),
            ).forEach { (code, label) ->
                FilterChip(
                    selected = AppearanceState.mode == code,
                    onClick = {
                        settings.themeMode = code
                        AppearanceState.mode = code
                    },
                    label = { Text(label) },
                )
            }
        }

        Text(tr("Colores", "Colours"), style = MaterialTheme.typography.titleLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            palettes.forEach { palette ->
                PaletteSwatch(palette, AppearanceState.palette == palette) {
                    settings.themePalette = palette.code
                    AppearanceState.palette = palette
                }
            }
        }
        if (supportsDynamicColor()) {
            Text(
                tr(
                    "Material You usa los colores de tu fondo de pantalla.",
                    "Material You uses the colours of your wallpaper.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(tr("Fondo de los widgets", "Widget background"), style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                100 to tr("Opaco", "Solid"),
                75 to "75 %",
                50 to "50 %",
                25 to "25 %",
                0 to tr("Transparente", "Clear"),
            ).forEach { (value, label) ->
                FilterChip(
                    selected = opacity == value,
                    onClick = {
                        opacity = value
                        settings.widgetOpacity = value
                        scope.launch { Graph.coordinator(context).refreshWidgets() }
                    },
                    label = { Text(label) },
                )
            }
        }

        Text(tr("Idioma", "Language"), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "" to tr("Del móvil", "Phone default"),
                "es" to "Español",
                "en" to "English",
            ).forEach { (code, label) ->
                FilterChip(
                    selected = settings.language == code,
                    onClick = {
                        settings.language = code
                        settings.applyLanguage()
                        Notifications.createChannels(context)
                        (context as? Activity)?.recreate()
                    },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun PaletteSwatch(palette: ThemePalette, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val outline by animateDpAsState(if (selected) 3.dp else 0.dp, label = "swatch")
    val hue = palette.hue
    val colors: List<Color> = when {
        hue != null -> tonalScheme(hue, dark = true).let { listOf(it.primaryContainer, it.primary, it.tertiary) }
        palette == ThemePalette.DYNAMIC && supportsDynamicColor() ->
            dynamicDarkColorScheme(context).let { listOf(it.primaryContainer, it.primary, it.tertiary) }
        else -> listOf(Sky.Night, Sky.Dusk, Sky.Dawn)
    }

    Column(
        modifier = Modifier.width(76.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .border(outline, MaterialTheme.colorScheme.primary, CircleShape)
                .padding(5.dp)
                .background(Brush.linearGradient(colors), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (palette == ThemePalette.DYNAMIC) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Color.White)
            }
            SelectedMark(selected)
        }
        Text(palette.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SelectedMark(selected: Boolean) {
    AnimatedVisibility(selected, enter = scaleIn(), exit = scaleOut()) {
        Icon(Icons.Filled.Check, null, tint = Color.White)
    }
}
