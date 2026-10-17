package com.alsafa.native5.accounting;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/**
 * Currency exchange between any two allowed vault/currency balances, including
 * two currencies inside the same vault. No sale, income or expense is created.
 *
 * Each currency is balanced independently through its FX clearing account.
 * Reconciliation of clearing balances and exchange gain/loss is a separate
 * explicit accounting step, never inferred from the exchange amounts.
 */
public final class VaultCurrencyExchange {
    private VaultCurrencyExchange() {}

    public static PostingBatch exchange(String eventId,Money given,
            InvoiceSettlement.Channel source,Money received,
            InvoiceSettlement.Channel destination) {
        if(eventId==null || !eventId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid exchange event ID");
        Objects.requireNonNull(given,"given");
        Objects.requireNonNull(received,"received");
        Objects.requireNonNull(source,"source");
        Objects.requireNonNull(destination,"destination");
        if(given.minorUnits()<=0 || received.minorUnits()<=0)
            throw new IllegalArgumentException("Exchange amounts must be positive");
        if(given.currency()==received.currency())
            throw new IllegalArgumentException("Same-currency movement uses vault transfer");
        VaultRouting.validate(source,given.currency());
        VaultRouting.validate(destination,received.currency());
        JournalEntry entry=new JournalEntry(eventId,Arrays.asList(
            new JournalEntry.Line("asset:fx_clearing",given),
            new JournalEntry.Line(VaultRouting.account(source,given.currency()),given.negate()),
            new JournalEntry.Line(VaultRouting.account(destination,received.currency()),received),
            new JournalEntry.Line("asset:fx_clearing",received.negate())
        ));
        String payload="{\"source\":\""+source.name()+"\",\"givenCurrency\":\""+
            given.currency()+"\",\"givenMinor\":"+given.minorUnits()+
            ",\"destination\":\""+destination.name()+"\",\"receivedCurrency\":\""+
            received.currency()+"\",\"receivedMinor\":"+received.minorUnits()+"}";
        return new PostingBatch(entry,"VAULT_CURRENCY_EXCHANGE",payload,Collections.emptyList());
    }
}
