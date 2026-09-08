# ERG-RM

Minimal Android app that connects to an FTMS-compatible smart trainer (e.g. **Elite Direto**)
over Bluetooth LE and sends power targets in **ERG mode**, with a TrainerDay-inspired UI.
Today's workout is fetched automatically from **Intervals.icu**.

## Features

- BLE scanning and connection to the trainer via Fitness Machine Service (FTMS, UUID `0x1826`).
- ERG mode: request control, start, and send power targets (`Set Target Power`, op code `0x05`).
- Live data (power, cadence, heart rate) from Indoor Bike Data (`0x2AD2`).
- Optional standalone heart rate sensor: scan and connect to any standard BLE Heart Rate Service
  (`0x180D`) chest strap or arm band, independent of the trainer — its reading takes priority over
  whatever HR the trainer itself might forward.
- Fetch today's planned workout from Intervals.icu (`.zwo` format) and automatic conversion of
  targets from %FTP to absolute watts.
- Local workout library: on-request import of `.zwo`, `.erg` or `.mrc` files from a folder chosen
  with the system picker (also works with a folder synced by Google Drive, if the Drive app is
  installed — no OAuth setup needed).
- Workout execution: step progression, interpolated ramps, power profile chart with live
  power/HR/cadence traces (tap-to-zoom, Coggan power zones, zone-colored interval bars),
  indefinite auto-extension at the end of the plan, manual +5 minutes on the current interval,
  Start/Pause/Stop control, and a live intensity (%FTP) adjustment.
- BLE connection kept alive by a **foreground service** (with a persistent notification) so it
  isn't lost when the app goes to the background mid-ride; the screen stays on for the whole
  session (`FLAG_KEEP_SCREEN_ON`).

## Project structure

```
app/src/main/java/com/ergrm/trainer/
├── ble/          # BLE scanner + FTMS/ERG and heart rate connection handling
├── intervals/    # Intervals.icu REST client + event parsing
├── library/      # Local workout library (.zwo/.erg/.mrc import via Storage Access Framework)
├── workout/      # Workout model, .zwo/.erg parsers, execution engine
├── data/         # Settings persistence (DataStore)
├── service/      # Foreground service that keeps the BLE connection alive in the background
└── ui/           # ViewModel + Jetpack Compose screens
```

## Setup

On first launch, open **Settings** (top-right icon) and enter:

- Your Intervals.icu **API key** (Settings → Developer Settings on the site).
- Your **Athlete ID** (e.g. `i123456`).
- Your **FTP** in watts, used to convert the workout's targets (%FTP) into absolute watts.

Then, from the main screen, tap the devices icon to scan for and connect your Elite Direto (or
any other FTMS-compatible trainer), and optionally a standalone BLE heart rate sensor.

## Build

The project uses Gradle with the Android Gradle Plugin; open the folder in Android Studio
(Giraffe or later) and sync, or from a terminal with the Android SDK configured:

```
./gradlew assembleDebug
```

A GitHub Actions workflow (`.github/workflows/build-apk.yml`) also builds a debug APK on every
push to this branch and uploads it as a workflow artifact — useful in environments without a
local Android SDK.

## Runtime requirements

- Android 8.0 (API 26) or later.
- Bluetooth LE and location/Bluetooth permissions granted at runtime.
- Notification permission (Android 13+) to see the connection status in the foreground service's
  persistent notification — if denied, the connection is still protected, the notification is
  just not visible.
- Internet connection to fetch the workout from Intervals.icu.
