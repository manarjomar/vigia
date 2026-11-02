package app.vigia.alarmas.domain

import java.util.Locale

/** Idioma de la app. Sin elección propia, sigue al móvil: español si está en español, inglés si no. */
object Lang {
    var override: Boolean? = null

    val spanish: Boolean
        get() = override ?: (Locale.getDefault().language == "es")
}

fun tr(spanish: String, english: String): String = if (Lang.spanish) spanish else english

val APP_LOCALE: Locale
    get() = if (Lang.spanish) Locale.forLanguageTag("es-ES") else Locale.ENGLISH
