# Privacy Policy

**Last updated:** November 17, 2026

This Privacy Policy describes how **ForkyHome** (`dev.azagra.forkyhome`), developed by **azagramac**, handles user information and device permissions in compliance with the **Google Play User Data Policy**.

---

## 1. Developer Identity & Contact

* **Application:** ForkyHome (`dev.azagra.forkyhome`)
* **Developer:** azagramac
* **Repository & Support:** [https://github.com/azagramac/aosp-launcher](https://github.com/azagramac/aosp-launcher)
* **Privacy Contact:** Open an issue or contact the maintainer via the official GitHub repository above *(or add your direct support email before publishing to Google Play)*.

---

## 2. Data Collection, Use, and Sharing

**ForkyHome does not collect, store on external servers, sell, or share any personal or sensitive user data.**

* **Zero Network Access:** The application does **not** request or use the `INTERNET` permission. No analytics, crash reporters, advertising SDKs, third-party AI integrations, or telemetry libraries are included in the app.
* **No Third-Party Sharing:** Because no data ever leaves your device, no data is shared with third parties.

---

## 3. Permissions & Local Data Access

Any sensitive permission or system API used by the launcher operates **strictly locally on your device**:

* **Calendar (`READ_CALENDAR`, optional runtime permission):**
  * **Purpose:** Used solely to display your next upcoming calendar event in the **At a Glance** home screen header when you explicitly enable calendar events and grant permission.
  * **Handling:** Event titles and start times are read directly from Android's local `CalendarContract` provider in memory for display only. They are never saved by the launcher, transmitted, or shared.
* **Biometrics & Device Credentials (`USE_BIOMETRIC`):**
  * **Purpose:** Used to authenticate you via the native Android `BiometricPrompt` before opening apps you marked as **Protected** or accessing the **Privacy** settings dialog.
  * **Handling:** Authentication is handled entirely by the Android OS. ForkyHome never accesses, reads, or stores biometric data, fingerprints, or PINs.
* **Installed Apps & App Shortcuts (`LauncherApps` / `<queries>`):**
  * **Purpose:** Used locally to list installed applications, render icons (including installed icon packs), and display app shortcuts.
  * **Handling:** Rendered icon bitmaps are cached locally in the app's private cache directory (`cacheDir/icon_cache`).
* **Package Uninstall Requests (`REQUEST_DELETE_PACKAGES`):**
  * **Purpose:** Allows you to initiate the standard Android system uninstallation dialog from an app's long-press context menu.
* **Notification Panel (`EXPAND_STATUS_BAR`):**
  * **Purpose:** Allows the optional swipe-down gesture on the home screen to expand the system notification shade.

---

## 4. Data Storage, Retention & Deletion

* **Local-Only Storage:** Your launcher layout, preferences, hidden apps list, and protected apps list are stored exclusively on your device using Jetpack DataStore Preferences.
* **No Cloud Backups:** `android:allowBackup="false"` is set in the manifest so launcher preferences and layouts are not uploaded to cloud backups.
* **No User Accounts:** The app does not require or support account creation.
* **Data Deletion:** You can delete all locally stored launcher data at any time by clearing the app's storage in Android Settings or uninstalling the application.

---

## 5. Children's Privacy

ForkyHome does not collect personal information from anyone, including children under the age of 13.

---

## 6. Changes to This Privacy Policy

Any updates to this Privacy Policy will be published in this document with an updated revision date.
