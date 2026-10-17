package com.alsafa.native5.accounting;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.util.Objects;

/**
 * Tablet entry point for an actual vault currency exchange.
 * SQLite commits first; background Firestore upload is then requested.
 * A scheduler failure cannot undo or lose the durable outbox event.
 */
public final class TabletVaultExchangeGateway {
    private final VaultExchangePostingService service;
    private final Context context;
    private final String storeId;

    public TabletVaultExchangeGateway(SQLiteDatabase db,Context context,String storeId) {
        service=new VaultExchangePostingService(db);
        this.context=Objects.requireNonNull(context,"context").getApplicationContext();
        if(storeId==null || !storeId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid store ID");
        this.storeId=storeId;
    }

    public PostingBatch exchange(String eventId,Money given,
            InvoiceSettlement.Channel source,Money received,
            InvoiceSettlement.Channel destination) {
        PostingBatch posted=service.exchange(eventId,given,source,received,destination);
        try {TabletSyncScheduler.schedule(context,storeId);}
        catch(RuntimeException unavailable) { /* outbox remains pending */ }
        return posted;
    }
}
