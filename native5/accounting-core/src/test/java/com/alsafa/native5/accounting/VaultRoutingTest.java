package com.alsafa.native5.accounting;

import org.junit.Test;
import static org.junit.Assert.*;

public class VaultRoutingTest {
    private static Money money(Money.Currency currency) {return new Money(currency,100);}
    @Test public void cashDrawerSupportsAllThreeCurrencies() {
        for(Money.Currency currency:Money.Currency.values())
            VaultRouting.validate(InvoiceSettlement.Channel.CASH_DRAWER,currency);
    }
    @Test public void shamCashSupportsUsdAndSypOnly() {
        VaultRouting.validate(InvoiceSettlement.Channel.SHAM_CASH,Money.Currency.USD);
        VaultRouting.validate(InvoiceSettlement.Channel.SHAM_CASH,Money.Currency.SYP);
        try {
            VaultRouting.validate(InvoiceSettlement.Channel.SHAM_CASH,Money.Currency.TRY);
            fail("TRY accepted in Sham Cash");
        } catch(IllegalArgumentException expected) {}
    }
    @Test public void invoiceReceiptsRejectTryToShamCash() {
        try {
            new InvoiceSettlement.Receipt(InvoiceSettlement.Channel.SHAM_CASH,
                money(Money.Currency.TRY),money(Money.Currency.TRY));
            fail("TRY receipt accepted");
        } catch(IllegalArgumentException expected) {}
    }
    @Test public void supplierPaymentRejectsTryToShamCash() {
        try {
            SupplierAccounting.settle("event-1","supplier-1",money(Money.Currency.TRY),
                InvoiceSettlement.Channel.SHAM_CASH);
            fail("TRY supplier payment accepted");
        } catch(IllegalArgumentException expected) {}
    }
    @Test public void onlineCollectionRejectsTryToShamCash() {
        try {
            OnlineOrderAccounting.collect("event-1","invoice-1",money(Money.Currency.TRY),
                money(Money.Currency.TRY),InvoiceSettlement.Channel.SHAM_CASH);
            fail("TRY online collection accepted");
        } catch(IllegalArgumentException expected) {}
    }
}
