# Native 6 verification

The tablet application uses a local SQLite database. Do not use a production store backup as a test database.

## Automated checks

From the `native6` directory with Android SDK 34 and JDK 17 installed:

```bash
bash ./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

The branch workflow `.github/workflows/native6-verify.yml` runs the same commands and uploads test reports. An APK is published as a workflow artifact **only if** both tests and compilation succeed.

Debug APK path after a successful local build:

`app/build/outputs/apk/debug/app-debug.apk`

Test reports after execution:

`app/build/reports/tests/testDebugUnitTest/index.html`

## Accounting acceptance checks

- Cash and journal balances are kept separately for USD, TRY, and SYP.
- A return against an unpaid sale reduces receivables, not cash.
- A return against a paid sale creates a refund liability, not a new customer debt.
- A partially paid sale return first clears remaining debt; only the excess is refundable.
- A refund cannot exceed its recorded liability on the selected date or any later date.
- Rejected operations must leave journal, stock, cash, and invoice state unchanged.
- Historical audits for cash, receivables, and refund liabilities must not report negative day-end balances in normal scenarios.
- Reports and audits must not mutate the store database.

## Release gate

A passing debug build does not by itself authorize use with live shop records. Validate restoration from a copy of the latest backup, compare invoice totals, inventory, supplier balances, and partner reports, then perform tablet acceptance testing before signing a release APK.
