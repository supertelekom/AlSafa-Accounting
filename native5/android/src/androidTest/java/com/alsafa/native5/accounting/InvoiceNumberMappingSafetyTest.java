package com.alsafa.native5.accounting;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Regression tests for immutable invoice numbering and additive v5->v6 upgrade. */
@RunWith(AndroidJUnit4.class)
public final class InvoiceNumberMappingSafetyTest {
    @Test public void mappingRequiresExistingInvoiceAndCannotBeReassigned() {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="native5-number-map-safety.db";
        ctx.deleteDatabase(name);
        try(Native5DatabaseHelper helper=new Native5DatabaseHelper(ctx,name)) {
            SQLiteDatabase db=helper.getWritableDatabase();
            db.execSQL("INSERT INTO online_invoices(invoice_id,total_minor,cost_minor,currency,created_at_ms) VALUES('inv-a',100,40,'USD',1)");
            db.execSQL("INSERT INTO online_invoices(invoice_id,total_minor,cost_minor,currency,created_at_ms) VALUES('inv-b',200,80,'USD',2)");
            db.execSQL("INSERT INTO invoice_number_mapping(invoice_id,invoice_number,assigned_at_ms) VALUES('inv-a','587',3)");
            assertRejected(db,"INSERT INTO invoice_number_mapping(invoice_id,invoice_number,assigned_at_ms) VALUES('inv-b','587',4)");
            assertRejected(db,"INSERT INTO invoice_number_mapping(invoice_id,invoice_number,assigned_at_ms) VALUES('missing','588',4)");
            assertRejected(db,"UPDATE invoice_number_mapping SET invoice_number='588' WHERE invoice_id='inv-a'");
            assertRejected(db,"DELETE FROM invoice_number_mapping WHERE invoice_id='inv-a'");
            try(Cursor c=db.rawQuery("SELECT invoice_number FROM invoice_number_mapping WHERE invoice_id='inv-a'",null)) {
                assertTrue(c.moveToFirst()); assertEquals("587",c.getString(0));
            }
        } finally { ctx.deleteDatabase(name); }
    }

    @Test public void existingInvoiceSurvivesUpgradeFromVersionFive() {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="native5-number-map-upgrade.db";
        ctx.deleteDatabase(name);
        try {
            try(Native5DatabaseHelper helper=new Native5DatabaseHelper(ctx,name)) {
                SQLiteDatabase db=helper.getWritableDatabase();
                db.execSQL("INSERT INTO online_invoices(invoice_id,total_minor,cost_minor,currency,created_at_ms) VALUES('legacy-1',150,50,'USD',1)");
                db.execSQL("DROP TABLE invoice_number_mapping");
                db.setVersion(5);
            }
            try(Native5DatabaseHelper helper=new Native5DatabaseHelper(ctx,name)) {
                SQLiteDatabase db=helper.getWritableDatabase();
                try(Cursor c=db.rawQuery("SELECT total_minor FROM online_invoices WHERE invoice_id='legacy-1'",null)) {
                    assertTrue(c.moveToFirst()); assertEquals(150,c.getLong(0));
                }
                db.execSQL("INSERT INTO invoice_number_mapping(invoice_id,invoice_number,assigned_at_ms) VALUES('legacy-1','587',2)");
            }
        } finally { ctx.deleteDatabase(name); }
    }

    private static void assertRejected(SQLiteDatabase db,String sql) {
        try { db.execSQL(sql); fail("Unsafe write accepted: "+sql); }
        catch(android.database.SQLException expected) { }
    }
}
