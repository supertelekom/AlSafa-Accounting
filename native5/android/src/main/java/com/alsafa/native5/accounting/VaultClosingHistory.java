package com.alsafa.native5.accounting;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.EnumMap;
import java.util.Objects;

/** Read historical immutable closing snapshots, not current mutable balances. */
public final class VaultClosingHistory {
    private final SQLiteDatabase db;
    public VaultClosingHistory(SQLiteDatabase db) {this.db=Objects.requireNonNull(db,"db");}

    public static final class Snapshot {
        public final String closingId;
        public final InvoiceSettlement.Channel vault;
        public final long closedAtMs;
        public final EnumMap<Money.Currency,VaultClosingReconciliation.Difference> counts;
        Snapshot(String closingId,InvoiceSettlement.Channel vault,long closedAtMs,
                EnumMap<Money.Currency,VaultClosingReconciliation.Difference> counts) {
            this.closingId=closingId;
            this.vault=vault;
            this.closedAtMs=closedAtMs;
            this.counts=counts;
        }
    }

    public Snapshot get(String closingId) {
        if(closingId==null || !closingId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid closing ID");
        InvoiceSettlement.Channel vault;
        long timestamp;
        try(Cursor c=db.rawQuery(
                "SELECT vault,closed_at_ms FROM vault_closings WHERE closing_id=?",
                new String[]{closingId})) {
            if(!c.moveToFirst()) return null;
            vault=InvoiceSettlement.Channel.valueOf(c.getString(0));
            timestamp=c.getLong(1);
        }
        EnumMap<Money.Currency,VaultClosingReconciliation.Difference> counts=
            new EnumMap<>(Money.Currency.class);
        try(Cursor c=db.rawQuery(
                "SELECT currency,expected_minor,counted_minor,variance_minor FROM vault_closing_counts WHERE closing_id=?",
                new String[]{closingId})) {
            while(c.moveToNext()) {
                Money.Currency currency=Money.Currency.valueOf(c.getString(0));
                VaultRouting.validate(vault,currency);
                Money expected=new Money(currency,c.getLong(1));
                Money counted=new Money(currency,c.getLong(2));
                VaultClosingReconciliation.Difference difference=
                    new VaultClosingReconciliation.Difference(expected,counted);
                if(difference.variance.minorUnits()!=c.getLong(3))
                    throw new IllegalStateException("Closing variance integrity error");
                counts.put(currency,difference);
            }
        }
        return new Snapshot(closingId,vault,timestamp,counts);
    }
}
