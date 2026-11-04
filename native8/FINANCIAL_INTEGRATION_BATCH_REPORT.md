# Financial integration — current verified implementation scope

Branch: native6-development. Changes restricted to native8/.

## Implemented in this batch
- MaintenanceDao.deliveredInPeriod(from,to) provides a read-only query of repair rows with delivery dates in the selected interval.
- MaintenanceProfitAuditRepository.inspect validates period dates and returns unresolved repair IDs; it **does not recognize revenue or profit**.
- MaintenanceProfitAuditTest ensures unverified repairs cannot be published as financial profit.

## Required next steps (not implemented)
1. Inspect the original main.js repair receipt and cost event generators; map each event's stable identifier, recognition date, amount, COGS, tax and exchange snapshot.
2. Reconcile imported repair receipts with newly created operational repair collections; guard against duplicated posting.
3. Distinguish in-store invoice-date profit from online collection-date profit and verify discounts and partial payments.
4. Reconcile original return/cancellation logic with Room sale refund allocations and profit reversals.
5. Only after all streams reconcile, set financial result completeness true and wire actual report/partner/profit screens.
6. Run Android Room tests and build on a disposable database clone. No build/test execution is claimed here.

## Financial safeguards
- Imported financial records remain read-only.
- Partner withdrawals reduce dues, not net profit.
- Unknown historical FX is not guessed.
- Do not connect UI to partialNet or mark an audit complete because a repair was delivered.
