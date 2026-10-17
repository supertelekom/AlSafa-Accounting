package com.alsafa.native5.accounting;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VaultPeriodReportTest {
    @Test public void exchangeAffectsBothCurrencyBalancesWithoutCreatingRevenue() {
        Native5DatabaseHelper helper=new Native5DatabaseHelper(
            ApplicationProvider.getApplicationContext(),":memory:");
        SQLiteDatabase db=helper.getWritableDatabase();
        try {
            db.execSQL("INSERT INTO accounting_events(event_id,operation_kind,created_at_ms) VALUES('opening-try','OWNER_CONTRIBUTION',1000)");
            db.execSQL("INSERT INTO journal_lines(event_id,line_no,account_id,currency,signed_minor_units) VALUES('opening-try',0,'asset:payment:CASH_DRAWER','TRY',100000)");
            db.execSQL("INSERT INTO journal_lines(event_id,line_no,account_id,currency,signed_minor_units) VALUES('opening-try',1,'equity:owner_contribution','TRY',-100000)");
            new VaultExchangePostingService(db).exchange("fx-report-1",
                new Money(Money.Currency.TRY,100000),InvoiceSettlement.Channel.CASH_DRAWER,
                new Money(Money.Currency.USD,2500),InvoiceSettlement.Channel.SHAM_CASH);
            long now=System.currentTimeMillis();
            VaultPeriodReport report=new VaultPeriodReport(db);
            VaultPeriodReport.Totals drawer=report.forPeriod(
                InvoiceSettlement.Channel.CASH_DRAWER,2000,now+60000)
                .get(Money.Currency.TRY);
            assertEquals(100000,drawer.opening.minorUnits());
            assertEquals(0,drawer.inflow.minorUnits());
            assertEquals(100000,drawer.outflow.minorUnits());
            assertEquals(0,drawer.closing.minorUnits());
            VaultPeriodReport.Totals sham=report.forPeriod(
                InvoiceSettlement.Channel.SHAM_CASH,2000,now+60000)
                .get(Money.Currency.USD);
            assertEquals(0,sham.opening.minorUnits());
            assertEquals(2500,sham.inflow.minorUnits());
            assertEquals(0,sham.outflow.minorUnits());
            assertEquals(2500,sham.closing.minorUnits());
            assertFalse(report.forPeriod(InvoiceSettlement.Channel.SHAM_CASH,2000,now+60000)
                .containsKey(Money.Currency.TRY));
        } finally {helper.close();}
    }
}
