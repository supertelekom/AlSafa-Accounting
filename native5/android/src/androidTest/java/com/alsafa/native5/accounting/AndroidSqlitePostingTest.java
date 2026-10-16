package com.alsafa.native5.accounting;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static com.alsafa.native5.accounting.Money.Currency.USD;

/** Real Android SQLite integration tests; must run on emulator/device. */
@RunWith(AndroidJUnit4.class)
public class AndroidSqlitePostingTest {
    private PostingBatch sale(String id, long quantity) {
        JournalEntry journal = new JournalEntry(id, Arrays.asList(
            new JournalEntry.Line("cash-usd",new Money(USD,1800)),
            new JournalEntry.Line("sales-usd",new Money(USD,-1800))));
        return new PostingBatch(journal,"SALE","{\"eventId\":\""+id+"\"}",
            Collections.singletonList(new InventoryDelta("watch-1",quantity)));
    }
    private long count(SQLiteDatabase db,String table) {
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+table,null)) {
            assertTrue(c.moveToFirst()); return c.getLong(0);
        }
    }
    @Test public void saleCommitsAtomicallyAndDuplicateDoesNotRepost() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("native5-instrumentation.db");
        try(Native5DatabaseHelper helper = new Native5DatabaseHelper(context,"native5-instrumentation.db")) {
            SQLiteDatabase db=helper.getWritableDatabase();
            db.execSQL("INSERT INTO inventory_balances(product_id,quantity) VALUES('watch-1',2)");
            AtomicPostingService service=new AtomicPostingService(new AndroidSqlitePostingPort(db));
            service.post(sale("sale-1",-1));
            assertEquals(1,count(db,"accounting_events"));
            assertEquals(2,count(db,"journal_lines"));
            assertEquals(1,count(db,"inventory_movements"));
            assertEquals(1,count(db,"outbox"));
            try { service.post(sale("sale-1",-1)); fail("Duplicate sale accepted"); }
            catch(IllegalStateException expected) { }
            assertEquals(1,count(db,"outbox"));
            try(Cursor c=db.rawQuery("SELECT quantity FROM inventory_balances WHERE product_id='watch-1'",null)) {
                assertTrue(c.moveToFirst()); assertEquals(1,c.getLong(0));
            }
        } finally { context.deleteDatabase("native5-instrumentation.db"); }
    }
    @Test public void insufficientStockRollsBackJournalAndOutbox() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("native5-instrumentation.db");
        try(Native5DatabaseHelper helper = new Native5DatabaseHelper(context,"native5-instrumentation.db")) {
            SQLiteDatabase db=helper.getWritableDatabase();
            db.execSQL("INSERT INTO inventory_balances(product_id,quantity) VALUES('watch-1',1)");
            try { new AtomicPostingService(new AndroidSqlitePostingPort(db)).post(sale("sale-2",-2)); fail("Insufficient stock accepted"); }
            catch(IllegalStateException expected) { }
            assertEquals(0,count(db,"accounting_events"));
            assertEquals(0,count(db,"journal_lines"));
            assertEquals(0,count(db,"inventory_movements"));
            assertEquals(0,count(db,"outbox"));
            try(Cursor c=db.rawQuery("SELECT quantity FROM inventory_balances WHERE product_id='watch-1'",null)) {
                assertTrue(c.moveToFirst()); assertEquals(1,c.getLong(0));
            }
        } finally { context.deleteDatabase("native5-instrumentation.db"); }
    }
}
