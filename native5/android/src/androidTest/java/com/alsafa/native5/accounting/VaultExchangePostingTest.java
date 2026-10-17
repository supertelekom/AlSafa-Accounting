package com.alsafa.native5.accounting;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VaultExchangePostingTest {
    private static Money m(Money.Currency currency,long minor) {return new Money(currency,minor);}
    @Test public void exchangePostsOnceAndDoesNotAllowOverdraft() {
        Native5DatabaseHelper helper=new Native5DatabaseHelper(
            ApplicationProvider.getApplicationContext(),":memory:");
        SQLiteDatabase db=helper.getWritableDatabase();
        try {
            db.execSQL("INSERT INTO accounting_events(event_id,operation_kind,created_at_ms) VALUES('opening-1','OWNER_CONTRIBUTION',1)");
            db.execSQL("INSERT INTO journal_lines(event_id,line_no,account_id,currency,signed_minor_units) VALUES('opening-1',0,'asset:payment:CASH_DRAWER','TRY',100000)");
            db.execSQL("INSERT INTO journal_lines(event_id,line_no,account_id,currency,signed_minor_units) VALUES('opening-1',1,'equity:owner_contribution','TRY',-100000)");
            VaultExchangePostingService service=new VaultExchangePostingService(db);
            service.exchange("fx-1",m(Money.Currency.TRY,100000),
                InvoiceSettlement.Channel.CASH_DRAWER,m(Money.Currency.USD,2500),
                InvoiceSettlement.Channel.SHAM_CASH);
            assertEquals(0,android.database.DatabaseUtils.longForQuery(db,
                "SELECT SUM(signed_minor_units) FROM journal_lines WHERE account_id='asset:payment:CASH_DRAWER' AND currency='TRY'",null));
            assertEquals(2500,android.database.DatabaseUtils.longForQuery(db,
                "SELECT SUM(signed_minor_units) FROM journal_lines WHERE account_id='asset:payment:SHAM_CASH' AND currency='USD'",null));
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM outbox WHERE operation_id='fx-1'",null));
            reject(() -> service.exchange("fx-2",m(Money.Currency.TRY,1),
                InvoiceSettlement.Channel.CASH_DRAWER,m(Money.Currency.USD,1),
                InvoiceSettlement.Channel.SHAM_CASH));
            reject(() -> service.exchange("fx-1",m(Money.Currency.TRY,100000),
                InvoiceSettlement.Channel.CASH_DRAWER,m(Money.Currency.USD,2500),
                InvoiceSettlement.Channel.SHAM_CASH));
            assertEquals(1,android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM accounting_events WHERE operation_kind='VAULT_CURRENCY_EXCHANGE'",null));
        } finally {helper.close();}
    }
    private static void reject(Runnable action) {
        try {action.run();fail("Expected rejection");}
        catch(RuntimeException expected) {}
    }
}
