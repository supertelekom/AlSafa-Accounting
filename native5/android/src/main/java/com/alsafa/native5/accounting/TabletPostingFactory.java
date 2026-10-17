package com.alsafa.native5.accounting;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

/** Factory used by simple and advanced tablet screens for all journaled writes. */
public final class TabletPostingFactory {
    private TabletPostingFactory() { }
    public static PostingGateway create(SQLiteDatabase db,Context context,String storeId) {
        if(db==null || context==null) throw new IllegalArgumentException("Missing database/context");
        if(storeId==null || !storeId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid store ID");
        PostingGateway local=new AtomicPostingService(new AndroidSqlitePostingPort(db));
        return new SyncAfterCommitGateway(local,()->TabletSyncScheduler.schedule(context,storeId));
    }
}
