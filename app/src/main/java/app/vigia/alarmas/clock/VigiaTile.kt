package app.vigia.alarmas.clock

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.vigia.alarmas.Graph
import app.vigia.alarmas.R
import app.vigia.alarmas.domain.DayState
import app.vigia.alarmas.domain.DayStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Botón del panel rápido: muestra el estado y salta la alarma o hace que suene. */
class VigiaTile : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var status: DayStatus? = null

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val current = status ?: return
        if (current.alarmId == null) return
        if (isLocked) unlockAndRun { toggle(current) } else toggle(current)
    }

    private fun toggle(current: DayStatus) {
        scope.launch {
            runCatching { Graph.coordinator(this@VigiaTile).toggleStatus(current) }
            refresh()
        }
    }

    private fun refresh() {
        scope.launch {
            val data = runCatching { Graph.coordinator(this@VigiaTile).widgetData() }.getOrNull() ?: return@launch
            status = data.focus
            val tile = qsTile ?: return@launch
            val rings = data.focus.state != DayState.SKIPPED && data.focus.state != DayState.NONE
            tile.icon = Icon.createWithResource(this@VigiaTile, R.drawable.ic_notification)
            tile.label = getString(R.string.app_name)
            tile.subtitle = StatusText.tile(data.focus)
            tile.state = if (rings) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
