package com.alsafa.native5.accounting;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;

/** Immutable management-report fact dated by confirmed receipt, never invoice creation. */
public final class RealizedCollectionRecord {
    public final String collectionId;
    public final String invoiceId;
    public final long collectedAtEpochMs;
    public final Money collected;
    public final Money realizedProfit;

    public RealizedCollectionRecord(String collectionId,String invoiceId,long collectedAtEpochMs,
                                    CollectionProfitAllocation.Allocation allocation) {
        if(collectionId==null || !collectionId.matches("[A-Za-z0-9_-]{1,128}") ||
           invoiceId==null || !invoiceId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid collection/invoice ID");
        if(collectedAtEpochMs<=0) throw new IllegalArgumentException("Missing actual collection timestamp");
        Objects.requireNonNull(allocation,"allocation");
        this.collectionId=collectionId;
        this.invoiceId=invoiceId;
        this.collectedAtEpochMs=collectedAtEpochMs;
        this.collected=allocation.collected;
        this.realizedProfit=allocation.realizedProfit;
    }
    public YearMonth reportMonth(ZoneId storeZone) {
        return YearMonth.from(Instant.ofEpochMilli(collectedAtEpochMs)
            .atZone(Objects.requireNonNull(storeZone,"storeZone")));
    }
}
