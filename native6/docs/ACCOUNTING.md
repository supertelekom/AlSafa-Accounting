# Accounting decisions

- Money is signed Int64 minor units (2 decimals), parsed with BigDecimal; overflow and fractional minor units are rejected. Never sum unlike currencies.
- Each operation runs in one SQLite transaction. Failures roll back invoice, stock, journal and receipt together. A reused event UUID cannot post twice.
- Journals balance separately per currency and are append-only. Credit notes and cash refunds are distinct records. Full cancellation returns only outstanding quantities; historical invoice numbers remain.
- Store sales recognize revenue/cost on issue. Debtor collections do not recognize revenue again. Online sales recognition remains a migration blocker.
- Exact inventory carrying value is stored separately from displayed rounded unit cost. Weighted-average depletion uses cumulative remaining value; the last unit carries rounding remainder. Returns restore the original frozen invoice cost, never the current product price.
- Return discounts are allocated by cumulative proportions, preventing leakage across partial returns. Credit reduces remaining debt first, then creates a refundable liability. Refunds require available cash and cannot exceed that liability.
- Purchases credit a supplier payable; payments debit that payable. Prior supplier debt has an explicit separate opening transaction per currency.
- Transfers and actual currency exchanges are paired journal lines. FX clearing is not treated as operating revenue; base-currency translation and realized exchange differences are not implemented yet.
- Month reports select exactly one YYYY-MM. Withdrawals retain their actual date; no allocation to the oldest unpaid month. Partner profit shares are pending.
- Closing records expected/count/variance without silently resetting the ledger. Snapshot closing is not a period lock; reopening and period-lock rules remain pending.
- Native5 a2ae1a1 reviewed as secondary accounting reference: Money, JournalEntry, Native5DatabaseHelper and UncollectedOnlineCancellationService. Native6 ports the invariants to Kotlin, removes all cloud/outbox dependencies, and does not depend on Native5 modules.
- Backups export all tables in a consistent transaction with SHA-256 envelope. Restore validates a disposable database before atomically inserting into an empty database. Existing data is never replaced. SHA-256 detects corruption; it is not encryption or a digital signature.
