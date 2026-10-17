package com.alsafa.native5.accounting;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class InvoiceSettlementTest {
    private static Money usd(long cents){return new Money(Money.Currency.USD,cents);}
    @Test public void onlineCashOnDeliveryStartsUnpaid(){
        InvoiceSettlement invoice=new InvoiceSettlement(usd(6000),Collections.emptyList());
        assertEquals(InvoiceSettlement.Status.UNPAID,invoice.status());
        assertEquals(6000,invoice.outstanding().minorUnits());
    }
    @Test public void partialShamCashLeavesCorrectBalance(){
        InvoiceSettlement invoice=new InvoiceSettlement(usd(6000),Collections.singletonList(
            new InvoiceSettlement.Receipt(InvoiceSettlement.Channel.SHAM_CASH,usd(2000),usd(2000))));
        assertEquals(InvoiceSettlement.Status.PARTIAL,invoice.status());
        assertEquals(4000,invoice.outstanding().minorUnits());
    }
    @Test public void mixedCurrencyRequiresExplicitValuation(){
        InvoiceSettlement invoice=new InvoiceSettlement(usd(6000),Arrays.asList(
            new InvoiceSettlement.Receipt(InvoiceSettlement.Channel.CASH_DRAWER,usd(5500),usd(5500)),
            new InvoiceSettlement.Receipt(InvoiceSettlement.Channel.CASH_DRAWER,
                new Money(Money.Currency.TRY,20000),usd(500))));
        assertEquals(InvoiceSettlement.Status.PAID,invoice.status());
        assertEquals(0,invoice.outstanding().minorUnits());
    }
    @Test public void cannotOverpayOrSilentlyRevalueSameCurrency(){
        try {
            new InvoiceSettlement(usd(6000),Collections.singletonList(
                new InvoiceSettlement.Receipt(InvoiceSettlement.Channel.BANK,usd(7000),usd(7000))));
            fail("Overpayment accepted");
        } catch(IllegalArgumentException expected){}
        try {
            new InvoiceSettlement.Receipt(InvoiceSettlement.Channel.CASH_DRAWER,usd(100),usd(200));
            fail("Inconsistent valuation accepted");
        } catch(IllegalArgumentException expected){}
    }
}
