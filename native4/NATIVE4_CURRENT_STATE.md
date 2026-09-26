# AlSafa Native 4 — Current implementation checkpoint

Updated: 2026-09-27
Branch: `native4-core-migration`

## Verified working baseline
- Build #145 proved end-to-end sync for existing SALE and REPAIR operations.
- Local-first path: SQLite -> durable Outbox -> Firebase Auth -> Firestore -> CONFIRMED.
- Firestore `(default)` exists in project `alsafa-native`; rules deployment succeeded.
- Existing SALE/REPAIR operationIds were preserved; no re-entry was required.
- Keep the stable Native 4 test signing key for updates; do not require uninstall between test builds.

## Safety invariants — do not regress
1. Local business data is authoritative. Cloud sync must never replace the complete local database.
2. Never delete local products merely because cloud data is absent.
3. Business transaction + related stock/cash effects + Outbox enqueue must be atomic where applicable.
4. Outbox operations are durable and retryable; preserve operationId for retries/idempotency.
5. Database migrations are additive. No DROP/recreate of business tables during upgrades.
6. Do not disturb the currently working Alpha SALE/REPAIR sync path while migrating modules to CoreDb.

## Daily closing implemented in core
- `CoreDb` is DB version 10.
- `DailyClosingStore` has explicit USD / TRY / SYP values.
- For each currency closing persists:
  - expected amount
  - counted amount
  - carry amount intentionally left for next day
  - drawer-out = counted - carry
- Carry cannot be negative or greater than counted.
- One open work day at a time; the same day cannot be closed twice.
- Expected amount is opening carry + MAIN drawer movements for that work day only.
- WORK_DAY_OPEN and DAILY_CLOSING are queued to Outbox.
- JVM tests cover carry preservation, current-day movements, drawer-out math, and invalid carry values.
- Workflow run #149 for commit `e8fe28d1` passed JVM tests, APK build and artifact upload.

## Current UI migration decision
- `MainActivity` still uses `OutboxDb` for the proven Alpha products/sales/repairs path.
- Do NOT replace that `db` object globally yet.
- Add a separate `CoreDb` + `DailyClosingStore` connection for CLOSING first.
- Existing `HomeSection.CLOSING` is the target navigation section.
- Current layout has no actual closing controls yet.

## Next implementation steps
1. Add a dedicated closing panel in `activity_main.xml`, hidden unless `HomeSection.CLOSING` is selected.
2. Add business-day field and controls to open a work day with opening carry USD/TRY/SYP.
3. Display expected USD/TRY/SYP from `DailyClosingStore.expected()`.
4. Add counted and carry inputs per currency.
5. Display drawer-out automatically as counted - carry; do not convert currencies automatically.
6. Close the day through `DailyClosingStore.close(...)` only.
7. After close, expose next-day opening using only the saved carry values; never the full counted balance.
8. Keep closing writes atomic and queued to Outbox.
9. Add/extend JVM tests before distributing an APK.
10. Build and test the full cycle: open -> movements -> expected -> counted -> carry -> close -> next-day carry.

## Longer-term direction
Prefer official Android/Firebase components for generic infrastructure where migration risk is justified (e.g. Room/WorkManager), but do not rewrite a verified working subsystem merely for architectural purity. Migrate incrementally with tests and preserved local data.
