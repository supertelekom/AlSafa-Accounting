package com.alsafa.native5.accounting;

import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Verify real on-disk upgrades keep existing events and add only new tables. */
@RunWith(AndroidJUnit4.class)
public class Native5ClosingMigrationTest {
    @Test public void upgradeFromV2RetainsPostedEventAndAddsClosingTables() {
        assertMigration(2);
    }
    @Test public void upgradeFromV3RetainsPostedEventAndAddsAdjustmentTable() {
        assertMigration(3);
    }
    private void assertMigration(int oldVersion) {
        Context context=ApplicationProvider.getApplicationContext();
        String name="native5-migration-test-"+oldVersion+".db";
        context.deleteDatabase(name);
        try {
            try(SQLiteDatabase old=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)) {
                old.execSQL("CREATE TABLE accounting_events(event_id TEXT PRIMARY KEY NOT NULL,operation_kind TEXT NOT NULL,created_at_ms INTEGER NOT NULL)");
                old.execSQL("INSERT INTO accounting_events(event_id,operation_kind,created_at_ms) VALUES('keep-me','SALE',123)");
                old.setVersion(oldVersion);
            }
            try(Native5DatabaseHelper helper=new Native5DatabaseHelper(context,name)) {
                SQLiteDatabase upgraded=helper.getWritableDatabase();
                assertEquals(4,upgraded.getVersion());
                assertEquals(1,DatabaseUtils.longForQuery(upgraded,
                    "SELECT COUNT(*) FROM accounting_events WHERE event_id='keep-me'",null));
                assertEquals(1,DatabaseUtils.longForQuery(upgraded,
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='vault_closings'",null));
                assertEquals(1,DatabaseUtils.longForQuery(upgraded,
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='vault_closing_counts'",null));
                assertEquals(1,DatabaseUtils.longForQuery(upgraded,
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='vault_variance_adjustments'",null));
            }
        } finally {context.deleteDatabase(name);}
    }
}
