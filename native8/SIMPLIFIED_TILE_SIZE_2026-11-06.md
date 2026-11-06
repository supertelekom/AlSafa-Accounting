# Compact simplified actions — 2026-11-06

User accepted fast sale, quick repair and ready-deliveries page appearance on device. Requested smaller home squares and deferred maintenance detail styling.

Modified: app/src/main/java/com/alsafa/native8/stylekit/SimplifiedHomePage.kt.
Created: SIMPLIFIED_TILE_SIZE_2026-11-06.md.

The action group is centered and capped at 374dp: two 180dp squares separated by 14dp, then a centered delivery square of the same size. Narrow windows use available width. Existing labels, icons, font, RTL, scroll host, destinations and all financial behavior are unchanged.

Validation: git diff --check passed. Device rendering and full Gradle build were not run. No accounting tests repeated for this layout-only change. User confirmation of the smaller dimensions remains pending. Historical delivery posting and original automatic printing remain outside this change.

Android-facing Kotlin smoke compilation passed (exit 0), using actual stylekit classes and stubbed service boundaries. This is not a Gradle/Room/device test. Existing deprecation warnings only.
