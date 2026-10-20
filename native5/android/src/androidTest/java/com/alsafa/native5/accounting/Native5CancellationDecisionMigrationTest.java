package com.alsafa.native5.accounting;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Migration regression: a v6 invoice and review must survive v7 upgrade. */
@RunWith(AndroidJUnit4.class)
public final class Native5CancellationDecisionMigrationTest {
    @Test public void upgradeKeepsInvoiceAndReviewAndAddsImmutableDecisions() {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="native5-v6-v7-review.db";
        ctx.deleteDatabase(name);
        try {
            try(Native5DatabaseHelper helper=new Native5DatabaseHelper(ctx,name)) {
                SQLiteDatabase db=helper.getWritableDatabase();
                db.execSQL("INSERT INTO online_invoices(invoice_id,total_minor,cost_minor,currency,created_at_ms) VALUES('prior-invoice',1000,400,'USD',1)");
                db.execSQL("INSERT INTO invoice_cancellation_requests(request_id,invoice_id,invoice_number,reason,requested_at_ms) VALUES('prior-review','prior-invoice','123','خطأ',2)");
                db.execSQL("DROP TRIGGER cancellation_decisions_no_update");
                db.execSQL("DROP TRIGGER cancellation_decisions_no_delete");
                db.execSQL("DROP TABLE invoice_cancellation_decisions");
                db.setVersion(6);
            }
            try(Native5DatabaseHelper helper=new Native5DatabaseHelper(ctx,name)) {
                SQLiteDatabase db=helper.getWritableDatabase();
                assertEquals(1000,android.database.DatabaseUtils.longForQuery(db,
                    "SELECT total_minor FROM online_invoices WHERE invoice_id='prior-invoice'",null));
                try(Cursor c=db.rawQuery("SELECT reason FROM invoice_cancellation_requests WHERE request_id='prior-review'",null)) {
                    assertTrue(c.moveToFirst());
                    assertEquals("خطأ",c.getString(0));
                }
                db.execSQL("INSERT INTO invoice_cancellation_decisions(request_id,decision,reason,decided_at_ms) VALUES('prior-review','REJECTED','تم التحقق',3)");
                try {
                    db.execSQL("UPDATE invoice_cancellation_decisions SET reason='تغيير' WHERE request_id='prior-review'");
                    fail("Immutable decision was changed");
                } catch(android.database.SQLException expected) { }
                assertEquals(7,db.getVersion());
            }
        } finally { ctx.deleteDatabase(name); }
    }
}
