# Native 6 development build validation — 2026-10-20

- Build: `:app:assembleDebug :app:testDebugUnitTest` — SUCCESS.
- Tests: 12; failures: 0; errors: 0.
- APK: AlSafa-Native6-6.0.0-dev01.apk
- SHA-256: 78fdd2e4b29837d1b6d467f377c1bdf01eb455a2f98ee2de9e773b90df6e3142
- Package: com.alsafa.native6; version 6.0.0-dev01; min SDK 26; target/compile SDK 34.
- APK signature: Android development/debug key, v2 verification passed.
- Manifest: large/xlarge tablet screens, RTL; no Internet permission.
- Source check: no android.webkit or com.google.firebase imports.
- Original Tajawal font and launcher artwork retained.

Verified cases: atomic failed multi-item sale; duplicate command; debt-first return and separate refund; cumulative discount rounding; currency isolation/overdraft; supplier payable and payment; actual-month withdrawal; backup round trip/existing-data rejection; closing does not reset balance; money precision/unbalanced currencies; exact weighted inventory carrying value; native homepage startup in Robolectric.

This is NOT a shop production release. No emulator/physical tablet acceptance was performed. Full legacy migration, full design/feature parity and production signing remain outstanding; see PARITY.md. The raw legacy source and Native 5 were not modified. No live store data was accessed.
