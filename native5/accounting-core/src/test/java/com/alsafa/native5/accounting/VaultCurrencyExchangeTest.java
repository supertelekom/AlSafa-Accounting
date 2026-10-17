package com.alsafa.native5.accounting;

import org.junit.Test;
import static org.junit.Assert.*;

public class VaultCurrencyExchangeTest {
    private static Money m(Money.Currency currency,long amount) {
        return new Money(currency,amount);
    }
    @Test public void convertsTryToUsdInsideCashDrawerWithoutIncome() {
        PostingBatch batch=VaultCurrencyExchange.exchange("exchange-1",m(Money.Currency.TRY,100000),
            InvoiceSettlement.Channel.CASH_DRAWER,m(Money.Currency.USD,2500),
            InvoiceSettlement.Channel.CASH_DRAWER);
        assertEquals("VAULT_CURRENCY_EXCHANGE",batch.operationKind());
        assertEquals(4,batch.journal().lines().size());
        assertEquals(-100000,batch.journal().lines().get(1).amount.minorUnits());
        assertEquals(2500,batch.journal().lines().get(2).amount.minorUnits());
        for(JournalEntry.Line line:batch.journal().lines())
            assertFalse(line.accountId.startsWith("income:") || line.accountId.startsWith("expense:"));
    }
    @Test public void convertsAcrossCashAndShamCash() {
        PostingBatch batch=VaultCurrencyExchange.exchange("exchange-2",m(Money.Currency.TRY,100000),
            InvoiceSettlement.Channel.CASH_DRAWER,m(Money.Currency.USD,2500),
            InvoiceSettlement.Channel.SHAM_CASH);
        assertEquals("asset:payment:SHAM_CASH",batch.journal().lines().get(2).accountId);
    }
    @Test public void refusesTurkishLiraInsideShamCash() {
        reject(() -> VaultCurrencyExchange.exchange("exchange-3",m(Money.Currency.USD,2500),
            InvoiceSettlement.Channel.CASH_DRAWER,m(Money.Currency.TRY,100000),
            InvoiceSettlement.Channel.SHAM_CASH));
        reject(() -> VaultCurrencyExchange.exchange("exchange-4",m(Money.Currency.TRY,100000),
            InvoiceSettlement.Channel.SHAM_CASH,m(Money.Currency.USD,2500),
            InvoiceSettlement.Channel.CASH_DRAWER));
    }
    @Test public void refusesZeroAndSameCurrency() {
        reject(() -> VaultCurrencyExchange.exchange("exchange-5",m(Money.Currency.USD,0),
            InvoiceSettlement.Channel.CASH_DRAWER,m(Money.Currency.SYP,1000),
            InvoiceSettlement.Channel.CASH_DRAWER));
        reject(() -> VaultCurrencyExchange.exchange("exchange-6",m(Money.Currency.USD,100),
            InvoiceSettlement.Channel.CASH_DRAWER,m(Money.Currency.USD,100),
            InvoiceSettlement.Channel.SHAM_CASH));
    }
    private static void reject(Runnable action) {
        try {action.run();fail("Invalid exchange accepted");}
        catch(IllegalArgumentException expected) {}
    }
}
