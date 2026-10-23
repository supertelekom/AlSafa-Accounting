package com.alsafa.native6

import android.app.Activity
import org.junit.Assert.assertThrows
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CustomerShareTest {
    @Test fun missingFileIsRejected() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val missing=File(activity.cacheDir,"shared_invoices/missing.pdf")
        assertThrows(IllegalArgumentException::class.java) {
            CustomerShare.send(activity,missing,"application/pdf","")
        }
    }

    @Test fun unsupportedMimeIsRejected() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val file=File(activity.filesDir,"images/test.jpg")
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1,2,3))
        try {
            assertThrows(IllegalArgumentException::class.java) {
                CustomerShare.send(activity,file,"text/plain","")
            }
        } finally { file.delete() }
    }

    @Test fun invoiceCannotBeSharedAsImage() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val file=File(activity.cacheDir,"shared_invoices/test.pdf")
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1,2,3))
        try {
            assertThrows(IllegalArgumentException::class.java) {
                CustomerShare.send(activity,file,"image/jpeg","")
            }
        } finally { file.delete() }
    }
}
