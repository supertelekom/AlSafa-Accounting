package com.alsafa.native7

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductBatchValidationTest {
    private fun product(id:String,group:String,color:String,barcode:String):JSONObject =
        JSONObject().apply {
            put("id",id);put("name","ساعة اختبار")
            put("variantGroupId",group);put("variantColor",color)
            put("color",color);put("barcode",barcode)
            put("stock",0);put("salePrice",10);put("cost",4)
        }

    @Test fun duplicateBarcodeRollsBackWholeBatch() {
        val db=SQLiteDatabase.create(null)
        try {
            LegacyDomainTables.create(db)
            NativeProductChanges.create(db)
            try {
                NativeProductChanges.createProducts(db,listOf(
                    product("test-a","g","أسود","123456"),
                    product("test-b","g","فضي","123456")))
                fail("Duplicate barcode accepted")
            } catch(_:IllegalArgumentException) {}
            db.rawQuery("SELECT COUNT(*) FROM native_new_products",null).use {
                assertTrue(it.moveToFirst())
                assertEquals(0,it.getInt(0))
            }
        } finally { db.close() }
    }

    @Test fun duplicateColourRollsBackWholeBatch() {
        val db=SQLiteDatabase.create(null)
        try {
            LegacyDomainTables.create(db)
            NativeProductChanges.create(db)
            try {
                NativeProductChanges.createProducts(db,listOf(
                    product("test-c","g","أسود","100"),
                    product("test-d","g","أسود","101")))
                fail("Duplicate colour accepted")
            } catch(_:IllegalArgumentException) {}
            db.rawQuery("SELECT COUNT(*) FROM native_new_products",null).use {
                assertTrue(it.moveToFirst())
                assertEquals(0,it.getInt(0))
            }
        } finally { db.close() }
    }
}
