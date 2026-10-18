# Legacy AlSafa accounting migration — acceptance gates

## Source of truth
The current production AlSafa backup, exported immediately before cutover, is the source of shop data. Native 5's own SQLite database is the destination. Never restore a legacy SQLite/JSON file over the Native 5 database or copy its tables without an explicit mapping.

## Mandatory migration stages
1. **Inspect** an actual production-format backup, identify its container type, files, schema/version and encodings; calculate SHA-256 before doing anything else. The existing tablet ZIP inspector is read-only and does not establish compatibility. Other formats need a dedicated inspector.
2. **Map** each source entity and ID to a documented Native 5 destination: products and variants, barcode and images, quantities, repairs and parts, sales and receipts, suppliers and purchases, debts and collections, customers, cashboxes and balances by currency, transfers, expenses, daily closing and carryover, partners and owner drawings, and configuration.
3. **Stage** parsed records in a separate isolated temporary database. Validate duplicate identifiers, references, currencies, totals, inventory counts, historical cash and supplier balances. Generate a report of accepted, rejected and unmatched records; no writes to the live accounting tables.
4. **Reconcile** with the old app: record counts and financial balances per currency and vault, outstanding receivables/payables, inventory quantities, purchase costs, prior-period realized profit and online deliveries awaiting collection. No implicit FX conversion and no re-recognition of historical profit.
5. **Approve** a preview explicitly before one-time atomic import into a fresh Native 5 database. Import must be idempotent and auditable with source backup fingerprint and source IDs. A failed import must not leave half-imported financial data.
6. **Cut over** only after offline operation and upload-only Firebase tests on the actual tablet. Keep the original backup and old application available for rollback. Never send legacy synchronization state to Firebase as new transactions.

## Current implementation status
- ZIP safety inventory with entry hashes: implemented, untested on an actual production backup.
- Tablet file picker and read-only inventory UI: implemented, Android build/device test pending.
- Real backup schema discovery: pending receipt or access to an actual backup/source export implementation.
- Entity mapping, staging, reconciliation, atomic import and cutover: NOT implemented.

**Safety rule:** A file merely passing ZIP inspection MUST NOT enable an Import button.
