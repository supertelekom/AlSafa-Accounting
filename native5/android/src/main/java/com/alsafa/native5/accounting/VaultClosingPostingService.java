package com.alsafa.native5.accounting;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Saves a complete, immutable vault closing snapshot and outbox atomically. */
public final class VaultClosingPostingService {
    private final SQLiteDatabase db;
    public VaultClosingPostingService(SQLiteDatabase db) {this.db=Objects.requireNonNull(db,"db");}

    public void close(String closingId,InvoiceSettlement.Channel vault,
            Map<Money.Currency,Money> counted) {
        if(closingId==null || !closingId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid closing ID");
        Objects.requireNonNull(vault,"vault");
        Objects.requireNonNull(counted,"counted");
        if(db.inTransaction()) throw new IllegalStateException("Nested closing transaction");
        db.beginTransactionNonExclusive();
        try {
            EnumMap<Money.Currency,VaultClosingReconciliation.Difference> differences=
                new VaultClosingReconciliation(db).reconcile(vault,counted);
            long now=System.currentTimeMillis();
            ContentValues event=new ContentValues();
            event.put("event_id",closingId);
            event.put("operation_kind","VAULT_CLOSING");
            event.put("created_at_ms",now);
            db.insertOrThrow("accounting_events",null,event);
            ContentValues closing=new ContentValues();
            closing.put("closing_id",closingId);
            closing.put("vault",vault.name());
            closing.put("closed_at_ms",now);
            db.insertOrThrow("vault_closings",null,closing);
            StringBuilder payload=new StringBuilder("{\"closingId\":\"").append(closingId)
                .append("\",\"vault\":\"").append(vault.name())
                .append("\",\"closedAtMs\":").append(now).append(",\"counts\":[");
            boolean first=true;
            for(Map.Entry<Money.Currency,VaultClosingReconciliation.Difference> row:differences.entrySet()) {
                Money.Currency currency=row.getKey();
                VaultClosingReconciliation.Difference d=row.getValue();
                ContentValues v=new ContentValues();
                v.put("closing_id",closingId);
                v.put("currency",currency.name());
                v.put("expected_minor",d.expected.minorUnits());
                v.put("counted_minor",d.counted.minorUnits());
                v.put("variance_minor",d.variance.minorUnits());
                db.insertOrThrow("vault_closing_counts",null,v);
                if(!first) payload.append(',');
                first=false;
                payload.append("{\"currency\":\"").append(currency.name())
                    .append("\",\"expectedMinor\":").append(d.expected.minorUnits())
                    .append(",\"countedMinor\":").append(d.counted.minorUnits())
                    .append(",\"varianceMinor\":").append(d.variance.minorUnits()).append('}');
            }
            payload.append("]}");
            ContentValues upload=new ContentValues();
            upload.put("operation_id",closingId);
            upload.put("operation_kind","VAULT_CLOSING");
            upload.put("payload",payload.toString());
            upload.put("created_at_ms",now);
            db.insertOrThrow("outbox",null,upload);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
