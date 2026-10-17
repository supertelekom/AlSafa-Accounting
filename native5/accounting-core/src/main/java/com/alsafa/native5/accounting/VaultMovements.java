package com.alsafa.native5.accounting;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/**
 * Vault operations for all tablet workflows. Balances are kept separately by
 * journal currency; transfers cannot silently exchange USD, SYP or TRY.
 */
public final class VaultMovements {
    private VaultMovements() {}

    public static PostingBatch transfer(String eventId,Money amount,
            InvoiceSettlement.Channel source,InvoiceSettlement.Channel destination) {
        requirePositive(amount);
        VaultRouting.validate(source,amount.currency());
        VaultRouting.validate(destination,amount.currency());
        if(source==destination) throw new IllegalArgumentException("Same vault transfer");
        return batch(eventId,"VAULT_TRANSFER",
            new JournalEntry.Line(VaultRouting.account(destination,amount.currency()),amount),
            new JournalEntry.Line(VaultRouting.account(source,amount.currency()),amount.negate()),
            source.name(),destination.name(),amount);
    }

    public static PostingBatch expense(String eventId,Money amount,
            InvoiceSettlement.Channel source) {
        requirePositive(amount);
        VaultRouting.validate(source,amount.currency());
        return batch(eventId,"CASH_EXPENSE",
            new JournalEntry.Line("expense:operating",amount),
            new JournalEntry.Line(VaultRouting.account(source,amount.currency()),amount.negate()),
            source.name(),"OPERATING_EXPENSE",amount);
    }

    public static PostingBatch ownerContribution(String eventId,Money amount,
            InvoiceSettlement.Channel destination) {
        requirePositive(amount);
        VaultRouting.validate(destination,amount.currency());
        return batch(eventId,"OWNER_CONTRIBUTION",
            new JournalEntry.Line(VaultRouting.account(destination,amount.currency()),amount),
            new JournalEntry.Line("equity:owner_contribution",amount.negate()),
            "OWNER_CAPITAL",destination.name(),amount);
    }

    public static PostingBatch ownerWithdrawal(String eventId,Money amount,
            InvoiceSettlement.Channel source) {
        requirePositive(amount);
        VaultRouting.validate(source,amount.currency());
        return batch(eventId,"OWNER_WITHDRAWAL",
            new JournalEntry.Line("equity:owner_drawings",amount),
            new JournalEntry.Line(VaultRouting.account(source,amount.currency()),amount.negate()),
            source.name(),"OWNER_DRAWINGS",amount);
    }

    private static PostingBatch batch(String id,String kind,JournalEntry.Line debit,
            JournalEntry.Line credit,String from,String to,Money amount) {
        if(id==null || !id.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid event ID");
        JournalEntry entry=new JournalEntry(id,Arrays.asList(debit,credit));
        String payload="{\"from\":\""+from+"\",\"to\":\""+to+
            "\",\"currency\":\""+amount.currency()+"\",\"minor\":"+amount.minorUnits()+"}";
        return new PostingBatch(entry,kind,payload,Collections.emptyList());
    }

    private static void requirePositive(Money amount) {
        Objects.requireNonNull(amount,"amount");
        if(amount.minorUnits()<=0) throw new IllegalArgumentException("Amount must be positive");
    }
}
