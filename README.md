<p align="center">
  <img src="https://developer.android.com/static/images/brand/android-head_3D.svg" width="96" alt="Android" />
</p>

<h1 align="center">ForkyHome</h1>

<p align="center">
  A lightweight, high-performance Android home screen launcher built with <strong>Kotlin</strong> and <strong>Jetpack Compose</strong>, inspired by <strong>LineageOS Trebuchet</strong> and <strong>AOSP</strong>, designed with <strong>Material 3 / Material You</strong>.
</p>

---

## Compatibility & Specs

* **Package**: `dev.azagra.forkyhome`
* **Android Compatibility**: **Android 14 (API 34)** up to **Android 16 (API 36)** (`minSdk = 34`, `targetSdk = 36`, `compileSdk = 36`)
* **Multi-Profile Support**: Queries activities across all active user profiles (`LauncherApps.profiles`), including Work Profile and Android 15+ Private Space
* **Languages**: English (default) and Spanish (`values-es/`)
* **License**: GPL-3.0

---

## Performance & AOSP-Grade Architecture

Built to match the responsiveness and low power footprint of native AOSP launchers:

* **Hardware-Backed Disk Icon Cache**: Rendered app icons (including icon pack masks, dynamic calendar icons, monochrome themed icons, and pre-clipped shapes) are cached in `cacheDir/icon_cache` and decoded as `Bitmap.Config.HARDWARE` (`AHardwareBuffer`), eliminating per-frame bitmap uploads during fast flings.
* **Incremental Package Updates**: `ACTION_PACKAGE_ADDED`, `ACTION_PACKAGE_CHANGED`, and `ACTION_PACKAGE_REMOVED` broadcasts update only the affected package instead of reloading the entire app catalog.
* **Pre-Clipped Icon Shapes**: `IconShape` masks are baked into cached bitmaps in `AppRepository`, avoiding per-frame GPU clipping during app drawer scrolling.
* **120 Hz+ Zero-Allocation Drawer Cells (`DrawerAppIcon`)**: Drawer grid items avoid per-cell `pointerInput` coroutines, `onPlaced` observers, and nested popup menus; a single grid-level gesture detector and shared `AppActionsMenu` handle tap, long-press actions, and drag-to-home without coroutine churn on recycle.
* **Zero-Recomposition Drag & Drop**: Drag coordinates are isolated using `derivedStateOf` and `Modifier.graphicsLayer` translations, while home grid items animate using `Animatable` pixel offsets without triggering re-layouts.
* **Async Main-Thread-Safe Loaders**: Widget provider lists, widget icons, installed icon packs, and app shortcuts are loaded off the main thread on `Dispatchers.IO`.
* **Baseline Profile & R8**: Includes `app/src/main/baseline-prof.txt` with `androidx.profileinstaller` for AOT compilation of startup, `HorizontalPager`, `LazyVerticalGrid`, `gestures`, and `pointer` hot paths, plus R8 minification and resource shrinking on release builds.
* **Adaptive High Refresh Rate**: Selects the highest supported `refreshRate` display mode for the current resolution in `onCreate`/`onResume` and enables `window.frameRateBoostOnTouchEnabled = true` + `View.REQUESTED_FRAME_RATE_CATEGORY_HIGH` on Android 15+ (`API 35+`).
* **Measured Release Performance (`adb shell dumpsys gfxinfo dev.azagra.forkyhome`)**:

  | Device (Release) | Android | Frames | Janky Frames | UI p50 | UI p90 | UI p95 | UI p99 | Frames `<= 8ms` | GPU p50 / p99 | Slow Bitmap Uploads |
  | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | :---: | ---: |
  | **OnePlus 8T** | Android 14 (OxygenOS 14) | `1854` | `2.54%` (`47`) | `6ms` | `9ms` | `13ms` | `40ms` | `88.9%` (`1648/1854`) | `2ms` / `6ms` | `0` |
  | **Nothing Phone (2)** | Android 16 (Nothing OS 4.1) | `3833` | `0.91%` (`35`) | `5ms` | `8ms` | `9ms` | `15ms` | `93.7%` (`3590/3833`) | `3ms` / `6ms` | `0` |
  | **Nothing Phone (2a)** | Android 16 (Nothing OS 4.1) | `4241` | `0.09%` (`4`) | `6ms` | `10ms` | `13ms` | `36ms` | `86.1%` (`3652/4241`) | `3ms` / `6ms` | `0` |
