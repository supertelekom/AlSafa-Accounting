package com.alsafa.native5.tablet;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Synthetic data only. Never includes a customer backup. */
@RunWith(AndroidJUnit4.class)
public class LegacyIsolatedStagingTest {
    private static final String SHA="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test public void stagesHistoryAndMetadataWithoutPostingAccounting() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("native5_legacy_staging.db");
        JSONObject data=new JSONObject();
        data.put("settings",new JSONObject().put("shopName","Synthetic Shop")
            .put("sync",new JSONObject().put("token","never-stage"))
            .put("partners",new JSONArray().put(new JSONObject().put("share",40))));
        data.put("rates",new JSONObject().put("TRY",42.5));
        data.put("products",new JSONArray().put(new JSONObject()
            .put("id","product-1").put("name","Synthetic Watch")));
        data.put("sales",new JSONArray().put(new JSONObject()
            .put("id","sale-1").put("amount",45.567)
            .put("deviceId","old-device").put("_syncState","pending")));
        try(LegacyIsolatedStaging staging=new LegacyIsolatedStaging(context)) {
            java.util.Map<String,Integer> staged=staging.stage(SHA,data);
            assertEquals(Integer.valueOf(1),staged.get("products"));
            assertEquals(Integer.valueOf(1),staged.get("sales"));
            assertEquals(2L,staging.verifiedRecordCount(SHA));
            SQLiteDatabase db=staging.getReadableDatabase();
            try(Cursor c=db.rawQuery("SELECT settings_json,rates_json FROM backup_metadata WHERE sha256=?",
                    new String[]{SHA})) {
                assertTrue(c.moveToFirst());
                JSONObject settings=new JSONObject(c.getString(0));
                assertEquals("Synthetic Shop",settings.getString("shopName"));
                assertFalse(settings.has("sync"));
                assertEquals(40,settings.getJSONArray("partners").getJSONObject(0).getInt("share"));
                assertEquals(42.5,new JSONObject(c.getString(1)).getDouble("TRY"),0.001);
            }
            try(Cursor c=db.rawQuery("SELECT payload FROM historical_records WHERE sha256=? AND collection_name='sales'",
                    new String[]{SHA})) {
                assertTrue(c.moveToFirst());
                JSONObject sale=new JSONObject(c.getString(0));
                assertEquals(45.567,sale.getDouble("amount"),0.000001);
                assertFalse(sale.has("deviceId"));
                assertFalse(sale.has("_syncState"));
            }
        }
    }

    @Test public void duplicateIdsRollbackEntireImport() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("native5_legacy_staging.db");
        JSONObject data=new JSONObject().put("settings",new JSONObject())
            .put("products",new JSONArray().put(new JSONObject().put("id","duplicate"))
                .put(new JSONObject().put("id","duplicate")))
            .put("sales",new JSONArray());
        try(LegacyIsolatedStaging staging=new LegacyIsolatedStaging(context)) {
            try {
                staging.stage(SHA,data);
                fail("Expected duplicate ID rejection");
            } catch(org.json.JSONException expected) {
                assertTrue(expected.getMessage().contains("Duplicate"));
            }
            assertEquals(-1L,staging.verifiedRecordCount(SHA));
            assertEquals(0L,android.database.DatabaseUtils.longForQuery(
                staging.getReadableDatabase(),"SELECT COUNT(*) FROM historical_records",null));
        }
    }
}
