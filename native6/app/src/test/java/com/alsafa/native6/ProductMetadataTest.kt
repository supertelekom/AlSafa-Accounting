package com.alsafa.native6

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class ProductMetadataTest {
    private fun fresh()=Store(StoreDb(RuntimeEnvironment.getApplication(),"product-meta-${Store.id()}.db"))

    @Test fun creationAndEditPreserveInventoryAndImages() {
        val store=fresh()
        val details=JSONObject().put("dialColor","أزرق").put("imagePath","/images/keep.jpg").toString()
        val id=store.addProduct("Casio","REF-01",Currency.USD,5500,3000,2,
            details=details,brand="Casio",model="MTP-001")
        assertEquals("Casio",store.rows("SELECT brand FROM products WHERE id=?",id).single()["brand"])
        val before=store.product(id)
        store.editProductFull(id,"Casio Classic","REF-02",6000,"CASIO","MTP-002",
            "ساعة أصلية","أسود","فضي","جلد")
        val row=store.rows("SELECT brand,model,details FROM products WHERE id=?",id).single()
        val updated=JSONObject(row.getValue("details"))
        assertEquals("CASIO",row["brand"])
        assertEquals("MTP-002",row["model"])
        assertEquals("أسود",updated.getString("dialColor"))
        assertEquals("جلد",updated.getString("strapColor"))
        assertEquals("/images/keep.jpg",updated.getString("imagePath"))
        assertEquals(before.stock,store.product(id).stock)
        assertEquals(before.value,store.product(id).value)
    }

    @Test fun invalidDuplicateBarcodeDoesNotChangeMetadata() {
        val store=fresh()
        store.addProduct("Other","TAKEN",Currency.USD,100,50,0)
        val id=store.addProduct("Watch","FREE",Currency.USD,100,50,0,brand="Before")
        try {
            store.editProductFull(id,"Changed","TAKEN",200,"After","REF","Details","","","")
            fail("Duplicate barcode should be rejected")
        } catch (_:IllegalArgumentException) { }
        val row=store.rows("SELECT name,brand,barcode FROM products WHERE id=?",id).single()
        assertEquals("Watch",row["name"])
        assertEquals("Before",row["brand"])
        assertEquals("FREE",row["barcode"])
    }
}
