package com.transitopia.bartwidget;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import com.transitopia.bartwidget.widget.WidgetPrefs;

/** Device regressions for route cache invalidation and launcher-owned countdown ticking. */
public class WidgetBehaviorInstrumentation {
    public static String runChecks(Instrumentation driver) {
        Activity activity = null;
        try {
            final int testId = 900001;
            WidgetPrefs.INSTANCE.save(driver.getTargetContext(), testId, "24TH", "EMBR");
            WidgetPrefs.INSTANCE.setLive(driver.getTargetContext(), testId, System.currentTimeMillis()/1000 + 900,
                java.util.Arrays.asList(System.currentTimeMillis()/1000 + 300), false, null);
            WidgetPrefs.INSTANCE.save(driver.getTargetContext(), testId, "EMBR", "24TH");
            if (!WidgetPrefs.INSTANCE.departures(driver.getTargetContext(), testId).isEmpty()) throw new AssertionError("Old route departures survived a station change");
            if (WidgetPrefs.INSTANCE.liveUntil(driver.getTargetContext(), testId) != 0) throw new AssertionError("Old route live window survived");

            activity = driver.startActivitySync(new Intent(driver.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final Activity host = activity;
            final Chronometer[] timer = new Chronometer[1];
            driver.runOnMainSync(() -> {
                RemoteViews remote = new RemoteViews(driver.getTargetContext().getPackageName(), R.layout.bart_widget_layout);
                remote.setViewVisibility(R.id.eta_next, View.GONE);
                remote.setViewVisibility(R.id.eta_countdown, View.VISIBLE);
                remote.setChronometerCountDown(R.id.eta_countdown, true);
                remote.setChronometer(R.id.eta_countdown, SystemClock.elapsedRealtime() + 120000, null, true);
                FrameLayout root = new FrameLayout(host);
                View view = remote.apply(host, root);
                root.addView(view);
                host.setContentView(root);
                timer[0] = view.findViewById(R.id.eta_countdown);
            });
            String previous = read(driver, timer[0]);
            StringBuilder samples = new StringBuilder(previous);
            // No widget broadcasts or provider updates during this test.
            for (int i = 0; i < 6; i++) {
                SystemClock.sleep(1100);
                String current = read(driver, timer[0]);
                if (current.equals(previous)) throw new AssertionError("Countdown did not tick: " + samples);
                samples.append(" → ").append(current);
                previous = current;
            }
            driver.getTargetContext().getSharedPreferences("bart_widget_prefs", 0).edit()
                .remove("origin_"+testId).remove("terminal_"+testId).remove("deps_"+testId)
                .remove("lastTrain_"+testId).remove("error_"+testId).remove("liveUntil_"+testId)
                .remove("updated_"+testId).commit();
            return "PASS route cache invalidation; native countdown without provider updates: " + samples;
        } finally {
            if (activity != null) { final Activity closing = activity; driver.runOnMainSync(closing::finish); }
        }
    }
    private static String read(Instrumentation driver, Chronometer timer) {
        final String[] text = new String[1];
        driver.runOnMainSync(() -> text[0] = timer.getText().toString());
        return text[0];
    }
}
