package com.transitopia.bartwidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.transitopia.bartwidget.R
import com.transitopia.bartwidget.data.BartLogic
import com.transitopia.bartwidget.data.BartRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BartWidgetProvider : AppWidgetProvider() {
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) render(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("appWidgetId", -1)
        val manager = AppWidgetManager.getInstance(context)
        when (intent.action) {
            ACTION_SWAP, ACTION_TAP, ACTION_NETWORK_REFRESH -> if (id >= 0) {
                if (intent.action == ACTION_NETWORK_REFRESH &&
                    WidgetPrefs.liveUntil(context, id) <= System.currentTimeMillis() / 1000) {
                    render(context, manager, id)
                    return
                }
                if (intent.action == ACTION_SWAP) WidgetPrefs.swap(context, id)
                if (intent.action != ACTION_NETWORK_REFRESH) beginLive(context, id)
                // Keep the receiver alive until the network request and widget update finish.
                val pending = goAsync()
                scope.launch {
                    try { fetchAndRender(context.applicationContext, manager, id,
                        showChecking = intent.action != ACTION_NETWORK_REFRESH) }
                    finally { pending.finish() }
                }
            }
            ACTION_TICK -> if (id >= 0) {
                render(context, manager, id)
                refreshAtDeparture(context, id, System.currentTimeMillis() / 1000)
                scheduleNextTick(context, id)
            }
            else -> super.onReceive(context, intent)
        }
    }

    /** Used by configuration as well as the widget's tap action. */
    fun startLive(context: Context, appWidgetId: Int) {
        context.sendBroadcast(Intent(context, BartWidgetProvider::class.java)
            .setAction(ACTION_TAP).putExtra("appWidgetId", appWidgetId))
    }

    private fun beginLive(context: Context, appWidgetId: Int) {
        val now = System.currentTimeMillis() / 1000
        WidgetPrefs.setLive(context, appWidgetId, now + LIVE_WINDOW_SEC,
            WidgetPrefs.departures(context, appWidgetId), WidgetPrefs.isLastTrain(context, appWidgetId), null)
        scheduleNetworkRefreshes(context, appWidgetId, now)
        scheduleNextTick(context, appWidgetId)
        WidgetTickerService.start(context)
    }

    private suspend fun fetchAndRender(context: Context, manager: AppWidgetManager, appWidgetId: Int,
                                       showChecking: Boolean = true) {
        val origin = WidgetPrefs.origin(context, appWidgetId) ?: run { render(context, manager, appWidgetId); return }
        val terminal = WidgetPrefs.terminal(context, appWidgetId) ?: run { render(context, manager, appWidgetId); return }
        val liveUntil = WidgetPrefs.liveUntil(context, appWidgetId)
        render(context, manager, appWidgetId, checking = showChecking)
        val result = BartRepository(context).nextTrainsDetailed(origin, terminal, 3)
        // An earlier route's in-flight response must never overwrite a newer selection.
        if (WidgetPrefs.origin(context, appWidgetId) != origin ||
            WidgetPrefs.terminal(context, appWidgetId) != terminal ||
            WidgetPrefs.liveUntil(context, appWidgetId) != liveUntil) return
        result.fold(
            onSuccess = { (trains, total) ->
                WidgetPrefs.setLive(context, appWidgetId, liveUntil,
                    trains.map { it.departureEpochSec }, total == 1, null)
            },
            onFailure = { e ->
                WidgetPrefs.setLive(context, appWidgetId, liveUntil,
                    WidgetPrefs.departures(context, appWidgetId), WidgetPrefs.isLastTrain(context, appWidgetId), e.message)
            }
        )
        render(context, manager, appWidgetId)
        scheduleNextTick(context, appWidgetId)
    }

    fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int, checking: Boolean = false) {
        manager.updateAppWidget(appWidgetId, createViews(context, appWidgetId, checking))
    }

    /** Bounded text cannot go negative, even if the process or a scheduled update is delayed. */
    fun createViews(context: Context, appWidgetId: Int, checking: Boolean = false,
                    now: Long = System.currentTimeMillis() / 1000): RemoteViews {
        val origin = WidgetPrefs.origin(context, appWidgetId)
        val terminal = WidgetPrefs.terminal(context, appWidgetId)
        if (origin == null || terminal == null) {
            return buildViews(context, appWidgetId, context.getString(R.string.app_name), listOf("Set up widget"), "SETUP", false, false)
        }
        val repo = BartRepository(context.applicationContext)
        val stations = repo.loadStations()
        val originName = BartLogic.shortName(stations.firstOrNull { it.id == origin }?.name ?: origin)
        val terminalName = BartLogic.shortName(stations.firstOrNull { it.id == terminal }?.name ?: terminal)
        val route = "$originName → $terminalName"
        val live = WidgetPrefs.liveUntil(context, appWidgetId) > now
        if (checking) { return buildViews(context, appWidgetId, route, listOf("Checking…"), "LIVE", true, false) }
        if (!live) { return buildViews(context, appWidgetId, route, listOf("Tap to Refresh"), "STALE", false, false) }
        val error = WidgetPrefs.error(context, appWidgetId)
        if (error != null && WidgetPrefs.departures(context, appWidgetId).isEmpty()) {
            return buildViews(context, appWidgetId, route, listOf("Unavailable"), "STALE", false, false)
        }
        val deps = WidgetPrefs.departures(context, appWidgetId).filter { it >= now }.take(3)
        if (deps.isEmpty()) {
            return buildViews(context, appWidgetId, route, listOf("No trains found"), "LIVE", true, false)
        }
        // Keep a zero-second departure for Leaving; remove it on the first second after departure.
        val estimates = buildEstimates(deps, now)
        val isLast = WidgetPrefs.isLastTrain(context, appWidgetId) && deps.size == 1
        return buildViews(context, appWidgetId, route, estimates, "LIVE", true, isLast)
    }

    /** Leaving at exactly zero; elapsed departures are excluded before formatting. */
    private fun buildEstimates(deps: List<Long>, now: Long): List<String> =
        deps.mapIndexed { i, dep ->
            val etaSec = dep - now
            if (i == 0) BartLogic.formatNextEta(etaSec) else BartLogic.formatLaterEta(etaSec)
        }

    private fun buildViews(context: Context, id: Int, route: String,
                           estimates: List<String>, badge: String, live: Boolean, lastTrain: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.bart_widget_layout)
        views.setTextViewText(R.id.eta_route, route)
        views.setTextViewText(R.id.eta_next, estimates.getOrNull(0) ?: "")
        views.setTextViewText(R.id.eta_second, estimates.getOrNull(1) ?: "")
        views.setTextViewText(R.id.eta_third, estimates.getOrNull(2) ?: "")
        views.setViewVisibility(R.id.sep1, if (estimates.size > 1) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.eta_second, if (estimates.size > 1) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.sep2, if (estimates.size > 2) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.eta_third, if (estimates.size > 2) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.badge, badge)
        views.setTextColor(R.id.badge, Color.parseColor(if (live) "#15803D" else "#6B7280"))
        views.setInt(R.id.badge, "setBackgroundResource",
            if (badge == "LIVE") R.drawable.badge_live else R.drawable.badge_stale)
        views.setViewVisibility(R.id.last_train_tag, if (lastTrain) View.VISIBLE else View.GONE)
        // Swap button: swap-horiz icon, reverses direction and restarts live mode.
        val swap = Intent(context, BartWidgetProvider::class.java)
            .setAction(ACTION_SWAP).putExtra("appWidgetId", id)
        views.setOnClickPendingIntent(R.id.swap_button, PendingIntent.getBroadcast(
            context, id * 10 + 1, swap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        // Settings cog (bottom right): edit this widget's station/destination config.
        val settings = Intent(context, WidgetConfigureActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        views.setOnClickPendingIntent(R.id.settings_button, PendingIntent.getActivity(
            context, id * 10 + 6, settings, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        // Tap anywhere else: refresh + restart live window.
        val tap = Intent(context, BartWidgetProvider::class.java)
            .setAction(ACTION_TAP).putExtra("appWidgetId", id)
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getBroadcast(
            context, id * 10 + 2, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return views
    }

    private fun alarm(context: Context, requestCode: Int, action: String, id: Int, triggerAtMs: Long,
                      allowWhileIdle: Boolean = true) {
        val intent = Intent(context, BartWidgetProvider::class.java).setAction(action).putExtra("appWidgetId", id)
        val pi = PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (allowWhileIdle) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            else am.setExact(AlarmManager.RTC, triggerAtMs, pi)
        } catch (e: SecurityException) {
            // Exact-alarm permission denied (Android 14+): fall back to inexact (may drift).
            if (allowWhileIdle) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            else am.set(AlarmManager.RTC, triggerAtMs, pi)
        }
    }

    private fun scheduleNextTick(context: Context, appWidgetId: Int) {
        val now = System.currentTimeMillis() / 1000
        if (WidgetPrefs.liveUntil(context, appWidgetId) <= now) return
        val departures = WidgetPrefs.departures(context, appWidgetId).filter { it >= now }.take(3)
        val changes = mutableListOf(WidgetPrefs.liveUntil(context, appWidgetId))
        departures.forEachIndexed { index, departure ->
            changes += departure // Leaving + prediction refresh at zero.
            changes += departure + 1 // Promote the next train after this one departs.
            if (index > 0 || departure - now >= 600) {
                changes += now + (departure - now) % 60 + 1 // Whole-minute label changes.
            }
        }
        // Fallback state transitions; the bounded live ticker handles visible one-second updates.
        alarm(context, appWidgetId * 10 + 3, ACTION_TICK, appWidgetId,
            changes.filter { it > now }.minOrNull()!! * 1000, allowWhileIdle = false)
    }

    private fun scheduleNetworkRefreshes(context: Context, appWidgetId: Int, nowSec: Long) {
        // Auto network refresh every 5 min for the 15-min live window (+5, +10; +15 ends live).
        alarm(context, appWidgetId * 10 + 4, ACTION_NETWORK_REFRESH, appWidgetId, (nowSec + 300) * 1000)
        alarm(context, appWidgetId * 10 + 5, ACTION_NETWORK_REFRESH, appWidgetId, (nowSec + 600) * 1000)
    }

    companion object {
        const val ACTION_REFRESH = "com.transitopia.bartwidget.REFRESH"
        const val ACTION_TAP = "com.transitopia.bartwidget.TAP"
        const val ACTION_SWAP = "com.transitopia.bartwidget.SWAP"
        const val ACTION_TICK = "com.transitopia.bartwidget.TICK"
        const val ACTION_NETWORK_REFRESH = "com.transitopia.bartwidget.NETWORK_REFRESH"
        const val LIVE_WINDOW_SEC = 900L
        fun refreshAtDeparture(context: Context, id: Int, now: Long) {
            if (WidgetPrefs.claimDepartureRefresh(context, id, now)) {
                context.sendBroadcast(Intent(context, BartWidgetProvider::class.java)
                    .setAction(ACTION_NETWORK_REFRESH).putExtra("appWidgetId", id))
            }
        }

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, BartWidgetProvider::class.java))
                .forEach { BartWidgetProvider().render(context, manager, it) }
            WidgetRefreshScheduler.schedule(context)
        }
    }
}
