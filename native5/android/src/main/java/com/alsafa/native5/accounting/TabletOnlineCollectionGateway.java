package com.alsafa.native5.accounting;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.util.Objects;

/** Entry point for online collections from either tablet UI mode. */
public final class TabletOnlineCollectionGateway {
    private final OnlineReceiptPostingService local;
    private final Context context;
    private final String storeId;
    public TabletOnlineCollectionGateway(SQLiteDatabase db,Context context,String storeId) {
        this.local=new OnlineReceiptPostingService(Objects.requireNonNull(db,"db"));
        this.context=Objects.requireNonNull(context,"context").getApplicationContext();
        if(storeId==null || !storeId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid store ID");
        this.storeId=storeId;
    }
    public OnlineCollectionPlan collect(String operationId,String invoiceId,Money receipt,
            InvoiceSettlement.Channel destination) {
        OnlineCollectionPlan result=local.collect(operationId,invoiceId,receipt,destination);
        try { TabletSyncScheduler.schedule(context,storeId); }
        catch(RuntimeException schedulingFailure) {
            // The committed outbox survives and periodic recovery can retry.
        }
        return result;
    }
}
