package com.alsafa.native5.accounting;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.firebase.firestore.FirebaseFirestore;

/** Durable background upload: local accounting remains usable while offline. */
public final class TabletSyncWorker extends Worker {
    public TabletSyncWorker(@NonNull Context context,@NonNull WorkerParameters params) {
        super(context,params);
    }
    @NonNull @Override public Result doWork() {
        String storeId=getInputData().getString("storeId");
        if(storeId==null || storeId.isEmpty()) return Result.failure();
        try(Native5DatabaseHelper helper=new Native5DatabaseHelper(getApplicationContext())) {
            SQLiteDatabase db=helper.getWritableDatabase();
            UploadOnlySync sync=new UploadOnlySync(new AndroidOutboxQueue(db),
                new FirestoreOperationWriter(FirebaseFirestore.getInstance(),storeId));
            int processed=0;
            while(processed<25 && !isStopped()) {
                if(!sync.uploadNext(System.currentTimeMillis(),120000L)) break;
                processed++;
            }
            if(isStopped()) return Result.retry();
            // Retry unfinished or failed uploads. Never mark them as uploaded locally.
            try(android.database.Cursor c=db.rawQuery(
                "SELECT COUNT(*) FROM outbox WHERE state IN ('PENDING','RETRY','SENDING')",null)) {
                c.moveToFirst();
                return c.getLong(0)>0 ? Result.retry() : Result.success();
            }
        } catch(Exception error) {
            return Result.retry();
        }
    }
}
