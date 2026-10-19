package com.alsafa.native5.accounting;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class InvoiceCancellationRequestServiceTest {
    @Test public void requestIsImmutableAndDoesNotChangeAccounting() {
        try(Native5DatabaseHelper helper=new Native5DatabaseHelper(
            ApplicationProvider.getApplicationContext(),":memory:")) {
            SQLiteDatabase db=helper.getWritableDatabase();
            db.execSQL("INSERT INTO online_invoices(invoice_id,total_minor,cost_minor,currency,created_at_ms) VALUES('inv-a',1000,400,'USD',1)");
            db.execSQL("INSERT INTO invoice_number_mapping(invoice_id,invoice_number,assigned_at_ms) VALUES('inv-a','587',1)");
            InvoiceCancellationRequestService service=new InvoiceCancellationRequestService(db);
            assertEquals("inv-a",service.requestReview("review-a","587","تسجيل خطأ"));
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM invoice_cancellation_requests",null));
            assertEquals(0,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM accounting_events",null));
            assertEquals(0,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM outbox",null));
            assertEquals(1000,android.database.DatabaseUtils.longForQuery(db,
                "SELECT total_minor FROM online_invoices WHERE invoice_id='inv-a'",null));
            try {
                service.requestReview("review-b","587","مكرر");
                fail("Duplicate request accepted");
            } catch(android.database.SQLException expected) {}
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM invoice_cancellation_requests",null));
            try {
                service.requestReview("review-missing","999","غير موجود");
                fail("Unknown invoice accepted");
            } catch(IllegalArgumentException expected) {}
        }
    }
}
