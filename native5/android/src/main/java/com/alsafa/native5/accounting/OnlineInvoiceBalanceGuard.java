package com.alsafa.native5.accounting;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Objects;

/** Atomic balance guard for online invoice receipts. */
public final class OnlineInvoiceBalanceGuard {
    private final SQLiteDatabase db;
    public OnlineInvoiceBalanceGuard(SQLiteDatabase db) { this.db=Objects.requireNonNull(db); }

    /** Call only within the same transaction that posts journal, receipt and outbox. */
    public OnlineCollectionPlan apply(String invoiceId,Money receipt) {
        if(!db.inTransaction()) throw new IllegalStateException("Transaction required");
        OnlineCollectionPlan plan;
        try(Cursor c=db.rawQuery("SELECT total_minor,cost_minor,currency,collected_minor,released_cost_minor FROM online_invoices WHERE invoice_id=?",
            new String[]{invoiceId})) {
            if(!c.moveToFirst()) throw new IllegalArgumentException("Unknown invoice");
            Money.Currency currency=Money.Currency.valueOf(c.getString(2));
            plan=OnlineCollectionPlan.calculate(
                new Money(currency,c.getLong(0)),new Money(currency,c.getLong(1)),
                new Money(currency,c.getLong(3)),new Money(currency,c.getLong(4)),receipt);
        }
        db.execSQL("UPDATE online_invoices SET collected_minor=collected_minor+?,released_cost_minor=released_cost_minor+? WHERE invoice_id=? AND collected_minor+?<=total_minor",
            new Object[]{receipt.minorUnits(),plan.costToRelease.minorUnits(),
                invoiceId,receipt.minorUnits()});
        if(android.database.DatabaseUtils.longForQuery(db,"SELECT changes()",null)!=1)
            throw new IllegalStateException("Invoice balance conflict");
        return plan;
    }
}
