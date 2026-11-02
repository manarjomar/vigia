package app.vigia.alarmas.ui

import app.vigia.alarmas.domain.tr
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.vigia.alarmas.R

private const val AUTHOR = "manarjomar"

/** Muestra el texto negrita entre dobles asteriscos sin depender de un visor de Markdown. */
private fun richText(text: String) = buildAnnotatedString {
    text.replace("`", "").split("**").forEachIndexed { index, part ->
        if (index % 2 == 1) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) }
        } else {
            append(part)
        }
    }
}

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val lines = remember {
        runCatching {
            context.assets.open(tr("descripcion.md", "description.md")).bufferedReader().use { it.readLines() }
        }.getOrDefault(emptyList()).filter { it.isNotBlank() }.drop(1)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.vigia_logo), null, Modifier.size(112.dp))
            Text("Vigía", style = MaterialTheme.typography.headlineMedium)
            Text(tr("Versión $version", "Version $version"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tr("Creada por $AUTHOR", "Created by $AUTHOR"))
            Text(
                tr("© 2026 $AUTHOR. Todos los derechos reservados.", "© 2026 $AUTHOR. All rights reserved."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()

        lines.forEach { line ->
            when {
                line.startsWith("### ") -> Text(
                    line.removePrefix("### "),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                line.startsWith("## ") -> Text(
                    line.removePrefix("## "),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp),
                )
                line.startsWith("- ") -> Text(richText("•  " + line.removePrefix("- ")))
                else -> Text(richText(line))
            }
        }

        HorizontalDivider()
        Text(
            tr("Los festivos internacionales proceden de Nager.Date. La lectura de códigos QR usa ZXing Android Embedded.", "International holidays come from Nager.Date. QR code scanning uses ZXing Android Embedded."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
