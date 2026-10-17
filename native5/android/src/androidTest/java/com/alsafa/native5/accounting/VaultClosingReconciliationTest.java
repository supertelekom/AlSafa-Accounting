package com.alsafa.native5.accounting;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.EnumMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VaultClosingReconciliationTest {
    @Test public void showsShortageAndSurplusWithoutMutatingLedger() {
        Native5DatabaseHelper helper=new Native5DatabaseHelper(
            ApplicationProvider.getApplicationContext(),":memory:");
        SQLiteDatabase db=helper.getWritableDatabase();
        try {
            db.execSQL("INSERT INTO accounting_events(event_id,operation_kind,created_at_ms) VALUES('opening-1','OWNER_CONTRIBUTION',1)");
            db.execSQL("INSERT INTO journal_lines(event_id,line_no,account_id,currency,signed_minor_units) VALUES('opening-1',0,'asset:payment:CASH_DRAWER','USD',10000)");
            db.execSQL("INSERT INTO journal_lines(event_id,line_no,account_id,currency,signed_minor_units) VALUES('opening-1',1,'equity:owner_contribution','USD',-10000)");
            EnumMap<Money.Currency,Money> counts=new EnumMap<>(Money.Currency.class);
            counts.put(Money.Currency.USD,new Money(Money.Currency.USD,9500));
            counts.put(Money.Currency.SYP,new Money(Money.Currency.SYP,200));
            counts.put(Money.Currency.TRY,new Money(Money.Currency.TRY,0));
            VaultClosingReconciliation reconciliation=new VaultClosingReconciliation(db);
            EnumMap<Money.Currency,VaultClosingReconciliation.Difference> differences=
                reconciliation.reconcile(InvoiceSettlement.Channel.CASH_DRAWER,counts);
            assertEquals(-500,differences.get(Money.Currency.USD).variance.minorUnits());
            assertEquals(200,differences.get(Money.Currency.SYP).variance.minorUnits());
            assertTrue(differences.get(Money.Currency.TRY).matches());
            assertEquals(10000,new VaultBalanceReader(db).balance(
                InvoiceSettlement.Channel.CASH_DRAWER,Money.Currency.USD).minorUnits());
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM accounting_events",null));
        } finally {helper.close();}
    }

    @Test public void rejectsMissingAndUnsupportedShamCashCurrency() {
        Native5DatabaseHelper helper=new Native5DatabaseHelper(
            ApplicationProvider.getApplicationContext(),":memory:");
        try {
            VaultClosingReconciliation reconciliation=
                new VaultClosingReconciliation(helper.getWritableDatabase());
            EnumMap<Money.Currency,Money> counts=new EnumMap<>(Money.Currency.class);
            counts.put(Money.Currency.USD,new Money(Money.Currency.USD,0));
            counts.put(Money.Currency.SYP,new Money(Money.Currency.SYP,0));
            counts.put(Money.Currency.TRY,new Money(Money.Currency.TRY,0));
            try {
                reconciliation.reconcile(InvoiceSettlement.Channel.SHAM_CASH,counts);
                fail("TRY count must not be accepted for Sham Cash");
            } catch(IllegalArgumentException expected) {}
        } finally {helper.close();}
    }
}
