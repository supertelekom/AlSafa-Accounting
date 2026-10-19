package com.alsafa.native5.tablet;

import static org.junit.Assert.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Read-only lookup edge cases; no shop data or cancellation writes. */
@RunWith(AndroidJUnit4.class)
public final class InvoiceCancellationLookupTest {
    @Test public void acceptsLocalizedDigitsAndOpaqueIds() {
        assertEquals("587", InvoiceCancellationActivity.normalizeInvoiceId("٥٨٧"));
        assertEquals("587", InvoiceCancellationActivity.normalizeInvoiceId("۵۸۷"));
        assertEquals("INV-587", InvoiceCancellationActivity.normalizeInvoiceId("INV-٥٨٧"));
        assertEquals("", InvoiceCancellationActivity.normalizeInvoiceId("INV 587"));
    }

    @Test public void publicNumberMustNotSilentlyMatchDifferentInternalInvoice() {
        SQLiteDatabase db=SQLiteDatabase.create(null);
        try {
            db.execSQL("CREATE TABLE online_invoices(invoice_id TEXT PRIMARY KEY,total_minor INTEGER,collected_minor INTEGER,currency TEXT)");
            db.execSQL("CREATE TABLE invoice_number_mapping(invoice_id TEXT PRIMARY KEY,invoice_number TEXT UNIQUE)");
            db.execSQL("INSERT INTO online_invoices VALUES('587',100,0,'USD')");
            db.execSQL("INSERT INTO online_invoices VALUES('other-id',200,0,'USD')");
            db.execSQL("INSERT INTO invoice_number_mapping VALUES('other-id','587')");
            try(Cursor c=db.rawQuery("SELECT i.invoice_id FROM online_invoices i LEFT JOIN invoice_number_mapping m ON m.invoice_id=i.invoice_id WHERE i.invoice_id=? OR m.invoice_number=? ORDER BY i.invoice_id LIMIT 2",new String[]{"587","587"})) {
                assertTrue(c.moveToFirst());
                String first=c.getString(0);
                assertTrue(c.moveToNext());
                assertNotEquals(first,c.getString(0));
            }
        } finally { db.close(); }
    }
}
