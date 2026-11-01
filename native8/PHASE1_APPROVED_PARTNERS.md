# Native 8 — approved partner scope (2026-11-01)

Owner explicitly removes both legacy external partners from Native 8. They must not appear as active partners, be assigned any profit share, or participate in operational accounting, reports or partner withdrawal forms.

Active partners ONLY:
- father: 40%
- me (Abdul-Karim): 35%
- brother (Yazan): 25%

The temporary 2026-10-26 backup has two entries in settings.externalPartners (external-1, external-2), but the inspected top-level sales, purchases, partnerWithdrawals, cashTransactions and products contain no populated external-related fields. This observation applies only to the temporary backup, not the larger future cutover backup.

For Native 8, calculate family distributable profit without subtracting external partner shares. This is an explicit owner-approved deviation from legacy v3.9.359's optional external partner calculation; retain the original source formula in audit documentation for traceability. Do not mutate the source backup. Preserve raw input in import archive and omit external partners from active operational tables.

At final cutover, validate the new backup again for historical external-partner financial activity. If such activity appears, flag for review instead of silently discarding it.

Customer orders remain excluded from active operational scope by owner instruction. No invented workflows.