* **Native System Integration**:
  * Launches apps via `LauncherApps.startMainActivity` with `ActivityOptions.makeClipRevealAnimation`.
  * Supports Android 15+ **Predictive Back** (`android:enableOnBackInvokedCallback="true"`).

---

## Privacy & Security

ForkyHome is designed with strict local-only privacy:

* **Zero Network Access**: Does **not** declare `android.permission.INTERNET`. No telemetry, analytics, crash reporting, or remote requests ever leave your device.
* **No Cloud Backups**: `android:allowBackup="false"` prevents launcher layout and preferences from being uploaded to cloud backups.
* **Hidden Apps**: Hide any app from the app drawer (persisted locally in Jetpack DataStore Preferences).
* **Protected Apps (`BiometricPrompt`)**: Lock sensitive apps behind native system authentication (`BIOMETRIC_WEAK` or `DEVICE_CREDENTIAL` PIN/pattern/password) before launching them or toggling their protection state.

---

## Features

### Home Screen, Dock & Widgets
* **Configurable Workspace**: Dynamic home pages (auto-compacted), configurable grid (`4–6` columns × `4–7` rows), and a bottom dock supporting up to **5** pinned apps.
* **Real-Time Drag & Drop**: Drag apps from the drawer, home screen, dock, or open folders directly onto grid cells, dock slots, or edge zones to switch pages automatically.
* **Trebuchet-Style Folders**: `2×2` preview grid icon, floating Material 3 folder overlay with debounced inline title editing, and drag-out support.
* **Native Widgets (`AppWidgetHost`)**: Add, configure, reconfigure, move, and resize (`spanX × spanY`) home screen widgets with automatic displacement of nearby icons.
* **At A Glance Header**: Optional top header displaying the current date, next scheduled alarm (`AlarmManager`), and battery level/charging state.
* **Desktop Controls**: Lock desktop layout, auto-add newly installed apps, toggle icon labels, swipe down anywhere to expand the notification shade (`EXPAND_STATUS_BAR`), and optional double-tap to sleep via `LockScreenService` (Accessibility).

### App Drawer
* **Spring Physics & Parallax**: Smooth finger-tracked open/close transitions with directional hysteresis and nested scroll integration.
* **Instant Search**: Lightweight pill search bar (`BasicTextField`) filtering visible apps in real time.
* **Customization**: `4–6` columns, independent icon scaling (`80%–120%`), label visibility toggle, and selectable background (`System`, `Light`, `Dark`, `AMOLED Black`) with adjustable opacity (`50%–100%`).

### Icons & Icon Packs
* **Adaptive Icon Sizing**: Automatic column-based sizing (`60dp` for 4 cols, `54dp` for 5 cols, `48dp` for 6 cols) with independent `80%–120%` scale sliders for Home and Drawer.
* **5 Icon Shapes**: System Default, Circle, Squircle, Rounded, and Teardrop.
* **Icon Pack Engine**: Compatible with standard icon packs (ADW, Nova, Lawnchair, Action, Apex, Go, Smart Launcher, CandyBar / Papirus) supporting `appfilter.xml` (`res/xml`, `res/raw`, or `assets`), `appmap.xml` and `theme_resources.xml` fallbacks, dynamic `<calendar>` icons with automatic midnight/date-change refresh, optical size balancing between pack icons and system fallbacks when a pack is active, freeform pack preservation, and `<iconback>`, `<iconmask>`, `<iconupon>`, `<scale>` fallback masking.
* **Themed Monochrome Icons**: Material You monochrome icon rendering on the home screen and dock using `AdaptiveIconDrawable.monochrome`.
* **App Shortcuts & Badges**: Long-press context menu with dynamic/manifest app shortcuts, app info, and uninstall actions. *(Note: `NotificationCounter` `NotificationListenerService` is included in source and commented out in `AndroidManifest.xml` by default for Android 15+ sideload Play Protect compatibility).*
