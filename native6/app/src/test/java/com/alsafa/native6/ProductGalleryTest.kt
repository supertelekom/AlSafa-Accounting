package com.alsafa.native6

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class ProductGalleryTest {
    @Test fun galleryIsIndependentAndPreservesFirstPhoto() {
        val s=Store(StoreDb(RuntimeEnvironment.getApplication(),"gallery-${Store.id()}.db"))
        val base=s.addProduct("Casio","BASE",Currency.USD,5000,2000,2)
        val blue=s.addColorVariant(base,"BLUE",5000,2000,1,"أزرق","","")
        s.setProductImage(base,"/images/base.jpg")
        s.appendProductImage(base,"/images/second.jpg")
        s.appendProductImage(base,"/images/second.jpg")
        s.appendProductImage(blue,"/images/blue.jpg")
        assertEquals(listOf("/images/base.jpg","/images/second.jpg"),s.productImages(base))
        assertEquals(listOf("/images/blue.jpg"),s.productImages(blue))
        assertEquals("/images/base.jpg",s.productImage(base))
        s.unlinkProductImage(base,"/images/base.jpg")
        assertEquals("/images/second.jpg",s.productImage(base))
        assertEquals(listOf("/images/blue.jpg"),s.productImages(blue))
    }

    @Test fun galleryLimitRollsBackWithoutLosingPhotos() {
        val s=Store(StoreDb(RuntimeEnvironment.getApplication(),"gallery-${Store.id()}.db"))
        val id=s.addProduct("Watch","B",Currency.USD,1000,500,1)
        repeat(8) { s.appendProductImage(id,"/images/$it.jpg") }
        try {
            s.appendProductImage(id,"/images/overflow.jpg")
            fail("Gallery must reject ninth image")
        } catch (_:IllegalArgumentException) { }
        assertEquals(8,s.productImages(id).size)
        assertEquals("/images/0.jpg",s.productImage(id))
    }
}
