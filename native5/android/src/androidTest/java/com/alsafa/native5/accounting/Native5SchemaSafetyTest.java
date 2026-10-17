package com.alsafa.native5.accounting;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Native5SchemaSafetyTest {
    @Test public void cannotDeletePostedEventOrNegativeStock() {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="native5-schema-safety.db";
        ctx.deleteDatabase(name);
        try(Native5DatabaseHelper helper=new Native5DatabaseHelper(ctx,name)) {
            SQLiteDatabase db=helper.getWritableDatabase();
            db.execSQL("INSERT INTO accounting_events(event_id,operation_kind,created_at_ms) VALUES('immutable-1','SALE',1)");
            db.execSQL("INSERT INTO journal_lines(event_id,line_no,account_id,currency,signed_minor_units) VALUES('immutable-1',0,'cash','USD',100)");
            db.execSQL("INSERT INTO inventory_balances(product_id,quantity) VALUES('watch-1',1)");
            assertSqlRejected(db,"DELETE FROM accounting_events WHERE event_id='immutable-1'");
            assertSqlRejected(db,"UPDATE inventory_balances SET quantity=-1 WHERE product_id='watch-1'");
            try(Cursor c=db.rawQuery("SELECT quantity FROM inventory_balances WHERE product_id='watch-1'",null)) {
                assertTrue(c.moveToFirst()); assertEquals(1,c.getLong(0));
            }
        } finally { ctx.deleteDatabase(name); }
    }
    private static void assertSqlRejected(SQLiteDatabase db,String sql) {
        try { db.execSQL(sql); fail("Database accepted unsafe write: "+sql); }
        catch(android.database.SQLException expected) { }
    }
}
