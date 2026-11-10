package com.transitopia.bartwidget;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import androidx.core.graphics.ColorUtils;
import java.io.File;
import java.io.FileOutputStream;

/** Inflates the real RemoteViews, including the host's parcel boundary and night resources. */
public class WidgetLayoutInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            String behavior = WidgetBehaviorInstrumentation.runChecks(this);
            result.putString("behavior", behavior);
            runOnMainSync(() -> {
                for (int mode : new int[]{Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES}) {
                    Configuration config = new Configuration(getTargetContext().getResources().getConfiguration());
                    config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | mode;
                    Context context = getTargetContext().createConfigurationContext(config);
                    for (int width : new int[]{240, 300, 360}) {
                        RemoteViews remote = new RemoteViews(context.getPackageName(), R.layout.bart_widget_layout);
                        remote.setTextViewText(R.id.eta_next, "4:32");
                        remote.setTextViewText(R.id.eta_second, "12 min");
                        remote.setTextViewText(R.id.eta_third, "20 min");
                        for (int id : new int[]{R.id.eta_second, R.id.eta_third, R.id.sep1, R.id.sep2}) remote.setViewVisibility(id, View.VISIBLE);
                        android.os.Parcel parcel = android.os.Parcel.obtain();
                        remote.writeToParcel(parcel, 0); parcel.setDataPosition(0);
                        RemoteViews received = RemoteViews.CREATOR.createFromParcel(parcel); parcel.recycle();
                        View view = received.apply(context, new FrameLayout(context));
                        float density = context.getResources().getDisplayMetrics().density;
                        int w = Math.round(width * density), h = Math.round(96 * density);
                        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
                        view.layout(0, 0, w, h);
                        View cog = view.findViewById(R.id.settings_button);
                        int right = w - cog.getRight(), bottom = h - cog.getBottom();
                        if (Math.abs(right - bottom) > 1 || Math.abs(right - 10 * density) > 1) throw new AssertionError("Cog insets: " + right + ", " + bottom);
                        View third = view.findViewById(R.id.eta_third);
                        View row = (View) third.getParent();
                        if (third.getRight() > row.getWidth() || row.getBottom() > h) throw new AssertionError("ETA clipped at width " + width);
                        double contrast = ColorUtils.calculateContrast(context.getColor(R.color.widget_text), context.getColor(R.color.widget_background));
                        if (contrast < 4.5) throw new AssertionError("Low text contrast: " + contrast);
                        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        view.draw(new Canvas(bitmap));
                        try (FileOutputStream out = new FileOutputStream(new File(getTargetContext().getCacheDir(), "widget-" + mode + "-" + width + ".png"))) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        } catch (Exception e) { throw new RuntimeException(e); }
                    }
                }
            });
            result.putString("stream", behavior + "\nPassed: RemoteViews inflation, 3 ETAs fit at 240/300/360dp, light/dark contrast, equal 10dp cog insets.\n");
            finish(-1, result);
        } catch (Throwable e) {
            result.putString("stream", e.toString()); finish(0, result);
        }
    }
}
