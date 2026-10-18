package com.alsafa.native5.tablet;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Historical backup staging only. Separate database; never posts journals,
 * updates live inventory, or writes an outbox entry.
 */
public final class LegacyIsolatedStaging extends SQLiteOpenHelper {
    private static final String DB_NAME="native5_legacy_staging.db";
    private static final String[] COLLECTIONS={
        "products","maintenanceParts","sales","repairs","expenses","payments",
        "partnerWithdrawals","cashTransactions","dailyClosings","purchases",
        "suppliers","supplierLedger","returns","usedWatchTransactions","customerOrders",
        "auditLog","discountRules","loyaltyLedger","productCostHistory",
        "smartReceipts","productDetailOptions","inventoryMovements",
        "inventorySessions","customerSessions","shiftHandovers"
    };

    public LegacyIsolatedStaging(Context context) {
        super(context,DB_NAME,null,1);
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE imports (sha256 TEXT PRIMARY KEY, created_at INTEGER NOT NULL, record_count INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE historical_records (sha256 TEXT NOT NULL, collection_name TEXT NOT NULL, legacy_id TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY (sha256,collection_name,legacy_id))");
        db.execSQL("CREATE INDEX idx_historical_collection ON historical_records (sha256,collection_name)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        throw new IllegalStateException("Staging database migration requires explicit implementation");
    }

    /**
     * Called only after preflight. Atomic and idempotent for the same SHA.
     * Do not call from UI thread. No promotion to live accounting is possible here.
     */
    public Map<String,Integer> stage(String sha256,JSONObject data) throws JSONException {
        if(sha256==null || !sha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid backup SHA-256");
        if(data==null || data.optJSONObject("settings")==null ||
                data.optJSONArray("products")==null || data.optJSONArray("sales")==null)
            throw new JSONException("Invalid backup structure");
        SQLiteDatabase db=getWritableDatabase();
        Map<String,Integer> counts=new LinkedHashMap<>();
        try(Cursor existing=db.rawQuery("SELECT record_count FROM imports WHERE sha256=?",new String[]{sha256})) {
            if(existing.moveToFirst())
                throw new IllegalStateException("Backup already staged; existing records preserved");
        }
        db.beginTransaction();
        try {
            int total=0;
            for(String name:COLLECTIONS) {
                JSONArray rows=data.optJSONArray(name);
                if(rows==null) {
                    if(data.has(name) && !data.isNull(name))
                        throw new JSONException("Invalid collection "+name);
                    counts.put(name,0);
                    continue;
                }
                for(int i=0;i<rows.length();i++) {
                    JSONObject row=rows.optJSONObject(i);
                    if(row==null) throw new JSONException("Invalid record "+name+" at "+i);
                    String id=row.optString("id","").trim();
                    if(id.isEmpty()) throw new JSONException("Missing ID in "+name+" at "+i);
                    ContentValues values=new ContentValues();
                    values.put("sha256",sha256);
                    values.put("collection_name",name);
                    values.put("legacy_id",id);
                    JSONObject preserved=new JSONObject(row.toString());
                    java.util.Iterator<String> keys=preserved.keys();
                    java.util.ArrayList<String> excluded=new java.util.ArrayList<>();
                    while(keys.hasNext()) {
                        String key=keys.next();
                        if(key.startsWith("_sync") || key.equals("deviceId") ||
                                key.equals("deviceName")) excluded.add(key);
                    }
                    for(String key:excluded) preserved.remove(key);
                    values.put("payload",preserved.toString());
                    long inserted=db.insertWithOnConflict("historical_records",null,values,SQLiteDatabase.CONFLICT_IGNORE);
                    if(inserted==-1) {
                        // A duplicate within this file must not silently overwrite history.
                        // Already-staged imports are returned before entering this transaction.
                        throw new JSONException("Duplicate staged record in "+name+" at "+i);
                    }
                }
                counts.put(name,rows.length());
                total+=rows.length();
            }
            ContentValues manifest=new ContentValues();
            manifest.put("sha256",sha256);
            manifest.put("created_at",System.currentTimeMillis());
            manifest.put("record_count",total);
            if(db.insertWithOnConflict("imports",null,manifest,SQLiteDatabase.CONFLICT_ABORT)==-1)
                throw new IllegalStateException("Failed to create import manifest");
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
        return counts;
    }
}
