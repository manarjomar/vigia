package app.vigia.alarmas.ui

import app.vigia.alarmas.domain.tr
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vigia.alarmas.Graph
import app.vigia.alarmas.data.AlarmEntity

private data class Destination(val label: String, val icon: ImageVector)

private fun destinations() = listOf(
    Destination(tr("Alarmas", "Alarms"), Icons.Filled.Alarm),
    Destination(tr("Reloj", "Clock"), Icons.Filled.Schedule),
    Destination(tr("Crono", "Stopwatch"), Icons.Filled.AvTimer),
    Destination(tr("Tiempo", "Timers"), Icons.Filled.HourglassBottom),
    Destination(tr("Más", "More"), Icons.Filled.MoreHoriz),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VigiaTheme {
                val context = LocalContext.current
                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                var tab by rememberSaveable { mutableIntStateOf(0) }
                var editing by remember { mutableStateOf<AlarmEntity?>(null) }
                var lastEditing by remember { mutableStateOf<AlarmEntity?>(null) }
                val alarms by remember { Graph.database(context).alarms().observeAll() }
                    .collectAsStateWithLifecycle(initialValue = emptyList())

                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box {
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            bottomBar = {
                                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    destinations().forEachIndexed { index, destination ->
                                        NavigationBarItem(
                                            selected = tab == index,
                                            onClick = { tab = index },
                                            icon = { Icon(destination.icon, null) },
                                            label = { Text(destination.label) },
                                        )
                                    }
                                }
                            },
                        ) { padding ->
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = {
                                    fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                                },
                                label = "tab",
                                modifier = Modifier.padding(padding),
                            ) { current ->
                                val modifier = Modifier.fillMaxSize()
                                when (current) {
                                    0 -> AlarmListScreen(modifier) {
                                        lastEditing = it
                                        editing = it
                                    }
                                    1 -> ClockScreen(modifier)
                                    2 -> StopwatchScreen(modifier)
                                    3 -> TimersScreen(modifier)
                                    else -> MoreScreen(modifier)
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = editing != null,
                            enter = slideInVertically { it / 3 } + fadeIn(),
                            exit = slideOutVertically { it / 3 } + fadeOut(),
                        ) {
                            lastEditing?.let { current ->
                                AlarmEditorScreen(
                                    initial = current,
                                    groups = alarms.map { it.config.group }.filter { it.isNotBlank() }.distinct(),
                                    onClose = { editing = null },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
