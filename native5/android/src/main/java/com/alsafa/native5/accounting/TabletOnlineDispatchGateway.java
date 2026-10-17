package com.alsafa.native5.accounting;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.util.List;

public final class TabletOnlineDispatchGateway {
    private final OnlineDispatchPostingService service;
    private final Context context;
    private final String storeId;
    public TabletOnlineDispatchGateway(SQLiteDatabase db,Context context,String storeId) {
        service=new OnlineDispatchPostingService(db);
        this.context=context.getApplicationContext();
        if(storeId==null || !storeId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid store ID");
        this.storeId=storeId;
    }
    public void dispatch(String eventId,String invoiceId,Money total,Money cost,
                         List<InventoryDelta> stock) {
        service.dispatch(eventId,invoiceId,total,cost,stock);
        try { TabletSyncScheduler.schedule(context,storeId); }
        catch (RuntimeException unavailable) { /* outbox is durable */ }
    }
}
