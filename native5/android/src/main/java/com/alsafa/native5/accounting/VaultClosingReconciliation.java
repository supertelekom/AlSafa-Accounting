package com.alsafa.native5.accounting;

import android.database.sqlite.SQLiteDatabase;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Compare physical closing count with posted ledger balances per vault/currency.
 * A discrepancy is informational; never auto-post an adjustment or erase entries.
 */
public final class VaultClosingReconciliation {
    private final VaultBalanceReader reader;

    public VaultClosingReconciliation(SQLiteDatabase db) {
        this.reader=new VaultBalanceReader(Objects.requireNonNull(db,"db"));
    }

    public static final class Difference {
        public final Money expected;
        public final Money counted;
        public final Money variance;
        Difference(Money expected,Money counted) {
            this.expected=expected;
            this.counted=counted;
            this.variance=new Money(expected.currency(),
                Math.subtractExact(counted.minorUnits(),expected.minorUnits()));
        }
        public boolean matches() {return variance.minorUnits()==0;}
    }

    public EnumMap<Money.Currency,Difference> reconcile(
            InvoiceSettlement.Channel vault,
            Map<Money.Currency,Money> counted) {
        Objects.requireNonNull(vault,"vault");
        Objects.requireNonNull(counted,"counted");
        EnumMap<Money.Currency,Money> expected=reader.balances(vault);
        if(counted.size()!=expected.size() || !counted.keySet().equals(expected.keySet()))
            throw new IllegalArgumentException("Provide a count for every allowed vault currency");
        EnumMap<Money.Currency,Difference> result=new EnumMap<>(Money.Currency.class);
        for(Map.Entry<Money.Currency,Money> entry:expected.entrySet()) {
            Money.Currency currency=entry.getKey();
            Money physical=Objects.requireNonNull(counted.get(currency),"Missing physical count");
            if(physical.currency()!=currency || physical.minorUnits()<0)
                throw new IllegalArgumentException("Invalid counted currency or negative count");
            result.put(currency,new Difference(entry.getValue(),physical));
        }
        return result;
    }
}
