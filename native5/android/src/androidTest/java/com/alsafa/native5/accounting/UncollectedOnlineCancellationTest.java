package com.alsafa.native5.accounting;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class UncollectedOnlineCancellationTest {
    @Test public void returnedStockReversesOnlyOnce() {
        try(Native5DatabaseHelper helper=new Native5DatabaseHelper(ApplicationProvider.getApplicationContext(),":memory:")) {
            SQLiteDatabase db=helper.getWritableDatabase();
            db.execSQL("INSERT INTO inventory_balances(product_id,quantity) VALUES('p',5)");
            new OnlineDispatchPostingService(db).dispatch("dispatch","invoice",
                new Money(Money.Currency.USD,1000),new Money(Money.Currency.USD,400),
                java.util.Collections.singletonList(new InventoryDelta("p",-2)));
            new InvoiceCancellationRequestService(db).requestReview("review","invoice","return");
            UncollectedOnlineCancellationService service=new UncollectedOnlineCancellationService(db);
            service.cancel("cancel","invoice","tablet",true);
            assertEquals(5,android.database.DatabaseUtils.longForQuery(db,
                "SELECT quantity FROM inventory_balances WHERE product_id='p'",null));
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM online_cancellations",null));
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM outbox WHERE operation_id='cancel'",null));
            try { service.cancel("again","invoice","tablet",true);fail("Duplicate"); }
            catch(android.database.SQLException expected) {}
        }
    }
}
