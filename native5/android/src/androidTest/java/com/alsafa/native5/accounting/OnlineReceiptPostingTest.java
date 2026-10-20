package com.alsafa.native5.accounting;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OnlineReceiptPostingTest {
    @Test public void receiptIsAtomicAndDuplicateCannotCollectTwice() {
        Native5DatabaseHelper helper=new Native5DatabaseHelper(
            ApplicationProvider.getApplicationContext(),":memory:");
        SQLiteDatabase db=helper.getWritableDatabase();
        try {
            db.execSQL("INSERT INTO online_invoices(invoice_id,total_minor,cost_minor,currency,created_at_ms) VALUES('invoice-1',6000,3500,'USD',1)");
            OnlineReceiptPostingService service=new OnlineReceiptPostingService(db);
            OnlineCollectionPlan first=service.collect("receipt-1","invoice-1",
                new Money(Money.Currency.USD,2000),InvoiceSettlement.Channel.CASH_DRAWER);
            assertEquals(4000,first.remainingDue.minorUnits());
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM online_collections",null));
            try {
                service.collect("receipt-1","invoice-1",
                    new Money(Money.Currency.USD,1000),InvoiceSettlement.Channel.CASH_DRAWER);
                fail("Duplicate operation ID accepted");
            } catch(android.database.SQLException expected) {}
            assertEquals(2000,android.database.DatabaseUtils.longForQuery(db,
                "SELECT collected_minor FROM online_invoices WHERE invoice_id='invoice-1'",null));
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM outbox",null));
            OnlineCollectionPlan finalReceipt=service.collect("receipt-2","invoice-1",
                new Money(Money.Currency.USD,4000),InvoiceSettlement.Channel.SHAM_CASH);
            assertTrue(finalReceipt.fullyPaid);
            assertEquals(3500,android.database.DatabaseUtils.longForQuery(db,
                "SELECT released_cost_minor FROM online_invoices WHERE invoice_id='invoice-1'",null));
        } finally { helper.close(); }
    }

    @Test public void pendingCancellationReviewPreventsReceiptAndLeavesBalancesUnchanged() {
        try(Native5DatabaseHelper helper=new Native5DatabaseHelper(
            ApplicationProvider.getApplicationContext(),":memory:")) {
            SQLiteDatabase db=helper.getWritableDatabase();
            db.execSQL("INSERT INTO online_invoices(invoice_id,total_minor,cost_minor,currency,created_at_ms) VALUES('invoice-review',6000,3500,'USD',1)");
            new InvoiceCancellationRequestService(db).requestReview("request-1","invoice-review","مراجعة");
            try {
                new OnlineReceiptPostingService(db).collect("receipt-blocked","invoice-review",
                    new Money(Money.Currency.USD,2000),InvoiceSettlement.Channel.CASH_DRAWER);
                fail("Collection was allowed while review is pending");
            } catch(IllegalStateException expected) {}
            assertFalse(db.inTransaction());
            assertEquals(0,android.database.DatabaseUtils.longForQuery(db,
                "SELECT collected_minor FROM online_invoices WHERE invoice_id='invoice-review'",null));
            assertEquals(0,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM accounting_events",null));
            assertEquals(0,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM outbox",null));
        }
    }
}
