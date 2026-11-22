<p align="center">
  <img src="https://github.com/user-attachments/assets/40396efd-92da-412e-9ad2-bb43c99a80bf" width="128" alt="icon" />
  <h1 align="center">ForkyHome</h1>
  <h3 align="center">Clean. Fast. Private. Open source.</h3>
</p>

<p align="center">
  A lightweight, minimalist and high-performance Android launcher built with <strong>Kotlin</strong> and <strong>Jetpack Compose</strong>, featuring <strong>Material 3 / Material You</strong>.
  <strong>No ads. No tracking. No network access.</strong>
  <br />
  Just your home screen.
</p>

## About

ForkyHome is an open-source Android home screen launcher inspired by the simplicity and philosophy of AOSP.

Designed around performance, privacy and simplicity, it provides a clean and distraction-free experience without unnecessary features, advertisements or background network activity.

Built with modern Android technologies, ForkyHome combines a minimalist interface with advanced customization, native widgets, icon packs, notification badges and smooth interactions.

**Key principles:**

- **Minimalist:** A clean interface focused on what matters.
- **Fast:** Optimized rendering and responsive interactions, designed for high-refresh-rate displays.
- **Private:** No Internet permission, telemetry, analytics or remote requests.
- **Ad-free:** No advertisements or unwanted distractions.
- **Open source:** Licensed under GPL-3.0.
- **Customizable:** Home screens, folders, widgets, icons and layouts.

---

## Screenshots

<p align="center">
  <img src="https://github.com/user-attachments/assets/9d1ea2aa-fd9a-4bad-bba4-53e6c9d412a0" width="22%" alt="Home Screen" />
  <img src="https://github.com/user-attachments/assets/34828ccd-6434-4b7f-998a-b2c1459cc391" width="22%" alt="Settings" />
  <img src="https://github.com/user-attachments/assets/ac82b20b-e269-4484-a174-189fbe608e7a" width="22%" alt="General Settings" />
  <img src="https://github.com/user-attachments/assets/7092258b-abd8-4b44-8c90-487fd391ff31" width="22%" alt="About" />
</p>

---

## Features

### Home Screen, Dock & Widgets

- **Configurable Workspace:** Dynamic home pages with automatic compaction and configurable grids (4–6 columns × 4–7 rows).
- **Customizable Dock:** Pin up to 5 favorite applications.
- **Real-Time Drag & Drop:** Move applications between the drawer, home screen, dock and folders. Supports automatic page switching through edge zones.
- **Folders:** 2×2 icon previews, floating Material 3 folder interface, inline title editing and drag-out support.
- **Native Widgets:** Add, configure, move and resize Android widgets using the native `AppWidgetHost` API.
- **At A Glance:** Optional header displaying the current date, next scheduled alarm, upcoming calendar event and battery level or charging state.
- **Notification Badges:** Optional numeric notification counter on app icons across the home screen, dock, open folders and app drawer, displaying the exact active notification count (supporting both grouped and summary-only notifications).
- **Desktop Controls:** Lock the workspace, automatically add newly installed applications, toggle icon labels, adjust home icon scaling and customize home screen interactions.
- **Quick Notification Access:** Swipe down anywhere on the home screen to expand the notification shade.
- **Double-Tap to Sleep:** Optional screen-lock functionality through Android Accessibility services.

### App Drawer

- **Smooth Animations:** Finger-tracked opening and closing transitions with spring physics, haptic feedback and parallax effects.
- **Fast Scroller:** Material You alphabetical fast scroller with letter popup indicator and automatic fade-out.
- **Instant Search:** Filter installed applications in real time.
- **Customizable Layout:** Choose between 4 and 6 columns.
- **Icon Scaling:** Independently adjust application icon sizes from 80% to 120%.
- **Flexible Appearance:** Show or hide application labels.
- **Background Options:** System, Light, Dark and AMOLED Black backgrounds, with adjustable opacity from 50% to 100%.

### Icons & Icon Packs

- **Adaptive Icon Sizing:** Automatic icon sizing based on the selected grid layout.
- **Five Icon Shapes:** System Default, Circle, Squircle, Rounded and Teardrop.
- **Icon Pack Support:** Compatible with standard third-party Android icon packs.
- **Advanced Icon Pack Compatibility:** Supports `appfilter.xml`, `appmap.xml` and `theme_resources.xml`, including resource fallbacks.
- **Dynamic Calendar Icons:** Automatically refresh calendar icons at midnight and on date changes.
- **Icon Masking:** Supports `<iconback>`, `<iconmask>`, `<iconupon>` and `<scale>` fallback mechanisms.
- **Themed Monochrome Icons:** Material You monochrome icons on the home screen and dock.
- **Per-App Icon Overrides:** Customize individual application icons directly from the long-press menu.
- **Optical Icon Balancing:** Maintains consistent visual sizing between icon pack assets and system fallback icons.

