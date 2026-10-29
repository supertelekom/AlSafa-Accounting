package com.alsafa.native7

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductInventoryAuditTest {
    @Test fun detectsFundingMismatchWithoutChangingInventory() {
        val db=SQLiteDatabase.create(null)
        try {
            LegacyDomainTables.create(db)
            NativeProductChanges.create(db)
            val item=JSONObject().apply {
                put("id","audit-product");put("name","ساعة اختبار")
                put("stock",2);put("salePrice",20);put("cost",5)
                put("fundingLots",JSONArray())
            }
            db.execSQL("INSERT INTO native_new_products(product_id,payload,created_at) VALUES (?,?,?)",
                arrayOf<Any>("audit-product",item.toString(),1L))
            val issues=NativeProductChanges.auditInventory(db)
            assertTrue(issues.any { it.productId=="audit-product" &&
                it.description.contains("دفعات التمويل") })
            assertEquals(2,LegacyProductRepository.findById(db,"audit-product")?.stock)
        } finally { db.close() }
    }
}
