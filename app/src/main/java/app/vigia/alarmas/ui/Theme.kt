package app.vigia.alarmas.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.vigia.alarmas.data.Settings
import app.vigia.alarmas.domain.tr

object Sky {
    val Night = Color(0xFF121638)
    val Dusk = Color(0xFF3A3478)
    val Plum = Color(0xFF7A4A84)
    val Rose = Color(0xFFE5806F)
    val Dawn = Color(0xFFFFC97A)
    val Day = Color(0xFF7FB2F0)
    val DayHigh = Color(0xFFBFDCFA)
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBAC5FF),
    onPrimary = Color(0xFF17205F),
    primaryContainer = Color(0xFF323C8E),
    onPrimaryContainer = Color(0xFFDEE1FF),
    secondary = Color(0xFFC8C2EA),
    onSecondary = Color(0xFF2E2A52),
    secondaryContainer = Color(0xFF3E3A66),
    onSecondaryContainer = Color(0xFFE5DFFF),
    tertiary = Sky.Dawn,
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF654513),
    onTertiaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF0F1230),
    onBackground = Color(0xFFE5E5F7),
    surface = Color(0xFF0F1230),
    onSurface = Color(0xFFE5E5F7),
    surfaceVariant = Color(0xFF2B2E55),
    onSurfaceVariant = Color(0xFFC5C5DE),
    surfaceContainerLowest = Color(0xFF0B0E28),
    surfaceContainerLow = Color(0xFF161A3D),
    surfaceContainer = Color(0xFF1B1F45),
    surfaceContainerHigh = Color(0xFF242852),
    surfaceContainerHighest = Color(0xFF2E325F),
    outline = Color(0xFF8E90AB),
    outlineVariant = Color(0xFF45476A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D4AA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE1FF),
    onPrimaryContainer = Color(0xFF0B1565),
    secondary = Color(0xFF5B5580),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5DFFF),
    onSecondaryContainer = Color(0xFF181339),
    tertiary = Color(0xFF8A5A08),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB0),
    onTertiaryContainer = Color(0xFF2C1A00),
    background = Color(0xFFF5F5FF),
    onBackground = Color(0xFF1A1B2E),
    surface = Color(0xFFF5F5FF),
    onSurface = Color(0xFF1A1B2E),
    surfaceVariant = Color(0xFFE1E1F2),
    onSurfaceVariant = Color(0xFF45465C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFEFFC),
    surfaceContainer = Color(0xFFE9E9F8),
    surfaceContainerHigh = Color(0xFFE3E3F4),
    surfaceContainerHighest = Color(0xFFDDDDF0),
    outline = Color(0xFF75768C),
    outlineVariant = Color(0xFFC5C5DA),
)

val TimeText = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Light,
    fontFeatureSettings = "tnum",
)

private val AppTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Light, fontFeatureSettings = "tnum"),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Light, fontFeatureSettings = "tnum"),
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Light, fontFeatureSettings = "tnum"),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

enum class ThemePalette(val code: String, val hue: Float?, private val es: String, private val en: String) {
    VIGIA("vigia", null, "Vigía", "Vigía"),
    DYNAMIC("dynamic", null, "Material You", "Material You"),
    OCEAN("ocean", 200f, "Océano", "Ocean"),
    FOREST("forest", 145f, "Bosque", "Forest"),
    SUNSET("sunset", 22f, "Atardecer", "Sunset"),
    LAVENDER("lavender", 275f, "Lavanda", "Lavender"),
    RUBY("ruby", 345f, "Rubí", "Ruby");

    val label: String get() = tr(es, en)

    companion object {
        fun from(code: String): ThemePalette = values().firstOrNull { it.code == code } ?: VIGIA
    }
}

/** Preferencias de apariencia. Al cambiar, la app se repinta sin reiniciarse. */
object AppearanceState {
    var mode by mutableStateOf("system")
    var palette by mutableStateOf(ThemePalette.VIGIA)

