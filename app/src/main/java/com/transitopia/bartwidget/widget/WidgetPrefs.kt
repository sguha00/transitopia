package com.transitopia.bartwidget.widget

import android.content.Context

object WidgetPrefs {
    private const val FILE = "bart_widget_prefs"
    fun save(context: Context, appWidgetId: Int, origin: String, terminal: String) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val changed = origin(context, appWidgetId) != origin || terminal(context, appWidgetId) != terminal
        val edit = prefs.edit().putString("origin_$appWidgetId", origin)
            .putString("terminal_$appWidgetId", terminal)
        if (changed) {
            edit.remove("deps_$appWidgetId").remove("lastTrain_$appWidgetId")
                .remove("error_$appWidgetId").remove("liveUntil_$appWidgetId")
        }
        edit.apply()
    }
    fun swap(context: Context, appWidgetId: Int) {
        val o = origin(context, appWidgetId); val t = terminal(context, appWidgetId)
        if (o != null && t != null) save(context, appWidgetId, t, o)
    }
    fun origin(context: Context, id: Int): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("origin_$id", null)
    fun terminal(context: Context, id: Int): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("terminal_$id", null)

    // Live-mode state: live until epoch sec, cached departure epochs, last-train flag, error.
    fun setLive(context: Context, id: Int, liveUntilSec: Long,
                departures: List<Long>, isLastTrain: Boolean, error: String?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong("liveUntil_$id", liveUntilSec)
            .putString("deps_$id", departures.joinToString(","))
            .putBoolean("lastTrain_$id", isLastTrain)
            .putString("error_$id", error)
            .putLong("updated_$id", System.currentTimeMillis() / 1000)
            .apply()
    }
    fun liveUntil(context: Context, id: Int): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong("liveUntil_$id", 0L)
    fun departures(context: Context, id: Int): List<Long> {
        val s = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("deps_$id", "") ?: ""
        return if (s.isBlank()) emptyList() else s.split(",").mapNotNull { it.toLongOrNull() }
    }
    fun isLastTrain(context: Context, id: Int): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("lastTrain_$id", false)
    fun error(context: Context, id: Int): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("error_$id", null)
}
