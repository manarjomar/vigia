package app.vigia.alarmas.ui

import app.vigia.alarmas.domain.tr
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import app.vigia.alarmas.data.CalendarInfo

/** Calendarios agrupados por cuenta. Cada cuenta se pliega y se puede marcar entera. */
@Composable
fun CalendarPicker(
    calendars: List<CalendarInfo>,
    selected: Set<Long>,
    onChange: (Set<Long>) -> Unit,
) {
    if (calendars.isEmpty()) {
        Text(
            tr("No hay calendarios disponibles. Comprueba el permiso y la sincronización de tu cuenta.", "No calendars available. Check the permission and your account sync."),
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    Column {
        calendars.groupBy { it.account }.toSortedMap().forEach { (account, items) ->
            AccountGroup(account, items, selected, onChange)
        }
    }
}

@Composable
private fun AccountGroup(
    account: String,
    items: List<CalendarInfo>,
    selected: Set<Long>,
    onChange: (Set<Long>) -> Unit,
) {
    val ids = items.map { it.id }.toSet()
    val count = ids.count { it in selected }
    var expanded by remember(account) { mutableStateOf(count > 0) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "account")
    val state = when (count) {
        0 -> ToggleableState.Off
        ids.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TriStateCheckbox(
                    state = state,
                    onClick = {
                        onChange(if (state == ToggleableState.On) selected - ids else selected + ids)
                    },
                )
                Icon(
                    Icons.Filled.AccountCircle,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(account, maxLines = 1)
                    Text(
                        if (count == 0) tr("${items.size} calendarios", "${items.size} calendars") else tr("$count de ${items.size} elegidos", "$count of ${items.size} chosen"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Filled.KeyboardArrowDown, tr("Plegar o desplegar", "Collapse or expand"), Modifier.rotate(rotation))
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 16.dp, end = 8.dp, bottom = 8.dp)) {
                    items.sortedBy { it.name.lowercase() }.forEach { calendar ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onChange(toggle(selected, calendar.id))
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = calendar.id in selected,
                                onCheckedChange = { onChange(toggle(selected, calendar.id)) },
                            )
                            Column {
                                Text(calendar.name)
                                if (!calendar.syncing) {
                                    Text(
                                        tr("No se sincroniza en este móvil", "Not syncing on this phone"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun toggle(selected: Set<Long>, id: Long): Set<Long> =
    if (id in selected) selected - id else selected + id