    fun load(settings: Settings) {
        mode = settings.themeMode
        palette = ThemePalette.from(settings.themePalette)
    }
}

private fun tone(hue: Float, saturation: Float, lightness: Float): Color =
    Color.hsl((hue + 360f) % 360f, saturation, lightness)

/** Roles de Material 3 calculados a partir de un tono: acento, complementarios y superficies. */
fun tonalScheme(hue: Float, dark: Boolean): ColorScheme {
    val second = hue + 20f
    val third = hue + 60f
    return if (dark) {
        darkColorScheme(
            primary = tone(hue, 0.75f, 0.80f),
            onPrimary = tone(hue, 0.85f, 0.18f),
            primaryContainer = tone(hue, 0.55f, 0.30f),
            onPrimaryContainer = tone(hue, 0.85f, 0.92f),
            secondary = tone(second, 0.30f, 0.78f),
            onSecondary = tone(second, 0.35f, 0.18f),
            secondaryContainer = tone(second, 0.25f, 0.28f),
            onSecondaryContainer = tone(second, 0.40f, 0.92f),
            tertiary = tone(third, 0.65f, 0.78f),
            onTertiary = tone(third, 0.70f, 0.18f),
            tertiaryContainer = tone(third, 0.45f, 0.30f),
            onTertiaryContainer = tone(third, 0.75f, 0.92f),
            background = tone(hue, 0.28f, 0.08f),
            onBackground = tone(hue, 0.15f, 0.91f),
            surface = tone(hue, 0.28f, 0.08f),
            onSurface = tone(hue, 0.15f, 0.91f),
            surfaceVariant = tone(hue, 0.15f, 0.24f),
            onSurfaceVariant = tone(hue, 0.15f, 0.80f),
            surfaceContainerLowest = tone(hue, 0.28f, 0.05f),
            surfaceContainerLow = tone(hue, 0.25f, 0.11f),
            surfaceContainer = tone(hue, 0.24f, 0.13f),
            surfaceContainerHigh = tone(hue, 0.22f, 0.17f),
            surfaceContainerHighest = tone(hue, 0.20f, 0.21f),
            outline = tone(hue, 0.10f, 0.60f),
            outlineVariant = tone(hue, 0.14f, 0.30f),
        )
    } else {
        lightColorScheme(
            primary = tone(hue, 0.60f, 0.36f),
            onPrimary = Color.White,
            primaryContainer = tone(hue, 0.80f, 0.90f),
            onPrimaryContainer = tone(hue, 0.80f, 0.14f),
            secondary = tone(second, 0.22f, 0.38f),
            onSecondary = Color.White,
            secondaryContainer = tone(second, 0.45f, 0.90f),
            onSecondaryContainer = tone(second, 0.40f, 0.14f),
            tertiary = tone(third, 0.55f, 0.34f),
            onTertiary = Color.White,
            tertiaryContainer = tone(third, 0.75f, 0.88f),
            onTertiaryContainer = tone(third, 0.70f, 0.13f),
            background = tone(hue, 0.60f, 0.975f),
            onBackground = tone(hue, 0.20f, 0.12f),
            surface = tone(hue, 0.60f, 0.975f),
            onSurface = tone(hue, 0.20f, 0.12f),
            surfaceVariant = tone(hue, 0.25f, 0.90f),
            onSurfaceVariant = tone(hue, 0.12f, 0.30f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = tone(hue, 0.45f, 0.95f),
            surfaceContainer = tone(hue, 0.40f, 0.93f),
            surfaceContainerHigh = tone(hue, 0.36f, 0.91f),
            surfaceContainerHighest = tone(hue, 0.32f, 0.89f),
            outline = tone(hue, 0.10f, 0.48f),
            outlineVariant = tone(hue, 0.18f, 0.80f),
        )
    }
}

fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun VigiaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (AppearanceState.mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val palette = AppearanceState.palette
    val hue = palette.hue
    val colors = when {
        hue != null -> tonalScheme(hue, dark)
        palette == ThemePalette.DYNAMIC && supportsDynamicColor() ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
