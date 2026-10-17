package com.alsafa.native5.accounting;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.Test;
import static org.junit.Assert.*;

public class RealizedCollectionRecordTest {
    @Test public void previousMonthInvoiceCollectedInOctoberCountsInOctober() {
        ZoneId zone=ZoneId.of("Asia/Damascus");
        long receipt=LocalDateTime.of(2026,10,3,11,0).atZone(zone).toInstant().toEpochMilli();
        Money usdTotal=new Money(Money.Currency.USD,6000);
        CollectionProfitAllocation.Allocation profit=CollectionProfitAllocation.allocate(
            usdTotal,new Money(Money.Currency.USD,4000),
            new Money(Money.Currency.USD,0),new Money(Money.Currency.USD,0),usdTotal);
        RealizedCollectionRecord record=new RealizedCollectionRecord(
            "receipt-1","september-invoice",receipt,profit);
        assertEquals(YearMonth.of(2026,10),record.reportMonth(zone));
        assertEquals(2000,record.realizedProfit.minorUnits());
    }
}
