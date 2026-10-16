package com.alsafa.native5.accounting;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises outbox state transitions against a real Android SQLite database. */
@RunWith(AndroidJUnit4.class)
public class AndroidOutboxQueueTest {
    @Test public void claimRetryAndConfirmWithFencing() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="native5-outbox-instrumentation.db";
        context.deleteDatabase(name);
        try(Native5DatabaseHelper helper=new Native5DatabaseHelper(context,name)) {
            SQLiteDatabase db=helper.getWritableDatabase();
            db.execSQL("INSERT INTO accounting_events(event_id,operation_kind,created_at_ms) VALUES('e1','SALE',1)");
            db.execSQL("INSERT INTO outbox(operation_id,operation_kind,payload,created_at_ms) VALUES('e1','SALE','{}',1)");
            AndroidOutboxQueue queue=new AndroidOutboxQueue(db);
            AndroidOutboxQueue.Item first=queue.claim(10000,1000);
            assertNotNull(first); assertEquals("e1",first.operationId); assertEquals(1,first.attempt);
            assertNull(queue.claim(10001,1000));
            assertTrue(queue.retry("e1",first.attempt,"offline"));
            AndroidOutboxQueue.Item second=queue.claim(10002,1000);
            assertNotNull(second); assertEquals(2,second.attempt);
            assertFalse(queue.confirm("e1",first.attempt,1));
            assertTrue(queue.confirm("e1",second.attempt,1));
            assertFalse(queue.retry("e1",second.attempt,"late error"));
            assertNull(queue.claim(12000,1000));
        } finally { context.deleteDatabase(name); }
    }
    @Test public void expiredClaimCanBeReclaimedWithoutDroppingOperation() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="native5-outbox-expiry.db";
        context.deleteDatabase(name);
        try(Native5DatabaseHelper helper=new Native5DatabaseHelper(context,name)) {
            SQLiteDatabase db=helper.getWritableDatabase();
            db.execSQL("INSERT INTO accounting_events(event_id,operation_kind,created_at_ms) VALUES('e2','REPAIR',1)");
            db.execSQL("INSERT INTO outbox(operation_id,operation_kind,payload,created_at_ms) VALUES('e2','REPAIR','{}',1)");
            AndroidOutboxQueue queue=new AndroidOutboxQueue(db);
            assertEquals(1,queue.claim(10000,1000).attempt);
            assertEquals(2,queue.claim(11000,1000).attempt);
            assertFalse(queue.confirm("e2",1,7));
            assertTrue(queue.confirm("e2",2,7));
        } finally { context.deleteDatabase(name); }
    }
}
