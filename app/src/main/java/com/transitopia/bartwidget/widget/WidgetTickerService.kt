package com.transitopia.bartwidget.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.transitopia.bartwidget.MainActivity
import com.transitopia.bartwidget.R

/** User-initiated, time-bounded updates; no wake lock or one-second wakeup alarms. */
class WidgetTickerService : Service() {
    private lateinit var ticker: WidgetLiveTicker
    private val provider = BartWidgetProvider()
    private val manager by lazy { AppWidgetManager.getInstance(this) }
    private fun widgetIds() = manager.getAppWidgetIds(ComponentName(this, BartWidgetProvider::class.java))

    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(
            CHANNEL, "Live train estimates", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shown during a widget's 15-minute live countdown." })
        ticker = WidgetLiveTicker(this, ::widgetIds,
            render = { id, now ->
                manager.updateAppWidget(id, provider.createViews(this, id, now = now))
            },
            refresh = { id ->
                sendBroadcast(Intent(this, BartWidgetProvider::class.java)
                    .setAction(BartWidgetProvider.ACTION_NETWORK_REFRESH).putExtra("appWidgetId", id))
            },
            onIdle = { stopSelf() })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            for (id in widgetIds()) {
                WidgetPrefs.stopLive(this, id)
                provider.render(this, manager, id)
            }
            ticker.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 0, Intent(this, WidgetTickerService::class.java)
            .setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_train)
            .setContentTitle("Transitopia is live")
            .setContentText("Updating train countdowns. Stops when the live window ends.")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Stop live updates", stop).build())
            .build()
        startForeground(NOTIFICATION_ID, notification)
        ticker.start()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ticker.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "live-train-estimates"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.transitopia.bartwidget.STOP_LIVE"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, WidgetTickerService::class.java))
            } catch (e: IllegalStateException) {
                // Device restrictions can stop ticking; bounded text still cannot become negative.
                Log.w("Transitopia", "Live ticker unavailable; using scheduled state updates", e)
            }
        }
    }
}
