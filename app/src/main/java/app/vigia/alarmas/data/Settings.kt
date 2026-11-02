package app.vigia.alarmas.data

import android.content.Context
import app.vigia.alarmas.domain.AlarmConfig
import app.vigia.alarmas.domain.AppJson
import app.vigia.alarmas.domain.Lang
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class AlarmTemplate(val name: String, val config: AlarmConfig)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("vigia", Context.MODE_PRIVATE)

    var noticeEnabled: Boolean
        get() = prefs.getBoolean("notice_enabled", true)
        set(value) = prefs.edit().putBoolean("notice_enabled", value).apply()

    var noticeHour: Int
        get() = prefs.getInt("notice_hour", 21)
        set(value) = prefs.edit().putInt("notice_hour", value).apply()

    var noticeMinute: Int
        get() = prefs.getInt("notice_minute", 0)
        set(value) = prefs.edit().putInt("notice_minute", value).apply()

    /** Vacío: sigue al móvil. También admite "es" y "en". */
    var language: String
        get() = prefs.getString("language", "").orEmpty()
        set(value) = prefs.edit().putString("language", value).apply()

    fun applyLanguage() {
        Lang.override = when (language) {
            "es" -> true
            "en" -> false
            else -> null
        }
    }

    var themeMode: String
        get() = prefs.getString("theme_mode", "system").orEmpty()
        set(value) = prefs.edit().putString("theme_mode", value).apply()

    var themePalette: String
        get() = prefs.getString("theme_palette", "vigia").orEmpty()
        set(value) = prefs.edit().putString("theme_palette", value).apply()

    /** Opacidad del fondo de los widgets, de 0 a 100. */
    var widgetOpacity: Int
        get() = prefs.getInt("widget_opacity", 100)
        set(value) = prefs.edit().putInt("widget_opacity", value).apply()

    var worldClocks: List<String>
        get() = prefs.getString("world_clocks", "").orEmpty().split(",").filter { it.isNotBlank() }
        set(value) = prefs.edit().putString("world_clocks", value.joinToString(",")).apply()

    var templates: List<AlarmTemplate>
        get() = runCatching {
            AppJson.decodeFromString(
                ListSerializer(AlarmTemplate.serializer()),
                prefs.getString("templates", "[]").orEmpty(),
            )
        }.getOrDefault(emptyList())
        set(value) = prefs.edit().putString(
            "templates",
            AppJson.encodeToString(ListSerializer(AlarmTemplate.serializer()), value),
        ).apply()

    fun isChecked(key: String): Boolean = prefs.getBoolean("check_$key", false)

    fun setChecked(key: String, value: Boolean) {
        prefs.edit().putBoolean("check_$key", value).apply()
    }
}
