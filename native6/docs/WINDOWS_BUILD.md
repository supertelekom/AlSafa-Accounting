# Native 6 — build on Windows with Android Studio

## First-time setup

1. Install **Android Studio** from https://developer.android.com/studio.
2. In **SDK Manager**, install **Android SDK Platform 34**, **Android SDK Build-Tools 34.x**, and **Android SDK Platform-Tools**. Let Studio install its bundled JDK (17 or newer supported by Gradle 8.9 / AGP 8.7).
3. Clone the private repository using an authorized GitHub account, or download the **native6-development** branch ZIP and extract it.
4. In Android Studio select **Open** and choose the inner **native6** folder (the folder containing `settings.gradle.kts`). Do **not** open `native4` or the legacy WebView app.
5. Wait for **Gradle Sync** to finish; approve any requested SDK package licenses.
6. Double-click `native6/build-windows.bat` or run from Windows Terminal inside the `native6` directory:
   ```bat
   build-windows.bat
   ```
7. On success the debug APK is at `native6\app\build\outputs\apk\debug\app-debug.apk`. It is **not** a production release. Run all tablet acceptance checks before entering real store data.

## When the build fails

- Copy the **first error** and approximately 30 lines around it from **Build Output** or the command window. Avoid sharing credentials, keystores, or customer records.
- If Gradle cannot download dependencies, check internet/proxy in Android Studio; the wrapper downloads Gradle 8.9 from services.gradle.org, while Android plugins and SDK use Google/Maven repositories.
- If JDK is missing, select **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** and use the bundled JDK. The batch script tries Android Studio's bundled JDK automatically.
- If `SDK location not found`, set `ANDROID_HOME` to `%LOCALAPPDATA%\Android\Sdk` or create a local `local.properties` with `sdk.dir=C:\\Users\\YOUR_USER\\AppData\\Local\\Android\\Sdk`. Do not commit `local.properties`.
- Never use the production app's application ID, data folder, or signing key. Native 6 uses `com.alsafa.native6`, isolated from the live app.
- No Firebase and no internet permission are required for the app itself; Gradle needs internet for first-time dependencies.

## After building

Before store deployment, test fresh install, offline start, product creation, sale, debt collection, partial/full returns, supplier purchases/payments, repairs, multiple currencies, vaults, missed-day closing, backup and restore. Confirm accounting journals and balances for each step. Keep a separate test database and backup; never import live shop data during early validation.
