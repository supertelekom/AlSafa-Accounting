# Native 5 — mandatory parity and release acceptance

## Source of truth for UI
The production AlSafa Accounting v3.9.353 UI, **not** the Native4 prototype, is the design authority.
Native4 screens in this repository are only implementation references. Do not assert 1:1
visual parity until the production source/layouts and actual screenshots have been compared.
Keep production unchanged and do not copy its data or signing key into test builds.

## Required functional areas
- Home: simplified, advanced, advisor modes; Arabic RTL, Tajawal, established color and navigation patterns.
- Products: variants, multiple images, barcode, QR labels, brand REF lookup, pricing, stock, supplier.
- Sales: quick/full, mixed USD/TRY/SYP payments, drawer/Sham Cash/external, invoice PDF, WhatsApp, discounts, loyalty.
- Repairs: intake, photos, quick repair, parts, status, handover, payment, invoice, AlSafa Care.
- Purchases/suppliers: payable lifecycle, partial/full settlement, partner funding, audit trail.
- Cash: currency-specific balances, transfers, withdrawals, carry-forward, daily closing, reports and profit.
- Sync: tablet-only application; atomic local commit + durable outbox; idempotent Firebase backups; offline recovery; no phone client or phone synchronization; no destructive replacement.
- Backup and migration: explicit verification, counts, hashes and rollback plan; never replace production data on install.

## Release gates (all mandatory)
1. Compare each production screen against Native5 screenshots and verify navigation/actions.
2. Unit and Android SQLite instrumentation tests pass, including crash/rollback/duplicate/overflow.
3. Cloud rules, upload retry, tablet-only operation and cloud recovery tests pass.
4. Multi-currency posting, payment split, inventory and profit reconciliations pass.
5. Existing production backup imported into an isolated test database with audited counts.
6. Signed, installable APK built; smoke-tested on tablet without overwriting production.
7. Only then consider store migration, with explicit user approval.

## Current status
This is a release acceptance contract, not a claim of implementation. Native5 is currently
a foundation (core, SQLite adapters, outbox), with no complete UI, cloud transport or APK.
GitHub-hosted CI is paused due to exhausted private-repository minutes.

## Confirmed device scope (2026-10-17)
Native5 is for the store TABLET ONLY. No phone application, phone read-only client, phone sync, or two-device replication. Firebase is the tablet's durable cloud backup/sync endpoint, not a second device. Remove any phone-oriented code and requirements from Native5.
