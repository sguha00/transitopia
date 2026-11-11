package com.transitopia.bartwidget.widget

import android.content.Context
import android.os.Handler
import android.os.Looper

/** Runs only during a user-started live session. Text freezes safely if execution is delayed. */
class WidgetLiveTicker(
    private val context: Context,
    private val widgetIds: () -> IntArray,
    private val render: (Int, Long) -> Unit,
    private val refresh: (Int) -> Unit,
    private val onIdle: () -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    private val previouslyLive = mutableSetOf<Int>()
    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.currentTimeMillis() / 1000
            val ids = widgetIds()
            val live = ids.filter { WidgetPrefs.liveUntil(context, it) > now }
            for (id in live) {
                render(id, now)
                if (WidgetPrefs.claimDepartureRefresh(context, id, now)) refresh(id)
            }
            for (id in previouslyLive - live.toSet()) {
                if (id in ids) render(id, now)
            }
            previouslyLive.clear()
            previouslyLive.addAll(live)
            if (live.isEmpty()) {
                stop()
                onIdle()
            } else {
                // Align to wall-clock seconds, rather than accumulating callback/render drift.
                handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000 + 5)
            }
        }
    }

    fun start() {
        running = true
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }
}
