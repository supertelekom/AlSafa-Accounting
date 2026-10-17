package com.alsafa.native5.accounting;

import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import java.util.Objects;

/**
 * Atomic vault FX exchange: check source currency balance, post four journal
 * lines and durable outbox in one SQLite transaction. No partial exchange.
 */
public final class VaultExchangePostingService {
    private final SQLiteDatabase db;
    public VaultExchangePostingService(SQLiteDatabase db) {
        this.db=Objects.requireNonNull(db,"db");
    }

    public PostingBatch exchange(String eventId,Money given,
            InvoiceSettlement.Channel source,Money received,
            InvoiceSettlement.Channel destination) {
        PostingBatch batch=VaultCurrencyExchange.exchange(eventId,given,source,received,destination);
        if(db.inTransaction()) throw new IllegalStateException("Nested posting transaction");
        AndroidSqlitePostingPort port=new AndroidSqlitePostingPort(db);
        port.begin();
        try {
            String account=VaultRouting.account(source,given.currency());
            long balance=DatabaseUtils.longForQuery(db,
                "SELECT COALESCE(SUM(signed_minor_units),0) FROM journal_lines WHERE account_id=? AND currency=?",
                new String[]{account,given.currency().name()});
            if(balance<given.minorUnits())
                throw new IllegalStateException("Insufficient vault currency balance");
            if(port.eventExists(eventId)) throw new IllegalStateException("Duplicate exchange event");
            port.insertEvent(eventId,batch.operationKind());
            int i=0;
            for(JournalEntry.Line line:batch.journal().lines())
                port.insertJournalLine(eventId,i++,line);
            port.insertOutbox(eventId,batch.operationKind(),batch.payload());
            port.commit();
            return batch;
        } catch(RuntimeException | Error failure) {
            port.rollback();
            throw failure;
        }
    }
}
