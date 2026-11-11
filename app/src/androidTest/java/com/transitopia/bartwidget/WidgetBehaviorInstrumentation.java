package com.transitopia.bartwidget;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.transitopia.bartwidget.widget.BartWidgetProvider;
import com.transitopia.bartwidget.widget.WidgetLiveTicker;
import com.transitopia.bartwidget.widget.WidgetPrefs;
import java.util.ArrayList;
import java.util.List;
import kotlin.Unit;

/** Exercises the same ticker and RemoteViews used by the widget, across the zero boundary. */
public class WidgetBehaviorInstrumentation {
    public static String runChecks(Instrumentation driver) {
        Activity activity = null;
        final WidgetLiveTicker[] ticker = new WidgetLiveTicker[1];
        final int id = 900001;
        try {
            WidgetPrefs.INSTANCE.save(driver.getTargetContext(), id, "24TH", "EMBR");
            WidgetPrefs.INSTANCE.setLive(driver.getTargetContext(), id, System.currentTimeMillis()/1000 + 900,
                java.util.Arrays.asList(System.currentTimeMillis()/1000 + 300), false, null);
            WidgetPrefs.INSTANCE.save(driver.getTargetContext(), id, "EMBR", "24TH");
            if (!WidgetPrefs.INSTANCE.departures(driver.getTargetContext(), id).isEmpty()) throw new AssertionError("Old route departures survived");
            activity = driver.startActivitySync(new Intent(driver.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final Activity host = activity;
            final List<String> samples = new ArrayList<>();
            final List<Long> refreshTimes = new ArrayList<>();
            final long departure = System.currentTimeMillis()/1000 + 4;
            final boolean[] idle = {false};
            driver.runOnMainSync(() -> {
                WidgetPrefs.INSTANCE.setLive(host, id, departure + 3,
                    java.util.Arrays.asList(departure, departure + 120), false, null);
                ticker[0] = new WidgetLiveTicker(host, () -> new int[]{id}, (widgetId, now) -> {
                    FrameLayout root = new FrameLayout(host);
                    View view = new BartWidgetProvider().createViews(host, widgetId, false, now).apply(host, root);
                    root.addView(view);
                    host.setContentView(root);
                    String text = ((TextView)view.findViewById(R.id.eta_next)).getText().toString();
                    if (text.contains("-")) throw new AssertionError("Negative ETA: " + text);
                    if (now < departure && text.equals("Leaving")) throw new AssertionError("Leaving shown before zero");
                    if (now == departure && !text.equals("Leaving")) throw new AssertionError("Missing Leaving at zero: " + text);
                    if (now > departure && now < departure + 3 && !text.startsWith("1:")) throw new AssertionError("Next train was not promoted: " + text);
                    samples.add(text);
                    return Unit.INSTANCE;
                }, widgetId -> { refreshTimes.add(System.currentTimeMillis()/1000); return Unit.INSTANCE; },
                    () -> { idle[0] = true; return Unit.INSTANCE; }, new android.os.Handler(android.os.Looper.getMainLooper()));
                ticker[0].start();
            });
            SystemClock.sleep(8500);
            driver.runOnMainSync(() -> {
                if (!samples.contains("Leaving")) throw new AssertionError("Zero transition not observed: " + samples);
                if (refreshTimes.size() != 1 || refreshTimes.get(0) != departure) throw new AssertionError("Expected one prediction refresh at zero: " + refreshTimes);
                if (!idle[0] || !samples.contains("Tap to Refresh")) throw new AssertionError("Ticker did not stop at end of live window");
            });
            return "PASS route invalidation, refresh at zero, promotion, live expiry: " + samples;
        } finally {
            driver.runOnMainSync(() -> { if (ticker[0] != null) ticker[0].stop(); });
            android.content.SharedPreferences.Editor edit = driver.getTargetContext().getSharedPreferences("bart_widget_prefs", 0).edit();
            for (String key : new String[]{"origin_", "terminal_", "deps_", "lastTrain_", "error_", "liveUntil_", "updated_", "boundary_"}) edit.remove(key + id);
            edit.commit();
            if (activity != null) { final Activity closing = activity; driver.runOnMainSync(closing::finish); }
        }
    }
}
