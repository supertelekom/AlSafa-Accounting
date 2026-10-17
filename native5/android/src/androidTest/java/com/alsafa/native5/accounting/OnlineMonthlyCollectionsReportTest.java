package com.alsafa.native5.accounting;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OnlineMonthlyCollectionsReportTest {
    private static Money usd(long minor){return new Money(Money.Currency.USD,minor);}
    @Test public void dispatchInSeptemberCollectionInOctoberOnlyCountsOctoberProfit() {
        Native5DatabaseHelper helper=new Native5DatabaseHelper(
            ApplicationProvider.getApplicationContext(),":memory:");
        SQLiteDatabase db=helper.getWritableDatabase();
        try {
            db.execSQL("INSERT INTO inventory_balances(product_id,quantity) VALUES('watch-1',1)");
            new OnlineDispatchPostingService(db).dispatch("dispatch-1","invoice-1",
                usd(6000),usd(3500),Collections.singletonList(new InventoryDelta("watch-1",-1)));
            ZoneId zone=ZoneId.of("Asia/Damascus");
            YearMonth collectionMonth=YearMonth.now(zone);
            YearMonth priorMonth=collectionMonth.minusMonths(1);
            long september=priorMonth.atEndOfMonth().atTime(23,0)
                .atZone(zone).toInstant().toEpochMilli();
            db.execSQL("UPDATE online_invoices SET created_at_ms=? WHERE invoice_id='invoice-1'",
                new Object[]{september});
            OnlineMonthlyCollectionsReport report=new OnlineMonthlyCollectionsReport(db);
            assertTrue(report.forMonth(priorMonth,zone).isEmpty());
            new OnlineReceiptPostingService(db).collect("receipt-1","invoice-1",
                usd(6000),InvoiceSettlement.Channel.SHAM_CASH);
            OnlineMonthlyCollectionsReport.Totals totals=
                report.forMonth(collectionMonth,zone).get(Money.Currency.USD);
            assertNotNull(totals);
            assertEquals(6000,totals.collected.minorUnits());
            assertEquals(2500,totals.realizedProfit.minorUnits());
        } finally {helper.close();}
    }
}
