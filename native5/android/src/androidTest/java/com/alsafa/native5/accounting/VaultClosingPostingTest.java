package com.alsafa.native5.accounting;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.EnumMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VaultClosingPostingTest {
    @Test public void storesImmutableSnapshotAndOutboxWithoutChangingCash() {
        Native5DatabaseHelper helper=new Native5DatabaseHelper(
            ApplicationProvider.getApplicationContext(),":memory:");
        SQLiteDatabase db=helper.getWritableDatabase();
        try {
            db.execSQL("INSERT INTO accounting_events(event_id,operation_kind,created_at_ms) VALUES('opening-1','OWNER_CONTRIBUTION',1)");
            db.execSQL("INSERT INTO journal_lines(event_id,line_no,account_id,currency,signed_minor_units) VALUES('opening-1',0,'asset:payment:CASH_DRAWER','USD',10000)");
            db.execSQL("INSERT INTO journal_lines(event_id,line_no,account_id,currency,signed_minor_units) VALUES('opening-1',1,'equity:owner_contribution','USD',-10000)");
            EnumMap<Money.Currency,Money> counted=new EnumMap<>(Money.Currency.class);
            counted.put(Money.Currency.USD,new Money(Money.Currency.USD,9800));
            counted.put(Money.Currency.SYP,new Money(Money.Currency.SYP,0));
            counted.put(Money.Currency.TRY,new Money(Money.Currency.TRY,0));
            VaultClosingPostingService service=new VaultClosingPostingService(db);
            service.close("close-1",InvoiceSettlement.Channel.CASH_DRAWER,counted);
            VaultClosingHistory.Snapshot snapshot=new VaultClosingHistory(db).get("close-1");
            assertNotNull(snapshot);
            assertEquals(-200,snapshot.counts.get(Money.Currency.USD).variance.minorUnits());
            assertEquals(10000,new VaultBalanceReader(db).balance(
                InvoiceSettlement.Channel.CASH_DRAWER,Money.Currency.USD).minorUnits());
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM outbox WHERE operation_id='close-1'",null));
            try {
                db.execSQL("UPDATE vault_closing_counts SET counted_minor=10000 WHERE closing_id='close-1'");
                fail("Closing snapshot unexpectedly mutable");
            } catch(android.database.SQLException expected) {}
            try {
                service.close("close-1",InvoiceSettlement.Channel.CASH_DRAWER,counted);
                fail("Duplicate closing accepted");
            } catch(android.database.SQLException expected) {}
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM vault_closings WHERE closing_id='close-1'",null));
        } finally {helper.close();}
    }
    @Test public void rejectsIncompleteShamCashCountsWithoutPosting() {
        Native5DatabaseHelper helper=new Native5DatabaseHelper(
            ApplicationProvider.getApplicationContext(),":memory:");
        SQLiteDatabase db=helper.getWritableDatabase();
        try {
            EnumMap<Money.Currency,Money> counted=new EnumMap<>(Money.Currency.class);
            counted.put(Money.Currency.USD,new Money(Money.Currency.USD,0));
            try {
                new VaultClosingPostingService(db).close("close-invalid",
                    InvoiceSettlement.Channel.SHAM_CASH,counted);
                fail("Incomplete counts accepted");
            } catch(IllegalArgumentException expected) {}
            assertEquals(0,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM accounting_events",null));
        } finally {helper.close();}
    }
}
