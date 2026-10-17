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
        this.storeId=storeId;
    }
    public void dispatch(String eventId,String invoiceId,Money total,Money cost,
                         List<InventoryDelta> stock) {
        service.dispatch(eventId,invoiceId,total,cost,stock);
        TabletSyncScheduler.schedule(context,storeId);
    }
}
