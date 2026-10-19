# Invoice cancellation (design only)

Invoice number example: 587. Reason example: recorded in error. No production invoice has been cancelled.

## Rules

1. Look up the exact invoice and require a written reason. Preserve the original invoice and number; mark it cancelled only through an immutable reversal event.
2. A cancellation event has a unique idempotency key tied to the invoice ID; replay must not duplicate stock, refund or journal movements.
3. Return each sold color or spare part only after physical return is confirmed. Repairs and services without returned parts do not automatically add stock.
4. Reverse receivables separately from actual cash refunds. Record an amount owed to the customer until the refund is executed. Refund from the same currency and payment account where possible, with explicit handling for split payments.
5. Record accounting adjustments on the cancellation date; retain closed-period history. Recompute reporting from ledger entries rather than deleting prior profit.
6. Write invoice status, reversal ledger, inventory movements, cash/receivable entries and Firebase outbox in one SQLite transaction; fail all or succeed all.
7. The tablet remains authoritative. Sync cancellation as an immutable event and prevent legacy devices from resurrecting the invoice.
8. Require an invoice preview, user confirmation, and audit actor/time. Block cancelling an already cancelled invoice.

## Current status

This is an approved design specification, not a live cancellation implementation. No transaction or data has been changed.
