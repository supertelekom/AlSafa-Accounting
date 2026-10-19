package com.alsafa.native5.accounting;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OnlineDispatchPostingTest {
    @Test public void dispatchDecrementsStockAndNeverBooksCashOrRevenue() {
        Native5DatabaseHelper helper=new Native5DatabaseHelper(
            ApplicationProvider.getApplicationContext(),":memory:");
        SQLiteDatabase db=helper.getWritableDatabase();
        try {
            db.execSQL("INSERT INTO inventory_balances(product_id,quantity) VALUES('watch-1',2)");
            OnlineDispatchPostingService service=new OnlineDispatchPostingService(db);
            service.dispatch("dispatch-1","invoice-1",
                new Money(Money.Currency.USD,6000),
                new Money(Money.Currency.USD,3500),
                Collections.singletonList(new InventoryDelta("watch-1",-1)));
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT quantity FROM inventory_balances WHERE product_id='watch-1'",null));
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM online_invoices",null));
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM outbox",null));
            assertEquals(0,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM journal_lines WHERE account_id LIKE 'income:%' OR account_id LIKE 'asset:payment:%'",null));
            try {
                service.dispatch("dispatch-2","invoice-2",
                    new Money(Money.Currency.USD,6000),
                    new Money(Money.Currency.USD,3500),
                    Collections.singletonList(new InventoryDelta("watch-1",-2)));
                fail("Insufficient stock accepted");
            } catch(IllegalStateException expected) {}
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM online_invoices",null));
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT quantity FROM inventory_balances WHERE product_id='watch-1'",null));
        } finally { helper.close(); }
    }

    @Test public void duplicatePublicNumberRollsBackInvoiceStockAndOutbox() {
        Native5DatabaseHelper helper=new Native5DatabaseHelper(
            ApplicationProvider.getApplicationContext(),":memory:");
        SQLiteDatabase db=helper.getWritableDatabase();
        try {
            db.execSQL("INSERT INTO inventory_balances(product_id,quantity) VALUES('watch-1',3)");
            OnlineDispatchPostingService service=new OnlineDispatchPostingService(db);
            Money total=new Money(Money.Currency.USD,6000);
            Money cost=new Money(Money.Currency.USD,3500);
            service.dispatch("dispatch-numbered-1","invoice-numbered-1","587",total,cost,
                Collections.singletonList(new InventoryDelta("watch-1",-1)));
            assertEquals(2,android.database.DatabaseUtils.longForQuery(db,
                "SELECT quantity FROM inventory_balances WHERE product_id='watch-1'",null));
            try {
                service.dispatch("dispatch-numbered-2","invoice-numbered-2","587",total,cost,
                    Collections.singletonList(new InventoryDelta("watch-1",-1)));
                fail("Duplicate public invoice number accepted");
            } catch(android.database.SQLException expected) { }
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM online_invoices",null));
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM invoice_number_mapping",null));
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM accounting_events",null));
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM outbox",null));
            assertEquals(2,android.database.DatabaseUtils.longForQuery(db,
                "SELECT quantity FROM inventory_balances WHERE product_id='watch-1'",null));
        } finally { helper.close(); }
    }
}
