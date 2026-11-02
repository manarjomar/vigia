package app.vigia.alarmas.alarm

import app.vigia.alarmas.domain.tr
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.ServiceCompat
import app.vigia.alarmas.Graph
import app.vigia.alarmas.data.HistoryType
import app.vigia.alarmas.domain.AlarmConfig
import app.vigia.alarmas.domain.ChallengeConfig
import app.vigia.alarmas.domain.ChallengeKind
import app.vigia.alarmas.domain.SoundConfig
import app.vigia.alarmas.ui.RingingActivity
import java.time.LocalDate
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class RingState(
    val label: String,
    val alarmIds: List<Long>,
    val challenge: ChallengeConfig = ChallengeConfig(),
    val snoozeMinutes: Int? = null,
    val fadeSeconds: Int = 0,
    val burst: Boolean = false,
)

class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var rampJob: Job? = null
    private var timeoutJob: Job? = null
    private var pendingChecks = 0
    private var originalVolume: Int? = null
    private var backupOnTimeout = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FIRE -> if (intent != null) handleFire(intent)
            ACTION_TIMER -> if (intent != null) handleTimer(intent)
            ACTION_DISMISS -> dismiss()
            ACTION_SNOOZE -> snooze()
            ACTION_STOP_DAY -> stopForToday()
            else -> if (_ringing.value == null && pendingChecks == 0) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleTimer(intent: Intent) {
        val name = intent.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { tr("Temporizador", "Timer") }
        ring(NO_ALARM_ID, RingState(name, emptyList()), SoundConfig(), AlarmConfig().ringLimitMinutes)
    }

    private fun handleFire(intent: Intent) {
        if (_ringing.value == null) {
            goForeground(Notifications.SERVICE_ID, Notifications.checking(this))
        }
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0)
        val snoozed = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZED, false)
        val backup = intent.getBooleanExtra(AlarmScheduler.EXTRA_BACKUP, false)
        val date = intent.getStringExtra(AlarmScheduler.EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()

        if (alarmId == AlarmScheduler.TEST_ALARM_ID) {
            ring(NO_ALARM_ID, RingState(tr("Prueba de alarma", "Alarm test"), emptyList()), SoundConfig(), 2)
            return
        }

        pendingChecks++
        scope.launch {
            val coordinator = Graph.coordinator(this@AlarmService)
            val result = try {
                withTimeoutOrNull(CHECK_TIMEOUT_MS) {
                    coordinator.checkFire(alarmId, date, snoozed, backup)
                } ?: FireResult(ring = true)
            } catch (_: Exception) {
                FireResult(ring = true)
            }
            pendingChecks--

            if (result.ring) {
                ringAlarm(alarmId, result)
                runCatching { coordinator.afterRing(alarmId) }
            } else if (_ringing.value == null && pendingChecks == 0) {
                stopService()
            }
        }
    }

    private fun ringAlarm(alarmId: Long, result: FireResult) {
        val config = result.config
        val label = config.label.ifBlank { "%02d:%02d".format(config.hour, config.minute) }
        val snooze = config.snooze
        val isBurst = result.burst != null
        val canSnooze = !isBurst && !result.backup && (snooze.maxCount == 0 || result.snoozeCount < snooze.maxCount)
        val sound = if (result.backup) config.sound.copy(volumePercent = 100, fadeSeconds = 0) else config.sound
        backupOnTimeout = config.backup.enabled && !result.backup && !isBurst
        val limit = result.burst?.let { minOf(it.everyMinutes, config.ringLimitMinutes) } ?: config.ringLimitMinutes
        ring(
            alarmId,
            RingState(
                label = label,
                alarmIds = listOf(alarmId),
                challenge = config.challenge,
                snoozeMinutes = if (canSnooze) snooze.minutes else null,
                fadeSeconds = sound.fadeSeconds,
                burst = isBurst,
            ),
            sound,
            limit,
        )
    }

    private fun ring(alarmId: Long, state: RingState, sound: SoundConfig, limitMinutes: Int) {
        val current = _ringing.value
        if (current != null) {
            if (alarmId in current.alarmIds) {
                armTimeout(limitMinutes)
                return
            }
            _ringing.value = current.copy(
                label = tr("${current.label} y ${state.label}", "${current.label} and ${state.label}"),
                alarmIds = (current.alarmIds + alarmId).filter { it != NO_ALARM_ID },
            )
            return
        }

        _ringing.value = state
        goForeground(Notifications.RINGING_ID, Notifications.ringing(this, state))
        Notifications.cancelChecking(this)
        acquireWakeLock(limitMinutes)
        wakeScreen()
        openRingingScreen()
        applyVolume(sound.volumePercent)
        startSound(sound)
        if (sound.vibrate) startVibration()

        armTimeout(limitMinutes)
    }

    private fun armTimeout(limitMinutes: Int) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(limitMinutes * 60_000L)
            onTimeout()
        }
    }

    private fun onTimeout() {
        val state = _ringing.value ?: return stopService()
        val backup = backupOnTimeout
        scope.launch {
            val coordinator = Graph.coordinator(this@AlarmService)
            state.alarmIds.forEach { id ->
                runCatching {
                    if (backup) coordinator.scheduleBackup(id) else coordinator.finish(id, HistoryType.UNANSWERED)
                }
            }
            stopRinging()
        }
    }

    private fun goForeground(id: Int, notification: Notification) {
        ServiceCompat.startForeground(
            this,
            id,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun dismiss() {
        val state = _ringing.value ?: return stopService()
        scope.launch {
            val coordinator = Graph.coordinator(this@AlarmService)
            state.alarmIds.forEach { runCatching { coordinator.finish(it, HistoryType.DISMISSED) } }
            stopRinging()
        }
    }

    private fun stopForToday() {
        val state = _ringing.value ?: return stopService()
        scope.launch {
            val coordinator = Graph.coordinator(this@AlarmService)
            state.alarmIds.forEach { runCatching { coordinator.stopForToday(it) } }
            stopRinging()
        }
    }

    private fun snooze() {
        val state = _ringing.value ?: return stopService()
        scope.launch {
            val coordinator = Graph.coordinator(this@AlarmService)
            state.alarmIds.forEach { runCatching { coordinator.snooze(it) } }
            stopRinging()
        }
    }

    private fun stopRinging() {
        timeoutJob?.cancel()
        rampJob?.cancel()
        releaseMedia()
        _ringing.value = null
        stopService()
    }

    private fun stopService() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun applyVolume(percent: Int) {
        runCatching {
            val audio = getSystemService(AudioManager::class.java)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            originalVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            val target = ceil(max * percent.coerceIn(5, 100) / 100.0).toInt().coerceIn(1, max)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        }
    }

    private fun restoreVolume() {
        val volume = originalVolume ?: return
        originalVolume = null
        runCatching {
            getSystemService(AudioManager::class.java)
                .setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
        }
    }

    private fun startSound(sound: SoundConfig) {
        val candidates = listOf(
            sound.uri?.let { Uri.parse(it) },
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (uri in candidates) {
            if (uri != null && tryPlay(uri, sound.fadeSeconds)) return
        }
    }

    private fun tryPlay(uri: Uri, fadeSeconds: Int): Boolean {
        val media = MediaPlayer()
        return try {
            val initial = if (fadeSeconds > 0) INITIAL_VOLUME else 1f
            media.setAudioAttributes(ALARM_ATTRIBUTES)
            media.setDataSource(this, uri)
            media.isLooping = true
            media.setVolume(initial, initial)
            media.prepare()
            media.start()
            player = media
            if (fadeSeconds > 0) rampVolume(media, fadeSeconds)
            true
        } catch (_: Exception) {
            media.release()
            false
        }
    }

    private fun rampVolume(media: MediaPlayer, seconds: Int) {
        rampJob = scope.launch {
            repeat(seconds) { step ->
                delay(1_000)
                val volume = INITIAL_VOLUME + (1f - INITIAL_VOLUME) * (step + 1) / seconds
                runCatching { media.setVolume(volume, volume) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        vibrator = getSystemService(Vibrator::class.java)
        vibrator?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0),
            ALARM_ATTRIBUTES,
        )
    }

    private fun acquireWakeLock(limitMinutes: Int) {
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vigia:alarm").apply {
            acquire(MAX_RING_MS)
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        runCatching {
            val power = getSystemService(PowerManager::class.java)
            power.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "vigia:screen",
            ).acquire(SCREEN_WAKE_MS)
        }
    }

    private fun openRingingScreen() {
        runCatching {
            startActivity(
                Intent(this, RingingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            )
        }
    }

    private fun releaseMedia() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        restoreVolume()
    }

    override fun onDestroy() {
        releaseMedia()
        _ringing.value = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_FIRE = "app.vigia.alarmas.action.FIRE"
        const val ACTION_TIMER = "app.vigia.alarmas.action.TIMER"
        const val ACTION_DISMISS = "app.vigia.alarmas.action.DISMISS"
        const val ACTION_SNOOZE = "app.vigia.alarmas.action.SNOOZE"
        const val ACTION_STOP_DAY = "app.vigia.alarmas.action.STOP_DAY"
        const val EXTRA_LABEL = "label"

        private const val NO_ALARM_ID = -2L
        private const val CHECK_TIMEOUT_MS = 2_500L
        private const val INITIAL_VOLUME = 0.08f
        private const val SCREEN_WAKE_MS = 30_000L
        private const val MAX_RING_MS = 12 * 60 * 60_000L

        private val ALARM_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        private val _ringing = MutableStateFlow<RingState?>(null)
        val ringing: StateFlow<RingState?> = _ringing.asStateFlow()

        fun needsChallenge(state: RingState): Boolean = state.challenge.kind != ChallengeKind.NONE
    }
}
