# Transitopia

An Android home-screen widget showing the next three BART trains between two stations, powered by BART's public GTFS-Realtime feed. No API key or account is required.

## Features

- Choose a boarding station and destination for each widget. Destinations can be intermediate stops; the train does not have to terminate there.
- Compact 3×1 layout with three ETAs, a direction-reversal button, and a settings cog.
- Below ten minutes, the next train uses Android's native countdown view to display seconds. Later trains show whole minutes.
- Tapping refresh, reversing direction, or saving widget settings fetches fresh data and starts a 15-minute live window. Further network refreshes are requested at +5 and +10 minutes.
- After the live window, the widget shows **Tap to Refresh**. Android may defer background work, particularly during power-saving modes.
- Settings show **Add Widget** for a new widget and **Update Widget** for an existing one.
- Garden T adaptive icon, including a monochrome layer, with matching light and dark app themes.

The **Last Train** label means only one matching trip remains in the current realtime feed. It is not a guarantee that this is the last scheduled train of the service day. Outside realtime coverage, the widget shows **No trains found**; there is no static-schedule fallback.

## Build and install

Requires JDK 17 and an Android SDK with platform 35. Minimum Android version: 8.0 / API 26.

Set `ANDROID_HOME` to your SDK location, or configure it in an untracked `local.properties` file.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Long-press the home screen, select **Widgets → Transitopia**, add the widget, and choose your stations. Launchers may retain an older widget's size after an update; remove and re-add it to use the current default size.

The debug build uses Android's local debug signing key. Signing keys, local SDK settings, generated APKs, build caches, and environment files are excluded from this repository.

## Device checks

With an Android emulator or device connected:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  com.transitopia.bartwidget.test/com.transitopia.bartwidget.WidgetLayoutInstrumentation
```

The device checks exercise RemoteViews inflation, native per-second countdown ticking without provider broadcasts, route-cache invalidation, light/dark text contrast, three-ETA fit at several widths, and settings-cog spacing. Unit tests cover trip parsing, directional/intermediate-stop matching, sorting, and ETA formatting.

## Data and matching

The app downloads [BART GTFS-Realtime TripUpdates](https://api.bart.gov/gtfsrt/tripupdate.aspx) and parses them with MobilityData's GTFS-Realtime protobuf bindings. BART's realtime platform stop IDs are mapped to parent stations using `app/src/main/assets/stations.json`, derived from [BART's static GTFS feed](https://www.bart.gov/dev/schedules/google_transit.zip).

Trips match when the origin appears before the destination in the reported stop sequence. Departures are filtered, deduplicated, sorted, and limited to the next three. Matching does not require a static trip-ID table.

## Project layout

- `app/src/main/java/com/transitopia/bartwidget/data/` — feed fetching, parsing, and matching
- `app/src/main/java/com/transitopia/bartwidget/widget/` — widget rendering, configuration, cached state, and refresh scheduling
- `app/src/main/java/com/transitopia/bartwidget/ui/` — garden color themes
- `app/src/main/res/` — adaptive icon, widget layout, and resources
- `app/src/test/` — unit tests
- `app/src/androidTest/` — device checks
- `design/transitopia-icon-ideas.html` — self-contained icon exploration page with SVG downloads; earlier rounds are retained alongside it
