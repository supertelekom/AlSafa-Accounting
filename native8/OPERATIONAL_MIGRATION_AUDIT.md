# Native 8 — Operational database audit (pre-migration gate)

Target branch: native6-development. Scope: native8/ only.
Database file: alsafa_native8_operational.db.
This document is an audit, NOT a completed migration or build certification.

## Direct OperationalDatabase construction sites
- ProductsActivity.kt
- ProductEditorActivity.kt
- ProductColorEditorActivity.kt
- ProductStockActivity.kt
- ProductDetailsActivity.kt
- RestoreActivity.kt
- sales/SalesEngine.kt
- sales/SalesProductCatalog.kt
- sales/LegacyReceivablesCollector.kt
- sales/AppDatabase.kt

## Classes holding an OperationalDatabase dependency
- data/operational/OperationalProductRepository.kt
- data/operational/ProductEditRepository.kt
- data/operational/ProductImportPromoter.kt
- data/operational/ProductSnapshotAudit.kt
- data/operational/ProductStockRepository.kt
- data/operational/ProductVariantRepository.kt
- sales/LegacySalesArchiveImporter.kt

## Additional database owners / risks
- sales/SalesDatabase.kt owns alsafa_native8_sales.db separately.
- data/migration/LegacyStagingDatabase.kt is a separate staging database; it must not be confused with the operational file.
- OperationalDatabase is SQLiteOpenHelper version 5; AppDatabase is Room version 5 and opens the same operational file.
- AppDatabase currently pre-opens OperationalDatabase and runs LegacySalesArchiveSchema.ensure() before Room opens.
- SQLite v5 uses inline UNIQUE(number) on sales and inline UNIQUE(parent_source,parent_id,color_name) on product_variants_local; Room declares indexes separately.
- product_variants_local.color_name uses COLLATE NOCASE; this must be preserved.
- product_stock_movements is immutable historical data and must not be reconstructed from balances.
- Existing v5 files may not have Room's identity table. Validate adoption on a copy of a real file before activation.

## Required release gates before enabling Room v6
1. Map all schema tables, indexes, FKs, defaults and collations against actual SQLite PRAGMAs.
2. Provide a migration from every supported on-device user_version (not only 5 if earlier files exist).
3. Ensure legacy archive tables are present and mapped before Room identity validation.
4. Preserve all row counts, invoice numbers, receivables, inventory movements and product variants.
5. Test a real v5 database copy and a fresh installation with Room schema validation.
6. Replace all operational SQLiteOpenHelper call sites with Room-backed paths before switching version.
7. Confirm Gradle compile and runtime migration tests; do not use fallbackToDestructiveMigration.
8. Only then enable AppDatabase version 6 and retire the old helper.

Status: inventory completed for the listed Kotlin sources; no schema migration has been activated.
