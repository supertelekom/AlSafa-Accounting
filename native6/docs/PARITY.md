# Legacy parity register

Source: v3.9.353; 944 declared functions, 44 static navigation routes, 48 forms, 165 named fields. Static inventory covers the full file. Detailed semantic review has focused on sale, return, purchase, vault, backup and homepage paths; full business-rule audit is NOT finished.

| Legacy route | Native 6 status |
|---|---|
| advanced-home | Native navigation to implemented operations |
| audit-log | Pending migration; no dummy screen added |
| customer-orders | Pending migration; no dummy screen added |
| customers | Pending migration; no dummy screen added |
| daily-close | Count snapshot and difference; carry/reconciliation workflow pending |
| dashboard | Pending migration; no dummy screen added |
| debts | Pending migration; no dummy screen added |
| expense-form | Pending migration; no dummy screen added |
| expenses | Pending migration; no dummy screen added |
| father-deliveries | Pending migration; no dummy screen added |
| father-home | Native quick menu; handover pending |
| father-repair | Pending migration; no dummy screen added |
| father-sale | Pending migration; no dummy screen added |
| inventory-analytics | Pending migration; no dummy screen added |
| inventory-movements | Pending migration; no dummy screen added |
| maintenance-parts | Basic catalog only |
| modern-hub | Pending migration; no dummy screen added |
| more | Pending migration; no dummy screen added |
| opening-balances | Pending migration; no dummy screen added |
| partners | Pending migration; no dummy screen added |
| printer | Pending migration; no dummy screen added |
| products | Create/edit/search; images and variants pending |
| purchase-form | Pending migration; no dummy screen added |
| purchases | Pending migration; no dummy screen added |
| repair-form | Receive without media |
| repairs | Receive/ready/labor invoice; parts and full state machine pending |
| reports | One-month accrual profit, actual-date withdrawals; partner allocation pending |
| returns | Partial/full sale returns and separate refund; exchanges pending |
| sale-form | Same-currency store sale, discount, debt and cash; advanced options pending |
| sales | List, collection, returns, cancellation, native PDF |
| search | Pending migration; no dummy screen added |
| security | Pending migration; no dummy screen added |
| settings | Backup section only |
| shift-handover | Pending migration; no dummy screen added |
| shortcut-settings | Pending migration; no dummy screen added |
| stock-alerts | Pending migration; no dummy screen added |
| today-center | Pending migration; no dummy screen added |
| used-watch-exchange | Pending migration; no dummy screen added |
| used-watch-purchase | Pending migration; no dummy screen added |
| used-watches | Pending migration; no dummy screen added |
| vaults | Balances, transfer, expense, withdrawal and FX |
| wallet | Pending migration; no dummy screen added |
| warranties | Pending migration; no dummy screen added |
| watch-advisor | Pending migration; no dummy screen added |

## Release blockers

- Full legacy data import: only lossless isolated staging exists; no active financial migration.
- Legacy design parity: original palette and typography reused; full layout/behavior parity remains.
- Offline equivalents for former online features (brand lookup, website, WhatsApp handoff); no network permission will be added.
- Camera, photo gallery, barcode/QR printing, per-color product data and funding lots.
- Online collection recognition, taxes, mixed-currency receipt/change and cross-currency stock valuation.
- External partners, dated share schedules, loyalty, reservations, used-watch exchanges, warranties and inventory sessions.
- Repair parts, repair credit notes, purchase cancellation and exchanges.
- Full restoration/recovery workflow into an occupied database; currently rejected without changing data.
- Real tablet UI/end-to-end acceptance and release signing.

No real shop backup has been opened or changed in this work.
## Next implementation batch

Priority: complete partner share schedules, month-isolated allocations and withdrawals by actual transaction date; supplier opening debt and dated settlements; then multi-currency receipts and repair parts. Every change must preserve append-only per-currency balanced journals and atomic SQLite transactions. Do not migrate live data until explicit approval. Tests are the final verification stage, not a substitute for feature completion.
