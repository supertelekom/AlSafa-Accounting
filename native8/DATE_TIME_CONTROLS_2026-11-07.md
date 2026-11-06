# Full-mode date/time controls

Implemented:
- Shared SafaDateTime date/time dialogs; dates stay ISO yyyy-MM-dd and times HH:mm. Picker-backed fields do not open the keyboard.
- Full maintenance intake: selectable intake date and time, saved separately from actual created_at; stable view IDs retain selected values on recreation.
- Invoice metadata date/time and maintenance settlement date/time use pickers in full mode only.
- Sales archive: date selectors and Today / This month / Previous month / All presets in full mode.
- Maintenance register: intake date range dialog, inclusive boundaries, presets and clear; filters persist on recreation.
- Financial review and partner-profit period controls: calendars and presets.
- MainActivity records the active work mode on resume so shared metadata/settlement/archive pages retain manual behaviour in simple mode. Fast sale, quick repair and simplified delivery posting are unchanged.

Validation:
- Kotlin/Android API boundary compilation covers the shared native control, MainActivity and maintenance/metadata code (not a complete Gradle/Compose/KSP build).
- SQLite fixture verifies inclusive start/end date filtering and clearing both bounds.
- git diff --check.

Scope boundaries:
- Collection, transfer, supplier payment, stock movements and daily closing still record execution time automatically. Their posting APIs do not expose independently editable transaction dates. No financial timestamps were rewritten, and no historical postings were replayed.
- Full sale creation/purchase drafts still use their existing automatic timestamps; adding user-selected financial posting dates across these repositories remains unimplemented and requires matching ledger/profit/closing semantics.
- This batch replaces the existing manual date inputs and adds full-intake/register selection. It is not a claim that every previously listed future transaction-date feature is complete.

Tablet checks:
1. Full intake: choose a date/time, save, reopen details and compare.
2. Full metadata editor/maintenance return: calendar and clock open without keyboard; cancel keeps previous value.
3. Sales/maintenance archive: test Today, previous month and inclusive range boundaries; clear to all records.
4. Profit reports: choose a month and verify exact dates before display.
5. Switch to simplified mode: quick sale/repair retain automatic time and no extra fields; shared metadata editor keeps prior input behaviour.

Files: stylekit/SafaDateTime.kt (new); MainActivity.kt; maintenance/MaintenanceActivity.kt, MaintenanceDao.kt, MaintenanceIntakeActivity.kt, MaintenanceReturnActivity.kt; sales/InvoiceMetadataActivity.kt, SalesUi.kt, SalesArchiveActivity.kt, FinancialReviewActivity.kt, PartnerProfitsActivity.kt.
