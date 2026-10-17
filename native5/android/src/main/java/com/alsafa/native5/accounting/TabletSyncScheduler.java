package com.alsafa.native5.accounting;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/**
 * Schedule after every successful local posting and at tablet startup.
 * Android may defer background jobs; WorkManager persists jobs across process death.
 */
public final class TabletSyncScheduler {
    private TabletSyncScheduler() { }
    public static void schedule(Context context,String storeId) {
        if(storeId==null || !storeId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid store ID");
        Constraints online=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        Data data=new Data.Builder().putString("storeId",storeId).build();
        OneTimeWorkRequest immediate=new OneTimeWorkRequest.Builder(TabletSyncWorker.class)
            .setConstraints(online).setInputData(data)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS)
            .build();
        WorkManager manager=WorkManager.getInstance(context.getApplicationContext());
        manager.enqueueUniqueWork("native5-tablet-upload",ExistingWorkPolicy.KEEP,immediate);
        PeriodicWorkRequest fallback=new PeriodicWorkRequest.Builder(TabletSyncWorker.class,15,TimeUnit.MINUTES)
            .setConstraints(online).setInputData(data).build();
        manager.enqueueUniquePeriodicWork("native5-tablet-upload-periodic",
            ExistingPeriodicWorkPolicy.KEEP,fallback);
    }
}
