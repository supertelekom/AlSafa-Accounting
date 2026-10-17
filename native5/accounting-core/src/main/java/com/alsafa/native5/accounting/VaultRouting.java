package com.alsafa.native5.accounting;

import java.util.Objects;

/**
 * Central payment-vault rules shared by sales, repairs, suppliers, collections
 * and cash movements. Currency is part of the ledger balance identity.
 */
public final class VaultRouting {
    private VaultRouting() {}

    public static void validate(InvoiceSettlement.Channel channel,Money.Currency currency) {
        Objects.requireNonNull(channel,"channel");
        Objects.requireNonNull(currency,"currency");
        if(channel==InvoiceSettlement.Channel.SHAM_CASH && currency==Money.Currency.TRY)
            throw new IllegalArgumentException("Sham Cash supports only USD and SYP");
    }

    public static String account(InvoiceSettlement.Channel channel,Money.Currency currency) {
        validate(channel,currency);
        return "asset:payment:"+channel.name();
    }

    public static void validateReceipt(InvoiceSettlement.Receipt receipt) {
        Objects.requireNonNull(receipt,"receipt");
        validate(receipt.channel,receipt.received.currency());
    }
}
