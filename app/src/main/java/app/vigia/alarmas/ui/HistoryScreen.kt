package app.vigia.alarmas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vigia.alarmas.Graph
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", SPANISH)

@Composable
fun HistoryScreen(modifier: Modifier) {
    val context = LocalContext.current
    val entries by remember { Graph.database(context).history().observeRecent() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    if (entries.isEmpty()) {
        Text("Aquí verás cuándo sonó o se saltó cada alarma, y por qué.", modifier.padding(24.dp))
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            Column {
                Text("${entry.type} · ${entry.label}", style = MaterialTheme.typography.titleSmall)
                val time = Instant.ofEpochMilli(entry.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .format(STAMP)
                val detail = if (entry.detail.isBlank()) time else "$time · ${entry.detail}"
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
