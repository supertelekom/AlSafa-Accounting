# AlSafa Native 6

Kotlin / Android SDK, tablet only, offline SQLite. Independent application ID and database; never opens the legacy or Native 5 database. No network permission, synchronization, Firebase, or WebView.

Reference: v3.9.353 PHONE-SUPPLIER-PAYMENT-RECOVERY source archive. `docs/legacy-inventory.json` inventories every declared function and static route/form/field in main.js; it is a migration checklist, not a claim that all features are implemented. Native5 a2ae1a1 accounting principles reviewed: exact minor units, per-currency balanced immutable journals, atomic inventory/posting, credit notes separated from refunds.

Development build only. Not approved for shop deployment. Feature parity and final device validation are mandatory before release.

Build with JDK 17 and Android SDK 34: `./gradlew :app:assembleDebug :app:testDebugUnitTest`. Gradle 8.9 wrapper is pinned with SHA-256.

Implemented and pending work are listed in `docs/PARITY.md`; posting rules are documented in `docs/ACCOUNTING.md`. Backup export uses the Android document picker, including Google Drive when provided by an installed app. Drive uploading itself requires connectivity; all accounting remains offline.

## Windows / Android Studio

**دليل عربي:** [`docs/START_WINDOWS_AR.md`](docs/START_WINDOWS_AR.md) — خطوات التثبيت والفتح والبناء على Windows 10.

Open the `native6` directory in Android Studio. See [`docs/WINDOWS_BUILD.md`](docs/WINDOWS_BUILD.md) for SDK 34 setup and run `build-windows.bat` to assemble the debug APK and run local unit tests. No GitHub Actions required.
