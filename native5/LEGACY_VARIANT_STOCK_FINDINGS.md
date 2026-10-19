# Legacy v3.9.359 variant stock investigation (read-only)

## Confirmed from the supplied production source

The legacy sale posting loop (in `assets/main.js`, near the `stockApplied: true` sale creation) resolves **each sale item's `productId`** with `productById(item.productId)`, reads `stockValue(product)`, then assigns `product.stock = before - item.qty` and appends an inventory movement referencing **that same product.id**. This code does not deliberately subtract from every color in a variant group.

The group display/editor uses `productVariantGroupItems(product, preferredBaseId)` to select products sharing `variantGroupId`. Legacy groups without any `variantRole: 'base'` trigger `legacyVariantGroupNeedsBaseChoice`, which is an existing migration/UI accommodation rather than proof of lost stock.

## Confirmed from the 2026-10-18 historical backup

- 552 product records, of which 226 have a nonempty `variantGroupId`.
- Grouped records have `variantRole: additional` on 97 records, `base` on 44, and no `variantRole` on 85. These missing roles should **not** be silently inferred or treated as invalid stock.
- 86 sales with 99 sale item lines; two item lines lack a usable `productId`. Their stock impact requires transaction-specific investigation.
- The snapshot predates some later sales and cannot establish today's live quantities.

## Required next checks

1. For the specific reported sale, compare sale item `productId` to the inventory movement `itemId` and the actual variant product `id`.
2. Compare list view stock source and the variant editor stock source, including `stockValue` and any derived/aggregate values.
3. Check whether a subsequent sync, stock adjustment, or import overwrote the variant's `stock`.
4. Never rewrite live quantities or historical invoice links based solely on an apparent discrepancy in the UI.
5. In Native 5, each variant must be its own stock-bearing product ID; a parent/group is organizational and must not maintain a second authoritative stock balance.

This document contains only schema/aggregate findings and code observations, no customer data or prices.
