package com.alsafa.native5.accounting;

/** Signed quantity adjustment; product ID is stable across device and cloud records. */
public final class InventoryDelta {
    private final String productId;
    private final long quantityChange;
    public InventoryDelta(String productId, long quantityChange) {
        if (productId == null || productId.trim().isEmpty()) throw new IllegalArgumentException("Missing product ID");
        if (quantityChange == 0) throw new IllegalArgumentException("Zero inventory change");
        this.productId = productId;
        this.quantityChange = quantityChange;
    }
    public String productId() { return productId; }
    public long quantityChange() { return quantityChange; }
}