### Application Shortcuts & Management

- **App Shortcuts:** Access dynamic and manifest-defined Android application shortcuts through long-press actions.
- **Application Information:** Open the native Android application information screen.
- **Uninstall Actions:** Access application removal through the native Android system interface.
- **App Visibility:** Hide applications from the app drawer without uninstalling them.
- **Backup & Restore:** Export and import your workspace layout, folders, dock, custom icons, and settings via Android's Storage Access Framework (supports plain JSON or optional AES-256-GCM password encryption, plus direct restore from onboarding on a clean install).

---

## Performance & Architecture

Built for responsive interactions, high-refresh-rate displays and efficient resource usage.

- **Hardware-Backed Disk Icon Cache:** Rendered application icons, including icon pack masks, dynamic calendar icons, monochrome themed icons and pre-clipped shapes, are cached in `cacheDir/icon_cache` and decoded as `Bitmap.Config.HARDWARE` (`AHardwareBuffer`), reducing bitmap uploads during fast scrolling.

- **Incremental Package Updates:** `ACTION_PACKAGE_ADDED`, `ACTION_PACKAGE_CHANGED` and `ACTION_PACKAGE_REMOVED` broadcasts update only affected applications instead of reloading the entire application catalog.

- **Pre-Clipped Icon Shapes:** `IconShape` masks are baked into cached bitmaps in `AppRepository`, avoiding repeated GPU clipping during app drawer scrolling.

- **Zero-Allocation Drawer Cells:** `DrawerAppIcon` avoids per-cell `pointerInput` coroutines, `onPlaced` observers and nested popup menus. A shared gesture detector and `AppActionsMenu` handle tap, long-press actions and drag-to-home.

- **Zero-Recomposition Drag & Drop:** Drag coordinates are isolated using `derivedStateOf` and `Modifier.graphicsLayer` translations. Home screen items animate using `Animatable` pixel offsets without triggering unnecessary re-layouts.

- **Asynchronous Loaders:** Widget provider lists, widget icons, installed icon packs and application shortcuts are loaded off the main thread using `Dispatchers.IO`.

- **Baseline Profile & R8:** Includes `app/src/main/baseline-prof.txt` and `androidx.profileinstaller` for AOT compilation of startup, `HorizontalPager`, `LazyVerticalGrid`, gesture and pointer interaction paths. Release builds also enable R8 minification and resource shrinking.

- **Adaptive High Refresh Rate:** Selects the highest supported display refresh rate for the current resolution. On Android 15+ (API 35+), enables `window.frameRateBoostOnTouchEnabled` and `View.REQUESTED_FRAME_RATE_CATEGORY_HIGH`.

### Measured Release Performance

Performance measurements obtained using:

```bash
adb shell dumpsys gfxinfo dev.azagra.forkyhome
```

| Device | Android | Frames | Janky Frames | UI p50 | UI p90 | UI p95 | UI p99 | Frames ≤ 8ms | GPU p50 / p99 | Slow Bitmap Uploads |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **OnePlus 8T** | Android 14 | 1,854 | 2.54% (47) | 6ms | 9ms | 13ms | 40ms | 88.9% | 2ms / 6ms | 0 |
| **Nothing Phone (2)** | Android 16 | 3,833 | 0.91% (35) | 5ms | 8ms | 9ms | 15ms | 93.7% | 3ms / 6ms | 0 |
| **Google Pixel 10 Pro XL** | Android 16 | 3,768 | 0.89% (31) | 4ms | 8ms | 8ms | 12ms | 94.5% | 2ms / 5ms | 0 |
| **Nothing Phone (2a)** | Android 16 | 4,241 | 0.09% (4) | 6ms | 10ms | 13ms | 36ms | 86.1% | 3ms / 6ms | 0 |

*Measurements from release builds running OxygenOS 14 and Nothing OS 4.1, respectively.*

### Native Android Integration

- Application launching through `LauncherApps.startMainActivity`.
- Native reveal animations using `ActivityOptions.makeClipRevealAnimation`.
- Android 15+ Predictive Back support through `android:enableOnBackInvokedCallback="true"`.
- Native Android widget integration through `AppWidgetHost`.
- On-device notification counting through `NotificationListenerService`.
- System biometric authentication through `BiometricPrompt`.
- Multi-user application discovery through `LauncherApps.profiles`.

---

