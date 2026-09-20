# WiFi Guardian — Wi-Fi Security Auditor

A privacy-first Android 10+ application for **authorized Wi-Fi security assessment**, visibility, education, and local reporting. It does not crack passwords, attack networks, inject packets, deauthenticate clients, create evil twins, intercept credentials, or bypass authentication.

## Technology

- Kotlin 2.4.10
- Android Gradle Plugin 9.4.1
- Gradle 9.7
- Jetpack Compose + Material 3
- Compose BOM 2026.09.00
- Room 2.8.5
- Coroutines + Flow
- MVVM with a lightweight application container
- Minimum SDK 29 (Android 10)
- Target/compile SDK 37

These versions were selected against the current Android/Jetpack release information available in September 2026. Android Studio Quail 4 supports AGP 9.4.

## Build

1. Open the `WiFiGuardian` folder in Android Studio Quail 4 or a compatible Android Studio release.
2. Let Gradle sync and install the Android SDK Platform 37 if requested.
3. Use JDK 17.
4. Run the `app` configuration on a physical Android 10+ device. A physical device is recommended because Wi-Fi scan results depend on device radio/OS behavior.
5. Build with **Build > Make Project** or `./gradlew assembleDebug`.

## Permissions and Android limitations

The app requests only permissions needed for Wi-Fi visibility/scanning:

- `ACCESS_WIFI_STATE`
- `CHANGE_WIFI_STATE`
- `ACCESS_NETWORK_STATE`
- `NEARBY_WIFI_DEVICES` on Android 13+
- `ACCESS_FINE_LOCATION` because Android's `startScan()` and `getScanResults()` still require it, including on current Android versions. Location services may also need to be enabled for scans.

Android explicitly documents scan throttling. `startScan()` can fail or return older results; the application never fabricates data and surfaces the failure/limitations instead.

The app declares `NEARBY_WIFI_DEVICES` with `neverForLocation`; it does not derive physical location from Wi-Fi APIs. Android 13+ introduced this permission for nearby Wi-Fi workflows, while scan APIs can still require `ACCESS_FINE_LOCATION`.

## Data model and privacy

- Wi-Fi scan results are processed locally.
- Scan history is stored only in the local Room database.
- Password analysis is entirely local; password input is not persisted, logged, transmitted, or sent to a server.
- Reports are generated locally.
- There is no analytics SDK or remote backend.

## Security scoring

The score is a **heuristic based only on observable configuration characteristics** such as advertised security capabilities, encryption, WPS advertisement, and observed channel congestion. It is not a penetration-test result and does not establish exploitability.

Important limitations:

- A scan cannot prove whether WPS is actually enabled; it can only identify an advertised WPS capability when present.
- WPA2/WPA3 labels do not prove the strength or secrecy of the password.
- Channel congestion is local and time-dependent.
- Hidden SSIDs/BSSID information can be unavailable because of OS/device restrictions.
- Firmware status, router admin configuration, UPnP state, guest isolation, and password quality cannot be inferred from a passive scan. The My Router Audit therefore uses user-supplied checklist information rather than pretending to inspect the router.

## Features

- Dashboard with current Wi-Fi details
- Real Android Wi-Fi scan results
- Network security analysis and severity-based findings
- Guided router self-audit
- Local password strength heuristics and secure random password generator
- Channel congestion visualization by observed networks
- Security education
- Local Room history
- Text and PDF report export
- Dark/light system theme support
- Arabic RTL-compatible Android resources

## Architecture

```
app/src/main/java/com/wifiguardian/
├── data/
│   ├── local/       Room database, entity, DAO
│   ├── repository/  Local history repository
│   └── wifi/        Android WifiManager + ConnectivityManager adapter
├── domain/
│   ├── model/       Domain models
│   └── usecase/     Password and router audit engines
├── presentation/    ViewModel, factory, report exporter
├── ui/theme/        Material 3 theme
├── GuardianApplication.kt
└── MainActivity.kt
```

## What this app deliberately does NOT do

No password cracking, credential theft, brute-force authentication attempts, packet injection, deauthentication, evil-twin creation, captive-portal credential collection, unauthorized access, stealth, persistence, or malware functionality.

## Verification note

The project is structured as a normal Android Studio Gradle project and the source has been reviewed for internal references. A full APK compilation requires an Android SDK/Gradle environment with the configured toolchain and network access to Maven repositories; this delivery environment does not contain a complete Android SDK, so an APK build cannot be truthfully claimed as executed here.

## Build the APK online — no Android Studio required

This repository includes GitHub Actions workflows that build the APK in the cloud.

### Quick method

1. Create a GitHub account at https://github.com if you do not already have one.
2. Create a new repository, for example `WiFiGuardian`.
3. Upload the **contents of this project folder** to the repository. The `.github/workflows/` folder must be included.
4. Open the repository on GitHub and select **Actions**.
5. Select **Build WiFi Guardian APK**.
6. Click **Run workflow**.
7. Wait for the workflow to finish successfully.
8. Open the completed workflow run and scroll to **Artifacts**.
9. Download `WiFiGuardian-debug-apk`.
10. Extract the downloaded artifact and install `app-debug.apk` on your Android phone.

The debug APK is signed automatically with the Android debug key and is suitable for personal testing.

### Release build

The repository also contains a `Build WiFi Guardian Release APK` workflow. It produces an unsigned release APK. For commercial distribution or Google Play, the application should be signed with your own private keystore. Never commit a private keystore or signing password to GitHub.

### Important

The cloud workflow intentionally does not require the Gradle wrapper JAR because the build environment installs Gradle 9.7 directly. This avoids a common failure where a ZIP contains `gradlew` and `gradle-wrapper.properties` but is missing `gradle-wrapper.jar`.
