# Simplified home single row — 2026-11-06

User rejected the two-plus-one arrangement and requested three adjacent, more attractive cards. This supersedes the prior lower centered delivery tile.

Modified: app/src/main/java/com/alsafa/native8/stylekit/SimplifiedHomePage.kt.
Created: SIMPLIFIED_SINGLE_ROW_2026-11-06.md.

All three actions now use one centered RTL row: sale on right, repair in center, delivery on left. Maximum group width 572dp gives 180dp squares separated by 16dp. Rounded 24dp corners, quiet cream/sand/sage gradients, circular icon badges, subtle elevation and touch ripple. Labels retain Tajawal. Existing routes and other pages unchanged. No financial or database changes.

Validation: git diff --check passed. Tablet appearance and full Gradle build not run. No accounting tests repeated for this display-only change. The next device check is the one-row appearance and all three navigation actions.

Kotlin Android-facing smoke compilation passed (exit 0), including actual stylekit with stubbed service boundaries. Not a full Android/Room build.
