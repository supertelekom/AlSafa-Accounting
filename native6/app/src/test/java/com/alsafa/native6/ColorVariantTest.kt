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
class ColorVariantTest {
    @Test fun variantsHaveIndependentInventoryAndAccounting() {
        val s=Store(StoreDb(RuntimeEnvironment.getApplication(),"variants-${Store.id()}.db"))
        val parent=s.addProduct("Casio","BASE",Currency.USD,5000,2000,3,brand="Casio",model="REF")
        val child=s.addColorVariant(parent,"BLUE",5500,2500,2,"أزرق","فضي","جلد")
        assertEquals(3,s.product(parent).stock)
        assertEquals(2,s.product(child).stock)
        assertEquals(1,s.colorVariants(parent).size)
        assertEquals(parent,JSONObject(s.rows("SELECT details FROM products WHERE id=?",child).single().getValue("details")).getString("variantParentId"))
        s.sale(listOf(SaleItem(child,1,5500)),"عميل","",Currency.USD,5500,"drawer")
        assertEquals(3,s.product(parent).stock)
        assertEquals(1,s.product(child).stock)
    }

    @Test fun catalogSearchFindsColourAndKeepsSeparateVariants() {
        val s=Store(StoreDb(RuntimeEnvironment.getApplication(),"variants-${Store.id()}.db"))
        val parent=s.addProduct("Casio","BASE",Currency.USD,5000,2000,4)
        val blue=s.addColorVariant(parent,"BLUE",5500,2500,2,"أزرق","فضي","جلد")
        val black=s.addColorVariant(parent,"BLACK",5700,2600,1,"أسود","أسود","معدن")
        assertEquals(listOf(blue),s.searchCatalog("أزرق").map { it.id })
        assertEquals(listOf(black),s.searchCatalog("معدن").map { it.id })
        assertEquals(2,s.colorVariants(parent).size)
        assertTrue(s.colorVariants(blue).isEmpty())
        assertEquals(4,s.product(parent).stock)
        assertEquals(2,s.product(blue).stock)
        assertEquals(1,s.product(black).stock)
    }

    @Test fun duplicateVariantBarcodeRollsBack() {
        val s=Store(StoreDb(RuntimeEnvironment.getApplication(),"variants-${Store.id()}.db"))
        val parent=s.addProduct("Casio","BASE",Currency.USD,5000,2000,3)
        s.addColorVariant(parent,"BLUE",5500,2500,2,"أزرق","","")
        try {
            s.addColorVariant(parent,"BLUE",5500,2500,2,"أسود","","")
            fail("Duplicate barcode must be rejected")
        } catch (_:IllegalArgumentException) { }
        assertEquals(1,s.colorVariants(parent).size)
        assertEquals(3,s.product(parent).stock)
    }
}
