package app.vigia.alarmas.clock

import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.View
import android.widget.FrameLayout
import app.vigia.alarmas.R
import kotlin.random.Random

/** Salvapantallas: reloj tenue que cambia de sitio para no marcar la pantalla. */
class VigiaDream : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private var content: View? = null

    private val move = object : Runnable {
        override fun run() {
            content?.let { view ->
                val parent = view.parent as? FrameLayout
                if (parent != null) {
                    val maxX = (parent.width - view.width).coerceAtLeast(0) / 2f
                    val maxY = (parent.height - view.height).coerceAtLeast(0) / 2f
                    view.animate()
                        .translationX(Random.nextFloat() * 2 * maxX - maxX)
                        .translationY(Random.nextFloat() * 2 * maxY - maxY)
                        .setDuration(1_500)
                        .start()
                }
            }
            handler.postDelayed(this, MOVE_EVERY_MS)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        isScreenBright = false
        setContentView(R.layout.dream_clock)
        content = findViewById(R.id.dream_content)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        handler.postDelayed(move, MOVE_EVERY_MS)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(move)
        super.onDreamingStopped()
    }

    private companion object {
        const val MOVE_EVERY_MS = 60_000L
    }
}
