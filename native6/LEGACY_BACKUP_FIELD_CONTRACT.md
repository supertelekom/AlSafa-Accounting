# Legacy backup field contract (verified 2026-10-28)

Source: AlSafa-Backup-2026-10-26T06-21-00-520Z.json, format alsafa-accounting-backup v2.

Do not rename legacy payload fields during import. Store original JSON unchanged in legacy_records. Display aliases are allowed only in read-only view models.

| Section | Records | Identity | Transaction date | Document number | Amount | Relationship |
| --- | ---: | --- | --- | --- | --- | --- |
| sales | 111 | id | date | invoiceNo | total, paid | items |
| purchases | 8 | id | date | supplierInvoice | total, paid | supplierId, items |
| payments | 36 | id | date | — | amount | referenceType, referenceId |
| cashTransactions | 722 | id | date | — | amount | referenceType, referenceId |
| supplierLedger | 2 | id | date | — | amount | supplierId |
| partnerWithdrawals | 72 | id | date | — | amount | partnerId |
| dailyClosings | 34 | id | date | — | drawerActual etc. | — |
| returns | 4 | id | date | invoiceNo | debtReduction, refundAmount | referenceType, referenceId |

All listed records have nonempty `date` and `id`; no duplicate IDs were found within any listed section in this sample.

**Critical distinction:** `date` is the legacy transaction date string (YYYY-MM-DD). `createdAt` is an epoch-milliseconds timestamp and must never silently substitute for `date` in financial period reports.

**Invoice fields:** Sales use `customer`, `phone`, `invoiceNo`. Purchases use `supplierId`, `supplierName`, `supplierInvoice`. Do not assume purchases contain `invoiceNo`.

**Accounting cutover:** Archived financial records are historical and read-only until their ledger impact, cash balances, cost basis, outstanding debts and opening equity are reconciled. Never replay historical transactions into opening balances automatically. This document describes the sampled backup, not every possible legacy version.
