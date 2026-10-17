package com.alsafa.native5.accounting;

import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.EnumMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VaultVarianceAdjustmentPostingTest {
    @Test public void adjustmentIsAtomicUniqueAndKeepsOriginalClosing() {
        try(Native5DatabaseHelper helper=new Native5DatabaseHelper(
                ApplicationProvider.getApplicationContext(),":memory:")) {
            SQLiteDatabase db=helper.getWritableDatabase();
            EnumMap<Money.Currency,Money> counts=new EnumMap<>(Money.Currency.class);
            counts.put(Money.Currency.USD,new Money(Money.Currency.USD,150));
            counts.put(Money.Currency.TRY,new Money(Money.Currency.TRY,0));
            counts.put(Money.Currency.SYP,new Money(Money.Currency.SYP,0));
            new VaultClosingPostingService(db).close("close-test",
                InvoiceSettlement.Channel.CASH_DRAWER,counts);
            VaultVarianceAdjustmentService service=new VaultVarianceAdjustmentService(db);
            service.adjust("variance-test","close-test",
                new Money(Money.Currency.USD,150),"Verified surplus during cash count");
            assertEquals(150,new VaultBalanceReader(db).balance(
                InvoiceSettlement.Channel.CASH_DRAWER,Money.Currency.USD).minorUnits());
            assertEquals(1,DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM vault_variance_adjustments WHERE closing_id='close-test'",null));
            assertEquals(1,DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM outbox WHERE operation_id='variance-test'",null));
            assertEquals(150,new VaultClosingHistory(db).get("close-test")
                .counts.get(Money.Currency.USD).variance.minorUnits());
            try {
                service.adjust("variance-again","close-test",
                    new Money(Money.Currency.USD,150),"Second attempt must fail");
                fail("Duplicate adjustment accepted");
            } catch(IllegalStateException expected) {}
            assertEquals(0,DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM accounting_events WHERE event_id='variance-again'",null));
        }
    }
    @Test public void rejectsAmountDifferentFromRecordedVariance() {
        try(Native5DatabaseHelper helper=new Native5DatabaseHelper(
                ApplicationProvider.getApplicationContext(),":memory:")) {
            SQLiteDatabase db=helper.getWritableDatabase();
            EnumMap<Money.Currency,Money> counts=new EnumMap<>(Money.Currency.class);
            counts.put(Money.Currency.USD,new Money(Money.Currency.USD,100));
            counts.put(Money.Currency.TRY,new Money(Money.Currency.TRY,0));
            counts.put(Money.Currency.SYP,new Money(Money.Currency.SYP,0));
            new VaultClosingPostingService(db).close("close-two",
                InvoiceSettlement.Channel.CASH_DRAWER,counts);
            try {
                new VaultVarianceAdjustmentService(db).adjust("wrong-amount","close-two",
                    new Money(Money.Currency.USD,200),"Incorrect correction");
                fail("Mismatched variance accepted");
            } catch(IllegalArgumentException expected) {}
            assertEquals(0,DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM vault_variance_adjustments",null));
        }
    }
}
