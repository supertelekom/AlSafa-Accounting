package com.alsafa.native5.accounting;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

/**
 * Tablet-authoritative cancellation entry point. Schedules upload only after
 * local journal, returned stock, audit and outbox commit successfully.
 */
public final class TabletOnlineCancellationGateway {
    private final UncollectedOnlineCancellationService service;
    private final Context context;
    private final String storeId;

    public TabletOnlineCancellationGateway(SQLiteDatabase db,Context context,String storeId) {
        this.service=new UncollectedOnlineCancellationService(db);
        this.context=context.getApplicationContext();
        if(storeId==null || !storeId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid store ID");
        this.storeId=storeId;
    }

    public void cancelUncollectedReturned(String eventId,String invoiceId,String actorId,
                                           boolean physicalReturnConfirmed) {
        service.cancel(eventId,invoiceId,actorId,physicalReturnConfirmed);
        try { TabletSyncScheduler.schedule(context,storeId); }
        catch(RuntimeException unavailable) { /* Local outbox remains durable. */ }
    }
}
