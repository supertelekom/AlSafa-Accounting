package com.alsafa.native5.accounting;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.util.Map;
import java.util.Objects;

/** Tablet closing entry point: local commit first, then best-effort upload scheduling. */
public final class TabletVaultClosingGateway {
    private final VaultClosingPostingService service;
    private final Context context;
    private final String storeId;
    public TabletVaultClosingGateway(SQLiteDatabase db,Context context,String storeId) {
        service=new VaultClosingPostingService(db);
        this.context=Objects.requireNonNull(context,"context").getApplicationContext();
        if(storeId==null || !storeId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid store ID");
        this.storeId=storeId;
    }
    public void close(String closingId,InvoiceSettlement.Channel vault,
            Map<Money.Currency,Money> counted) {
        service.close(closingId,vault,counted);
        try {TabletSyncScheduler.schedule(context,storeId);}
        catch(RuntimeException unavailable) { /* durable outbox will retry */ }
    }
}