## Privacy & Security

Privacy is a fundamental part of ForkyHome's architecture.

The launcher is designed to operate locally on your device without requiring network connectivity.

### No Network Access

- Does not declare `android.permission.INTERNET`.
- No telemetry or analytics.
- No remote requests.
- No integrated advertising or advertising SDKs.
- No remote crash reporting.

### Local Data Storage

- Application preferences are stored locally using Jetpack DataStore Preferences.
- Hidden application settings remain on the device.
- Icon cache files are stored locally.
- `android:allowBackup="false"` disables Android cloud backup for application data.
- Manual backups are saved locally through Android's Storage Access Framework (`BiometricPrompt`-gated on export, with optional AES-256-GCM + PBKDF2 password encryption).
- The hidden/protected apps screen enables `FLAG_SECURE` to block screenshots and screen recording.

### Protected Applications

Sensitive applications can be protected using the native Android `BiometricPrompt` API.

Supported authentication methods include:

- Biometric authentication (`BIOMETRIC_WEAK`).
- Device credentials (`DEVICE_CREDENTIAL`), including PIN, pattern and password.

Authentication is required before launching protected applications or changing their protection settings.

---

## Compatibility & Specifications

| Specification | Details |
|---|---|
| Application | ForkyHome |
| Package | `dev.azagra.forkyhome` |
| Minimum Android | Android 14 (API 34) |
| Target Android | Android 16 (API 36) |
| Compile SDK | API 36 |
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| Design System | Material 3 / Material You |
| Architecture | Native Android APIs |
| Languages | English and Spanish |
| License | GPL-3.0 |

### Multi-Profile Support

ForkyHome queries application activities across active Android user profiles through `LauncherApps.profiles`.

Supported environments include:

- Personal Profile.
- Android Work Profile.
- Android 15+ Private Space.

---

## Installation via ADB

You can install ForkyHome directly on your device using Android Debug Bridge (`adb`):

### Release Build (Recommended)

For daily use or to evaluate the full performance and smoothness of the launcher, install the **release** APK (built with `isDebuggable = false` and R8 code/resource optimization):

```bash
adb install dev.azagra.forkyhome-<version>.apk
```

To update an existing installation without losing your layout or settings:

```bash
adb install -r dev.azagra.forkyhome-<version>.apk
```

### Debug Build

The **debug** APK is intended for development and testing:

```bash
adb install dev.azagra.forkyhome-<version>-<versionCode>.apk
```

> **Note:** The debug build has `isDebuggable = true` enabled for the development environment (and runs without R8 optimization). This can noticeably degrade responsiveness, animations, and overall user experience, which is normal behavior. If you want to fully test the launcher, install the **release** build instead.

> **Note on `.aab` files:** Android App Bundle (`.aab`) artifacts are reserved exclusively for **Google Play Internal Testing** distribution and are not meant to be installed directly via `adb install`.

---

## Permissions & System Integration

ForkyHome uses Android system APIs to provide home screen functionality.

Some optional features require additional system permissions or user authorization:

| Feature | Android API / Permission |
|---|---|
| Application discovery | `LauncherApps` |
| Home screen widgets | `AppWidgetHost` |
| Notification badges | `NotificationListenerService` |
| Upcoming calendar events | `READ_CALENDAR` |
| Notification shade gesture | `EXPAND_STATUS_BAR` |
| Double-tap to sleep | `BIND_ACCESSIBILITY_SERVICE` |
| Protected applications | `USE_BIOMETRIC` (`BiometricPrompt`) |
| Application uninstall requests | `REQUEST_DELETE_PACKAGES` |
| Application package updates | Package broadcasts |
| Scheduled alarm display | `AlarmManager` |

Optional functionality may require explicit configuration or authorization through Android system settings.

---

## Technology Stack

- **Kotlin:** Primary application development language.
- **Jetpack Compose:** Declarative UI framework.
- **Material 3:** Modern Android design system.
- **Jetpack DataStore:** Local preferences and configuration storage.
- **Coroutines & Dispatchers:** Asynchronous operations.
- **AndroidX ProfileInstaller:** Baseline Profile installation.
- **R8:** Release optimization and code shrinking.
- **Native Android APIs:** Application management, widgets, biometric authentication and system integration.

---

## License

ForkyHome is free and open-source software distributed under the **GNU General Public License v3.0 (GPL-3.0)**.

See [LICENSE](LICENSE) for the complete license text.

---

<p align="center">
  <img src="https://developer.android.com/static/images/brand/android-head_3D.svg" width="96" alt="Android" />
  <h3 align="center">Powered by Android</h3>
</p>
